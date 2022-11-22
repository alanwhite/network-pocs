package xyz.arwhite.net.proxit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.fusionauth.jwt.domain.JWT;
import xyz.arwhite.net.auth.AuthServer;

public class ProxyConnection implements Runnable {

	private record ProxyRequest(String endpoint, int response, 
			Optional<Map<String,List<String>>> responseHeaders) {}

	private record TargetConnection(Socket targetConn, int response) {}

	private Socket clientConn;
	private ExecutorService ioWorkerpool;
	private Optional<AuthServer> authServer;

	public ProxyConnection(Socket conn, ExecutorService ioWorkerPool, Optional<AuthServer> authServer) {
		clientConn = conn;
		this.ioWorkerpool = ioWorkerPool;
		this.authServer = authServer;
	}

	@Override
	public void run() {

		try {

			// System.out.println("Processing connection ...");
			clientConn.setSoTimeout(60000);

			var proxyRequest = readAndAuthorizeRequest(clientConn);
			if (proxyRequest.response != 200 ) {
				writeErrorResponseAndClose(clientConn,proxyRequest.response,
						proxyRequest.endpoint, proxyRequest.responseHeaders);
				return;
			}

			/*
			 * All validation of the request has been successful so pass the baton 
			 * to the execution side, connecting to the intended target, informing 
			 * the original caller if successful and then relaying all IO until the 
			 * connection is interrupted.
			 */

			var openResult = openTarget(proxyRequest.endpoint);
			if ( openResult.response != 200 ) {
				writeErrorResponseAndClose(clientConn,openResult.response,
						"Connecting Target", Optional.empty());				
				return;
			}

			if ( !writeOKResponse(clientConn, Optional.empty()) ) {
				openResult.targetConn.close();
				return;
			}

			relayIO(clientConn,openResult.targetConn);

		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private ProxyRequest readAndAuthorizeRequest(Socket clientConn) {

		try {
			// System.out.println("Reading request");

			var clientReader = new BufferedReader(
					new InputStreamReader(clientConn.getInputStream(), "US-ASCII"));

			String httpRequest = clientReader.readLine();
			if ( httpRequest == null ) {
				return new ProxyRequest("Unexpected end of stream",500,Optional.empty());
			}

			// System.out.println("Analysing request");

			String[] requestWords = httpRequest.split(" ");
			if ( requestWords.length != 3 ) // HTTP/1.1 400 Too Many Words In Request
				return new ProxyRequest("Too Many Words In Request",400,Optional.empty());

			if ( !requestWords[0].equals("CONNECT") )  // HTTP/1.1 501 Only CONNECT Implemented
				return new ProxyRequest("Only CONNECT Implemented",501,Optional.empty());

			// Grab headers
			var headers = Headers.parse(clientReader.lines()
					.takeWhile(line -> line.length() != 0)
					.collect(Collectors.toList()));

			// Headers.prettyPrint(headers);

			System.out.println("Checking authorization for request");

			// headers should be compared case insensitive
			var authTokens = headers.get("proxy-authorization");
			if ( authTokens == null || authTokens.size() != 1 ) {
				var outHeaders = new HashMap<String,List<String>>();
				outHeaders.put("Proxy-Authenticate", Arrays.asList("Basic", "Bearer"));
				return new ProxyRequest("Proxy Authorization Required",407,Optional.of(outHeaders));
			}

			if ( !authorize(authTokens.get(0)) )
				return new ProxyRequest("Proxy Authorization Failed",403,Optional.empty());

			return new ProxyRequest(requestWords[1],200,Optional.empty());

		} catch (Exception e) {
			e.printStackTrace();
			return new ProxyRequest("",500,Optional.empty());
		}
	}

	/**
	 * If using an Auth Server, validate if the supplied JWT is signed by the trusted Auth Server.
	 * 
	 * If the caller used http basic authentication of the form http://user:password@wherever.com 
	 * then the JWT must be supplied as the password property.
	 * 
	 * The caller can provide the JWT as a bearer token if they can provide the
	 * Proxy-Authorization header directly - many clients don't know how to pass proxy headers.
	 * 
	 * If not using an Auth Server, any old user:pass will do ...!
	 * 
	 * @param string the value provided in the Proxy-Authorization header
	 * @return true if the JWT is valid and issued by our trusted issuer
	 */
	private boolean authorize(String string) {

		try {
			System.out.println("Analysing Proxy-Authorization header");

			String[] words = string.split(" ");
			if ( words == null || words.length != 2 || words[0] == null || words[1] == null )
				return false;

			String basicUser = "";
			String basicPass = "";
			
			if ( "Basic".equals(words[0]) ) {
				String basicToken = new String(Base64.getDecoder().decode(words[1]));

				String[] creds = basicToken.split(":");
				if ( creds == null || creds.length != 2 )
					return false;
			
				basicUser = creds[0];
				basicPass = creds[1];
			}
			
			if ( authServer.isPresent() ) { 
				String token = "";

				if ( "Bearer".equals(words[0]) ) 
					token = words[1];

				else if ( basicPass != "" ) {
					token = basicPass;

				} else // JWT wasn't provided either way
					return false;

				System.out.println("Validating JWT");
				JWT jwt = JWT.getDecoder().decode(token, authServer.get().getSigVerifiers());

				// jwt.getAllClaims().entrySet().forEach(System.out::println);

				return true;
			} else {
				// provide any old user:pass, but you have to provide it
				System.out.println("Authorized user "+basicUser);
				return true;
			}

		} catch(Exception e) {
			e.printStackTrace();
			return false;

		}
	}

	private void writeErrorResponseAndClose(Socket clientConn, int response, 
			String reason, Optional<Map<String,List<String>>> responseHeaders) {

		try {
			var clientWriter = new BufferedWriter(
					new OutputStreamWriter(clientConn.getOutputStream(), "US-ASCII"));
			clientWriter.write("HTTP/1.1 "+response+" "+reason+"\r\n");

			if( responseHeaders.isPresent() )
				writeHeaders(responseHeaders.get(), clientWriter);

			clientWriter.write("\r\n");
			clientWriter.flush();

			clientConn.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean writeOKResponse(Socket clientConn, 
			Optional<Map<String,List<String>>> responseHeaders) {

		try {
			var clientWriter = new BufferedWriter(
					new OutputStreamWriter(clientConn.getOutputStream(), "US-ASCII"));
			clientWriter.write("HTTP/1.1 200 OK\r\n");

			if( responseHeaders.isPresent() )
				writeHeaders(responseHeaders.get(), clientWriter);

			clientWriter.write("\r\n");
			clientWriter.flush();

			return true;

		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	private void writeHeaders(Map<String,List<String>> responseHeaders, 
			BufferedWriter clientWriter) throws IOException {

		final var outHeaders = new ArrayList<String>();
		responseHeaders.entrySet().forEach(
				entry -> entry.getValue().forEach(
						val -> outHeaders.add(entry.getKey()+": " + val)));

		for ( var hdr : outHeaders ) {
			System.out.println(hdr);
			clientWriter.write(hdr+"\r\n");
		}
	}

	private TargetConnection openTarget(String requestTarget) {

		try {
			URI uri = new URI(null,requestTarget,null,null,null);
			InetAddress address = InetAddress.getByName(uri.getHost());

			var targetSocket = new Socket(address, uri.getPort());
			targetSocket.setSoTimeout(60000);

			return new TargetConnection(targetSocket,200);

		} catch (UnknownHostException e) {
			e.printStackTrace();
			return new TargetConnection(null,500);
		} catch (URISyntaxException e) {
			e.printStackTrace();
			return new TargetConnection(null,400);
		} catch (IOException e) {
			e.printStackTrace();
			return new TargetConnection(null,500);
		}

	}

	private void relayIO(Socket clientConn, Socket targetConn) {

		List<Callable<Object>> tasks = new ArrayList<>(2);
		tasks.add(Executors.callable(new UniRelay2(clientConn,targetConn)));
		tasks.add(Executors.callable(new UniRelay2(targetConn,clientConn)));

		try {
			ioWorkerpool.invokeAll(tasks);
		} catch (InterruptedException e1) {
			// e1.printStackTrace();
		}

		// try and close connections, irrespective of what state they're in
		try {
			clientConn.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			targetConn.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * We deliberately do not chain the input and output streams in this
	 * implementation as in future they may be separated by a bridge
	 * 
	 * @author Alan R. White
	 *
	 */
	private class UniRelay2 implements Runnable {

		Socket in, out;

		public UniRelay2(Socket in, Socket out) {
			this.in = in;
			this.out = out;
		}

		@Override
		public void run() {
			try {
				var input = in.getInputStream();
				var output = out.getOutputStream();

				// input.transferTo(output);

				byte[] buffer = new byte[4096];
				int bytesRead = input.read(buffer);

				while ( bytesRead != 0 ) {
					output.write(buffer, 0, bytesRead);

					// if will block, flush out stream
					if ( input.available() < 1 )
						output.flush();

					bytesRead = input.read(buffer);					
				}

			} catch (IOException e) {
				if ( !e.getMessage().equals("Connection reset") ) 
					e.printStackTrace();
			}
		}

	}

}

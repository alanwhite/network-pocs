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
import java.net.http.HttpHeaders;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.fusionauth.jwt.JWTExpiredException;
import io.fusionauth.jwt.JWTUnavailableForProcessingException;
import io.fusionauth.jwt.Verifier;
import io.fusionauth.jwt.domain.JWT;
import io.fusionauth.jwt.rsa.RSAVerifier;
import xyz.arwhite.net.auth.AuthServer;

public class ProxyConnection implements Runnable {

	private record ProxyRequest(String endpoint, int response) {}
	private record TargetConnection(Socket targetConn, int response) {}

	private Socket clientConn;
	private ExecutorService workerPool;
	private AuthServer authServer;

	public ProxyConnection(Socket conn, ExecutorService ioWorkerPool, AuthServer authServer) {
		clientConn = conn;
		workerPool = ioWorkerPool;
		this.authServer = authServer;
	}

	@Override
	public void run() {

		try {

			System.out.println("Processing connection ...");
			clientConn.setSoTimeout(60000);

			var proxyRequest = readRequest(clientConn);
			if (proxyRequest.response != 200 ) {
				writeErrorResponseAndClose(clientConn,proxyRequest.response,proxyRequest.endpoint);
				return;
			}

			/*
			 * All validation of the request has been successful to pass the baton to 
			 * the execution side, connecting to the intended target, informing the original
			 * caller if successful and then relaying all IO until the connection is interrupted.
			 */

			var openResult = openTarget(proxyRequest.endpoint);
			if ( openResult.response != 200 ) {
				writeErrorResponseAndClose(clientConn,openResult.response,"Connecting Target");				
				return;
			}

			if ( !writeOKResponse(clientConn) ) {
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

	private ProxyRequest readRequest(Socket clientConn) {

		try {
			System.out.println("Reading request");
			var clientReader = new BufferedReader(new InputStreamReader(clientConn.getInputStream(), "US-ASCII"));

			String httpRequest = clientReader.readLine();

			System.out.println("Analysing request");
			String[] requestWords = httpRequest.split(" ");
			if ( requestWords.length != 3 ) {
				// HTTP/1.1 400 Too Many Words In Request
				return new ProxyRequest("Too Many Words In Request",400);
			}

			if ( !requestWords[0].equals("CONNECT") ) {
				// HTTP/1.1 501 Only CONNECT Implemented
				return new ProxyRequest("Only CONNECT Implemented",501);
			}

			// Grab headers
			List<String> headerStrings = new ArrayList<>();
			String headerLine = clientReader.readLine();
			while(headerLine.length() != 0 ) {
				headerStrings.add(headerLine);
				headerLine = clientReader.readLine();
			}

			var headers = Headers.parse(headerStrings);
			// Headers.prettyPrint(headers);

			System.out.println("Checking authorization for request");
			if ( !authorize(headers) )
				return new ProxyRequest("Proxy Authorization Failed",403);

			return new ProxyRequest(requestWords[1],200);

		} catch (Exception e) {
			e.printStackTrace();
			return new ProxyRequest("",500);
		}
	}

	/**
	 * When using http basic authentication of the form http://user:pass@wherever.com 
	 * the user:pass string is stripped away, encoded in Base64 and added as 
	 * a header called Proxy-Authorization.
	 * 
	 * In this implementation the user element is not used, and the password is expected
	 * to contain a signed JWT issued by the trusted auth server.
	 * 
	 * @param headers
	 * @return true if the JWT is valid and issued by our trusted issuer
	 */
	private boolean authorize(Map<String,List<String>> headers) {

		try {
			System.out.println("Analysing headers");
			var authTokens = headers.get("Proxy-Authorization");
			if ( authTokens == null )
				return false;

			if ( authTokens.size() != 1 )
				return false;

			String[] words = authTokens.get(0).split(" ");
			if ( words == null || words.length != 2 || words[0] == null )
				return false;

			if ( !"Basic".equals(words[0]) ) 
				return false;

			System.out.println("Analysing basic auth tokens");
			String basicToken = new String(Base64.getDecoder().decode(words[1]));
			System.out.println(basicToken);

			String[] creds = basicToken.split(":");
			if ( creds == null || creds.length != 2 )
				return false;

			System.out.println("Validating JWT");
			JWT jwt = JWT.getDecoder().decode(creds[1], authServer.getSigVerifiers());

			System.out.println(jwt.getObject("resource_access"));
			
			return true;
		}
		catch(Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	private void writeErrorResponseAndClose(Socket clientConn, int response, String reason) {

		try {
			var clientWriter = new BufferedWriter(new OutputStreamWriter(clientConn.getOutputStream(), "US-ASCII"));
			clientWriter.write("HTTP/1.1 "+response+" "+reason);
			clientWriter.write("\r\n");
			clientWriter.flush();

			clientConn.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean writeOKResponse(Socket clientConn) {

		try {
			var clientWriter = new BufferedWriter(new OutputStreamWriter(clientConn.getOutputStream(), "US-ASCII"));
			clientWriter.write("HTTP/1.1 200 OK\r\n");
			clientWriter.write("\r\n");
			clientWriter.flush();

			return true;

		} catch (IOException e) {
			e.printStackTrace();
			return false;
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
			workerPool.invokeAll(tasks);
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

	private class UniRelay implements Runnable {

		Socket in, out;

		public UniRelay(Socket in, Socket out) {
			this.in = in;
			this.out = out;
		}

		@Override
		public void run() {
			try {
				var input = in.getInputStream();
				var output = out.getOutputStream();

				input.transferTo(output);

			} catch (IOException e) {
				if ( !e.getMessage().equals("Connection reset") ) 
					e.printStackTrace();
			}
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

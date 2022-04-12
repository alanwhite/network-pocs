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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyConnection implements Runnable {

	private record ProxyRequest(String endpoint, int response) {}
	private record TargetConnection(Socket targetConn, int response) {}

	private Socket clientConn;
	private ExecutorService workerPool;
	
	public ProxyConnection(Socket conn, ExecutorService ioWorkerPool) {
		clientConn = conn;
		workerPool = ioWorkerPool;
	}

	@Override
	public void run() {

		try {

			clientConn.setSoTimeout(60000);

			var proxyRequest = readRequest(clientConn);
			if (proxyRequest.response != 200 ) {
				writeErrorResponseAndClose(clientConn,proxyRequest.response,proxyRequest.endpoint);
				return;
			}

			// Pass the baton to the execute side

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
			var clientReader = new BufferedReader(new InputStreamReader(clientConn.getInputStream(), "US-ASCII"));

			String httpRequest = clientReader.readLine();

			String[] requestWords = httpRequest.split(" ");
			if ( requestWords.length != 3 ) {
				// HTTP/1.1 400 Too Many Words In Request
				return new ProxyRequest("Too Many Words In Request",400);
			}

			if ( !requestWords[0].equals("CONNECT") ) {
				// HTTP/1.1 501 Only CONNECT Implemented
				return new ProxyRequest("Only CONNECT Implemented",501);
			}

			// Discard headers, not currently used
			String headerLine = clientReader.readLine();
			while(headerLine.length() != 0 ) 
				headerLine = clientReader.readLine();

			return new ProxyRequest(requestWords[1],200);

		} catch (IOException e) {
			e.printStackTrace();
			return new ProxyRequest("",500);
		}
	}

	private void writeErrorResponseAndClose(Socket clientConn, int response, String reason) {

		try {
			var clientWriter = new BufferedWriter(new OutputStreamWriter(clientConn.getOutputStream(), "US-ASCII"));
			clientWriter.write("HTTP/1.1 "+response+" "+reason);
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

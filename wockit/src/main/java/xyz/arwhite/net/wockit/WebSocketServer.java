package xyz.arwhite.net.wockit;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebSocketServer implements Runnable {

	private ExecutorService connectionPool;
	
	private URI uri;
	private WebSocketAdapter handler;
	
	public WebSocketServer(int maxThreads, String fullURI, WebSocketAdapter handler) throws URISyntaxException {
		this.handler = handler;
		connectionPool = Executors.newFixedThreadPool(maxThreads);
	
		uri = new URI(fullURI);
	}

	@Override
	public void run() {
		try {
			/*
			 * Listen for incoming connections
			 */
			InetAddress address = InetAddress.getByName(uri.getHost());
			ServerSocket server = new ServerSocket(uri.getPort(),0,address);
			
			/*
			 * Accept & Dispatch
			 * New incoming connections are sent to a thread in the pool to handle
			 */
			while(true) {
				Socket conn = server.accept();
				connectionPool.execute(new WebSocketConnection(conn,handler));
			}
			
			
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}

package xyz.arwhite.net.proxit;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServer implements Runnable {

	private ExecutorService connectionPool;
	private ExecutorService ioWorkerPool;
	private int tcpPort = 2580;
	
	public ProxyServer(int maxThreads) {
		connectionPool = Executors.newFixedThreadPool(maxThreads);
		ioWorkerPool = Executors.newFixedThreadPool(maxThreads * 2);
	}

	public void run() {
		try {
			ServerSocket server = new ServerSocket(tcpPort);
			
			/*
			 * Listen & Dispatch
			 * New incoming connections are sent to a thread in the pool to handle
			 */
			while(true) {
				Socket conn = server.accept();
				connectionPool.execute(new ProxyConnection(conn,ioWorkerPool));
			}
			
			
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}

package xyz.arwhite.net.proxit;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import xyz.arwhite.net.auth.AuthServer;

public class ProxyServer implements Runnable {

	private final static String PROXIT_PORT_ENV_VAR = "PROXIT_PORT";
	private final static String AUTH_SERVER_ENV_VAR = "PROXIT_AUTH_SERVER";

	private ExecutorService connectionPool;
	private ExecutorService ioWorkerPool;
	private int tcpPort = 2580;
	private AuthServer authServer;

	public ProxyServer(int maxThreads) throws Exception {
		connectionPool = Executors.newFixedThreadPool(maxThreads);
		ioWorkerPool = Executors.newFixedThreadPool(maxThreads * 2);

		/*
		 * Override TCP Port Proxit will listen on, if specified
		 */
		var portVar = System.getenv(PROXIT_PORT_ENV_VAR);
		if ( portVar != null ) {
			try {
				tcpPort = Integer.parseInt(portVar);
			} catch(Exception e) {
				throw(new Exception("Proxit TCP Port variable not set to integer value",e));
			}
		}

		/*
		 * Establish authorisation server integration
		 * 
		 * Note: if using self-signed TLS certs on the Auth Server, ensure the jvm has
		 * loaded your custom cacerts file using the runtime flag
		 * java -Djavax.net.ssl.trustStore=../cacerts -Djavax.net.ssl.trustStorePassword=$TS_PASSWORD
		 */
		String authServerVar = System.getenv(AUTH_SERVER_ENV_VAR);
		if ( authServerVar == null )
			throw new Exception("No trusted authorisation server URL environment variable");

		authServer = new AuthServer(URI.create(authServerVar));

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
				connectionPool.execute(new ProxyConnection(conn,ioWorkerPool,authServer));
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}

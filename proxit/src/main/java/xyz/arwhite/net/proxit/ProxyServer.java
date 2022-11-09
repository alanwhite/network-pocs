package xyz.arwhite.net.proxit;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import xyz.arwhite.net.auth.AuthServer;

public class ProxyServer implements Runnable {

	private final static String PROXIT_DEFAULT_PORT = "2580";

	private final static String PROXIT_PORT_ENV_VAR = "PROXIT_PORT";
	private final static String AUTH_SERVER_ENV_VAR = "PROXIT_AUTH_SERVER";

	private ExecutorService connectionPool;
	private ExecutorService ioWorkerPool;
	private int tcpPort = 0;
	private AuthServer authServer;

	public ProxyServer(int maxThreads, boolean vThreads) throws Exception {
		connectionPool = Executors.newVirtualThreadPerTaskExecutor();
		ioWorkerPool = Executors.newVirtualThreadPerTaskExecutor();
		
		configure();
	}
	
	public ProxyServer(int maxThreads) throws Exception {
		connectionPool = Executors.newFixedThreadPool(maxThreads);
		ioWorkerPool = Executors.newFixedThreadPool(maxThreads * 2);
		
		configure();
	}
		
	private void configure() throws Exception {
		/*
		 * Override TCP Port Proxit will listen on, if specified
		 */
		try {
			tcpPort = Integer.parseInt(
					Objects.requireNonNullElse(System.getenv(PROXIT_PORT_ENV_VAR), PROXIT_DEFAULT_PORT));
		} catch(Exception e) {
			throw(new Exception("Proxit TCP Port variable not set to integer value",e));
		}

		/*
		 * Establish authorisation server integration
		 * 
		 * Note: if using self-signed TLS certs on the Auth Server, ensure the jvm has
		 * loaded your custom cacerts file using the runtime flag
		 * java -Djavax.net.ssl.trustStore=../cacerts -Djavax.net.ssl.trustStorePassword=$TS_PASSWORD
		 * 
		 * TODO: add in the option to read trusted certs in from a pem file
		 */
		String authServerVar = System.getenv(AUTH_SERVER_ENV_VAR);
		if ( authServerVar == null ) {
			System.out.println("Not using token authentication");
			authServer = null;
		} else
			authServer = new AuthServer(URI.create(authServerVar));
	}

	public void run() {
		System.out.println("Proxy listening on port "+tcpPort);
		try (ServerSocket server = new ServerSocket(tcpPort)) {
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

package xyz.arwhite.net.proxit;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import xyz.arwhite.net.auth.AuthServer;

public class ProxyServer implements Runnable {

	/*
	 * If not overriden, this will be the TCP Port the proxy listens on
	 */
	private final static String PROXIT_DEFAULT_PORT = "2580";

	/*
	 * If present, the contents of this env variable will be used to define
	 * the TCP Port the proxy listens on
	 */
	private final static String PROXIT_PORT_ENV_VAR = "PROXIT_PORT";
	
	/*
	 * If defined, proxit will use the Auth Server at this URL to validate 
	 * JWTs provided either as passwords in basic auth, or bearer tokens as 
	 * a proxy header.
	 */
	private final static String AUTH_SERVER_ENV_VAR = "PROXIT_AUTH_SERVER";
	
	/*
	 * If this env variable is set to the name of a file containing a cacert pem
	 * it will be used to validate the certs presented by the Auth Server 
	 */
	private final static String AUTH_SERVER_CACERT_ENV_VAR = "PROXIT_AUTH_CACERT";

	/*
	 * Used if not using java virtual threads
	 */
	private ExecutorService connectionPool;
	private ExecutorService ioWorkerPool;
	
	/*
	 * The TCP Port the proxy will listen on
	 */
	private int tcpPort = 0;
	
	/*
	 * The Auth Server can be used to validate JWTs presented as bearer tokens or basic 
	 * auth passwords on the proxy request. Principle being an Auth Server can be used
	 * to authorize a caller to use the proxy. If the Auth Server is specified, then a
	 * valid JWT must be provided for proxit to honour the proxy request.
	 */
	private Optional<AuthServer> authServer = Optional.empty();

	/**
	 * Constructor used if virtual threads are to be used
	 * 
	 * @param maxThreads TODO: use this to control max concurrency
	 * @param vThreads
	 * @throws Exception
	 */
	public ProxyServer(int maxThreads, boolean vThreads) throws Exception {
		connectionPool = Executors.newVirtualThreadPerTaskExecutor();
		ioWorkerPool = Executors.newVirtualThreadPerTaskExecutor();

		configure();
	}

	/**
	 * Constructor to use if platform threads are to be used
	 * 
	 * @param maxThreads
	 * @throws Exception
	 */
	public ProxyServer(int maxThreads) throws Exception {
		connectionPool = Executors.newFixedThreadPool(maxThreads);
		ioWorkerPool = Executors.newFixedThreadPool(maxThreads * 2);

		configure();
	}

	private void configure() throws IllegalArgumentException {
		/*
		 * Override TCP Port Proxit will listen on, if specified
		 */
		try {
			tcpPort = Integer.parseInt(
					Objects.requireNonNullElse(System.getenv(PROXIT_PORT_ENV_VAR), PROXIT_DEFAULT_PORT));
			
		} catch(NumberFormatException e) {
			throw(new IllegalArgumentException("Proxit TCP Port variable not set to integer value",e));
		}

		/*
		 * Establish authorisation server integration
		 * 
		 * Note: if using self-signed TLS certs on the Auth Server, ensure the jvm has
		 * loaded your custom cacerts file using the runtime flag
		 * java -Djavax.net.ssl.trustStore=../cacerts -Djavax.net.ssl.trustStorePassword=$TS_PASSWORD
		 * 
		 * or supply a PEM file
		 */

		var authServerURL = System.getenv(AUTH_SERVER_ENV_VAR);
		
		if ( authServerURL == null ) {
			System.out.println("Not using token authentication");
			
		} else {
			Optional<String> cacertFile = Optional.of(
					(String) Objects.requireNonNullElse(System.getenv(AUTH_SERVER_CACERT_ENV_VAR), Optional.empty()));
			
			try {
				authServer = Optional.of(new AuthServer(URI.create(authServerURL),cacertFile));
				
			} catch (Exception e) {
				System.out.println("Unable to use the specified Auth Server URL");
				e.printStackTrace();
			}
		}
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

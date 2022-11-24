package xyz.arwhite.net.proxit;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

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
	 * If this env variable is set to the name of a PEM file containing a cert
	 * and private key, proxit will only accept encrypted CONNECT requests
	 */
	private final static String PROXY_TLS_PEM = "PROXIT_TLS_PEM";

	/*
	 * Required if the PROXY_TLS_PEM variable is set, in order to decrypt the
	 * private key in the PEM file. 
	 * 
	 * TODO: encrypted private keys NOT YET SUPPORTED private keys must not encrypted in
	 * the pkcs12 pem file (hint: openssl pkcas12 -nodes)
	 */
	private final static String PROXY_TLS_PASS = "PROXIT_TLS_PASS";

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
	 * Used to create the socket the proxy will listen on, is set to either a
	 * plain socket or a SSL socket if a cert and key are provided in a PEM file
	 */
	private ServerSocketFactory proxySocketFactory;

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
		 * Set up TLS on the proxy listener if requested
		 */
		proxySocketFactory = this.configureProxySocketFactory(
				System.getenv(PROXY_TLS_PEM),
				System.getenv(PROXY_TLS_PASS));

		/*
		 * Set up Auth Server integration if requested
		 */
		authServer = this.configureAnyAuthServer(System.getenv(AUTH_SERVER_ENV_VAR));

	}

	/**
	 * Establish the socket factory that will be used to create the socket that proxit will use.
	 * 
	 * If the provided filename is a valid PEM file with a private key and cert then
	 * the SSL socket factory will be used, presenting the provided cert for TLS. 
	 * 
	 * Otherwise the default plain server socket factory is used.
	 * 
	 * @param getenv
	 * @return
	 */
	private ServerSocketFactory configureProxySocketFactory(String pemFile, String pemPass) {
		var factory = ServerSocketFactory.getDefault();
		
		if ( pemFile == null )
			return factory;

		var keyStoreOpt = ProxyTLS.getKeyStoreFromFile(pemFile, pemPass);

		if ( keyStoreOpt.isEmpty() )
			return factory;

		try {
			KeyManagerFactory kmf = KeyManagerFactory
					.getInstance(KeyManagerFactory.getDefaultAlgorithm());

			kmf.init(keyStoreOpt.get(), "proxytls".toCharArray());

			var sslContext = SSLContext.getInstance("TLS");
			sslContext.init(kmf.getKeyManagers(), null, SecureRandom.getInstanceStrong());
			factory = sslContext.getServerSocketFactory();

		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnrecoverableKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (KeyStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (KeyManagementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return factory;
	}

	/**
	 * Establish authorisation server integration 
	 * 
	 * Note: if using self-signed TLS certs on the Auth Server, ensure the jvm has
	 * loaded your custom cacerts file using the runtime flag
	 * java -Djavax.net.ssl.trustStore=../cacerts -Djavax.net.ssl.trustStorePassword=$TS_PASSWORD
	 * 
	 * or supply a PEM file 
	 */
	private Optional<AuthServer> configureAnyAuthServer(String authServerURL) {
		Optional<AuthServer> response = Optional.empty();

		if ( authServerURL == null ) {
			System.out.println("Not using token authentication");

		} else {
			Optional<String> cacertFile = Optional.of(
					(String) Objects.requireNonNullElse(System.getenv(AUTH_SERVER_CACERT_ENV_VAR), Optional.empty()));

			try {
				response = Optional.of(new AuthServer(URI.create(authServerURL),cacertFile));

			} catch (Exception e) {
				System.out.println("Unable to use the specified Auth Server URL");
				e.printStackTrace();
			}
		}

		return response;
	}

	public void run() {
		System.out.println("Proxy listening on port "+tcpPort);

		try (var server = proxySocketFactory.createServerSocket(tcpPort)) {
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

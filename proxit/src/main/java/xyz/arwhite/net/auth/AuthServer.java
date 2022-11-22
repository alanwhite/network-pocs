package xyz.arwhite.net.auth;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class AuthServer {

	private URI authServerURI;

	private OpenIDConfiguration oidConfig;

	private CertsCache sigVerifiers = new CertsCache();

	private SSLContext sslContext;

	public AuthServer(URI authServerURI, Optional<String> cacertFile) throws Exception {
		this.authServerURI = authServerURI;

		/*
		 * Use any provided cert to establish trust with the Auth Server
		 */
		sslContext = AuthServer.createSSLContext(cacertFile);

		/*
		 * Obtain the metadata that defines how this Auth Server operates
		 */
		oidConfig = OpenIDConfiguration.fetchFrom(this.authServerURI, sslContext);

		/*
		 * Cache the public keys this Auth Server uses to sign certs, update every 6 hours
		 * Alternatively/additionally we could refresh if we get a cache miss on a k-id 
		 * and queue missed k-ids until done (proxit.later)
		 */
		ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
		ses.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				try {
					CertsCache.refresh(sigVerifiers, oidConfig, sslContext);
				} catch (Exception e) {
					// report this with whatever logger we use
					e.printStackTrace();
				}
			}
		}, 0, 6, TimeUnit.HOURS);

	}

	/**
	 * Returns the SSL Context to be used by all HTTPS calls to the Auth Server.
	 * If a filename is provided containing a CA Root cert it will be used in the return
	 * SSL context to validate any cert presented by the Auth Server.
	 * 
	 * @param cacertFile
	 * @return
	 * @throws NoSuchAlgorithmException
	 */
	protected static SSLContext createSSLContext(Optional<String> cacertFile) throws NoSuchAlgorithmException {
		
		try {
			if ( cacertFile.isEmpty() )
				return SSLContext.getDefault();
			
			var ownTrustStore = AuthServer.getTrustStoreFromFile(cacertFile.get());
			if ( ownTrustStore.isEmpty() )
				return SSLContext.getDefault();
			
			// set up own ssl context
			var tmf = TrustManagerFactory.getInstance("PKIX");
			tmf.init(ownTrustStore.get());
			
			var sslContext = SSLContext.getInstance("TLSv1.3");
			sslContext.init(null, tmf.getTrustManagers(), null);
			
			return sslContext;
			
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyStoreException e) {
			System.out.println("unable to use auth server cacert for trust");
			e.printStackTrace();
		} catch (KeyManagementException e) {
			System.out.println("unable to use auth server cacert for TLS");
			e.printStackTrace();
		}
		
		return SSLContext.getDefault();
	}
	
	/**
	 * Builds a keystore populated with the CA Root cert in the supplied filename
	 * 
	 * @param cacertPEMFile containing the CA Root cert 
	 * @return
	 */
	protected static Optional<KeyStore> getTrustStoreFromFile(String cacertPEMFile) {
		
		Optional<KeyStore> response = Optional.empty();
		
		try( var pemReader = new BufferedReader(
				new InputStreamReader(new FileInputStream(cacertPEMFile))) ) {
			var certFactory = CertificateFactory.getInstance("X.509");
			
			List<String> certStrings = new ArrayList<>();
			StringBuilder certData = new StringBuilder();
			String line = pemReader.readLine();
			while( line != null ) {
				if ( line.contains("BEGIN CERTIFICATE") ) 
					certData = new StringBuilder();
				else if ( line.contains("END CERTIFICATE")) 
					certStrings.add(certData.toString());
				else
					certData.append(line);
				
				line = pemReader.readLine();
			}
			
			if ( certStrings.isEmpty() ) {
				System.out.println("no certs found in trust PEM");
				return response;
			}
			
			KeyStore authServerTrustStore = KeyStore.getInstance("pkcs12");
			authServerTrustStore.load(null,null);
			
			for ( String certString : certStrings ) {
				byte[] certBytes = Base64.getDecoder().decode(certString);
				var cert = certFactory.generateCertificate(new ByteArrayInputStream(certBytes));
				authServerTrustStore.setCertificateEntry(Integer.toString(cert.hashCode()), cert);
			}

			response = Optional.of(authServerTrustStore);
			
		} catch (KeyStoreException e) {
			System.out.println("unable to create auth server trust store");
			e.printStackTrace();
		} catch (FileNotFoundException e1) {
			System.out.println("cacert file supplied for auth server does not exist");
			e1.printStackTrace();
		} catch (IOException e1) {
			System.out.println("unable to read the auth server cacertfile");
			e1.printStackTrace();
		} catch (CertificateException e) {
			System.out.println("unable to process certificate in auth server cacert file");
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			System.out.println("unable to initialize auth server trust store");
			e.printStackTrace();
		} 
		
		return response;

	}

	public CertsCache getSigVerifiers() {
		return sigVerifiers;
	}

}

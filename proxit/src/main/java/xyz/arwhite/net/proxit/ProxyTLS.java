package xyz.arwhite.net.proxit;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;

public class ProxyTLS {

	/**
	 * Builds a keystore populated with the certs and private key in the supplied filename
	 * 
	 * @param certPEMFile containing the certs and private key 
	 * @return
	 */
	protected static Optional<KeyStore> getKeyStoreFromFile(String certPEMFile, String passPhrase) {

		Optional<KeyStore> response = Optional.empty();
		
		try( var pemReader = new BufferedReader(
				new InputStreamReader(new FileInputStream(certPEMFile))) ) {

			var certStrings = new ArrayList<String>();
			var certData = new StringBuilder();
			var keyStrings = new ArrayList<String>();
			var keyData = new StringBuilder();

			boolean isCertData = false;
			String line = pemReader.readLine();
			while( line != null ) {
				if ( line.contains("BEGIN CERTIFICATE") ) {
					certData = new StringBuilder();
					isCertData = true;
					
				} else if ( line.contains("BEGIN PRIVATE KEY") ) {
					keyData = new StringBuilder();
					isCertData = false;
					
				} else if ( line.contains("END CERTIFICATE")) 
					certStrings.add(certData.toString());
				
				else if ( line.contains("END PRIVATE KEY")) 
					keyStrings.add(keyData.toString());
				
				else if ( isCertData )
					certData.append(line);
	
				else
					keyData.append(line);

				line = pemReader.readLine();
			}

			if ( certStrings.isEmpty() ) {
				System.out.println("no certs found in proxy PEM file");
				return response;
			}
			
			if ( keyStrings.isEmpty() || keyStrings.size() != 1 ) {
				System.out.println("incorrect number of private keys in proxy PEM file");
				return response;
			}

			// cert chain
			var certs = new ArrayList<Certificate>();
			var certFactory = CertificateFactory.getInstance("X.509");

			for ( String certString : certStrings ) {
				byte[] certBytes = Base64.getDecoder().decode(certString);
				var cert = certFactory.generateCertificate(new ByteArrayInputStream(certBytes));
				certs.add(cert);
			}
			
			// private key
			byte [] keyBytes = Base64.getDecoder().decode(keyStrings.get(0));
			var keyFactory = KeyFactory.getInstance("RSA");
			
			var keySpec = new PKCS8EncodedKeySpec(keyBytes);
			var privateKey = keyFactory.generatePrivate(keySpec);
			
			// load private key and cert chain into keystore for TLS
			var proxyKeyStore = KeyStore.getInstance("pkcs12");
			proxyKeyStore.load(null,null);
			proxyKeyStore.setKeyEntry("proxy_tls_cert", privateKey, 
					"proxytls".toCharArray(), certs.toArray(new Certificate[0]));
			
			response = Optional.of(proxyKeyStore);

		} catch (KeyStoreException e) {
			System.out.println("unable to create proxy server key store");
			e.printStackTrace();
		} catch (FileNotFoundException e1) {
			System.out.println("cert file supplied for proxy does not exist");
			e1.printStackTrace();
		} catch (IOException e1) {
			System.out.println("unable to read the proxy cert file");
			e1.printStackTrace();
		} catch (CertificateException e) {
			System.out.println("unable to process certificate in proxy cert file");
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			System.out.println("unable to initialize proxy key store");
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			System.out.println("private key in proxy cert file not usable");
			e.printStackTrace();
		} 

		return response;

	}
}



package xyz.arwhite.net.proxit;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

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

	/**
	 * Builds a java keystore from a PEM file, that can be used to present a server TLS cert by
	 * SSLSocketServer or code derived from that, like https listeners
	 * 
	 * The PEM file must contain a single private key and the associated certificate plus any 
	 * intermediate certs required (ie the cert chain without the root ca cert).
	 * 
	 * The private key may be specified with BEGIN PRIVATE KEY or BEGIN ENCRYPTED PRIVATE KEY.
	 * If encrypted it must be with the AES alg, as that's all java crypto understands. If using
	 * openssl to create the pem file, use the -aes256 flag otherwise it uses 3des which java 
	 * doesn't know how to decrypt.
	 * 
	 * @param certPEMFile the pkcs12 pem file containing the private key and cert chain
	 * @param passPhrase an Optional that must contain the pass phrase for an encrypted private key
	 * @return an Optional populated with a KeyStore if successful, otherwise empty
	 */
	protected static Optional<KeyStore> createKeyStoreFromPEM(String certPEMFile, Optional<String> passPhrase) {

		Optional<KeyStore> response = Optional.empty();

		try( var pemReader = new BufferedReader(
				new InputStreamReader(new FileInputStream(certPEMFile))) ) {

			var certStrings = new ArrayList<String>();
			var certData = new StringBuilder();
			var keyStrings = new ArrayList<String>();
			var keyData = new StringBuilder();

			boolean isCertData = false;
			boolean isPrivateKeyEncrypted = false;
			String line = pemReader.readLine();
			while( line != null ) {
				if ( line.contains("BEGIN CERTIFICATE") ) {
					certData = new StringBuilder();
					isCertData = true;

				} else if ( line.contains("BEGIN ENCRYPTED PRIVATE KEY") ) {
					isPrivateKeyEncrypted = true;
					keyData = new StringBuilder();
					isCertData = false;

				} else if ( line.contains("BEGIN PRIVATE KEY") ) {
					keyData = new StringBuilder();
					isCertData = false;

				} else if ( line.contains("END CERTIFICATE")) 
					certStrings.add(certData.toString());

				else if ( line.contains("END ENCRYPTED PRIVATE KEY")) 
					keyStrings.add(keyData.toString());

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

			/*
			 * Add magic here for decrypt
			 * 
			 * https://stackoverflow.com/questions/2654949/how-to-read-a-password-encrypted-key-with-java
			 */

			PKCS8EncodedKeySpec keySpec;

			if ( isPrivateKeyEncrypted ) {
				var encryptPKInfo = new EncryptedPrivateKeyInfo(keyBytes);
				var cipher = Cipher.getInstance(encryptPKInfo.getAlgName());

				var pbeKeySpec = new PBEKeySpec(passPhrase.get().toCharArray());
				var secFac = SecretKeyFactory.getInstance(encryptPKInfo.getAlgName());
				var pbeKey = secFac.generateSecret(pbeKeySpec);

				var algParams = encryptPKInfo.getAlgParameters();
				cipher.init(Cipher.DECRYPT_MODE, pbeKey, algParams);
				keySpec = encryptPKInfo.getKeySpec(cipher);
			} else
				keySpec = new PKCS8EncodedKeySpec(keyBytes);

			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
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
		} catch (FileNotFoundException e) {
			System.out.println("cert file supplied for proxy does not exist");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("unable to read the proxy cert file (or a probs reading the private key encryption error)");
			e.printStackTrace();
		} catch (CertificateException e) {
			System.out.println("unable to process certificate in proxy cert file");
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			System.out.println("unable to initialize proxy key store");
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			System.out.println("private key in proxy cert file not usable");
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			System.out.println("probs getting cipher for algname");
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			System.out.println("probs initializing cipher for algname");
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			System.out.println("probs initializing cipher for algname");
			e.printStackTrace();
		} 

		return response;

	}

}



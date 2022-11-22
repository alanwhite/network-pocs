package xyz.arwhite.net.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class AuthServerTest {

	@Test
	void loadPEMFileTest() throws KeyStoreException {
		// call the loadcert method using test data
		
		var trustStore = AuthServer.getTrustStoreFromFile(
				getClass().getClassLoader().getResource("test-cert.pem").getFile());
		
		assertTrue(trustStore.isPresent());

		var trusting = trustStore.get();
		var numCerts = trusting.size();
		
		assertTrue(1 == numCerts);
	}
	
	@Test
	void badPEMFileName() {
		var trustStore = AuthServer.getTrustStoreFromFile("bah.humbug");
		assertTrue(trustStore.isEmpty());
		
	}
	
	
	@Test
	void emptyPEMFile() throws KeyStoreException {
		var trustStore = AuthServer.getTrustStoreFromFile(
				getClass().getClassLoader().getResource("empty.pem").getFile());
		
		assertTrue(trustStore.isEmpty());
		
	}
	
	@Test
	void createSSLContextTest() throws NoSuchAlgorithmException {
		
		var sslContext = AuthServer.createSSLContext(Optional.of(
				getClass().getClassLoader().getResource("test-cert.pem").getFile()));
		
		assertNotNull(sslContext);
	}

	@Test
	void noSuchFileSSLContextTest() throws NoSuchAlgorithmException {
		
		var sslContext = AuthServer.createSSLContext(Optional.of("fred.bloggs"));
		
		assertNotNull(sslContext); // should be a default one
	}
	
	@Test
	void noPEMProvidedSSLContextTest() throws NoSuchAlgorithmException {
		
		var sslContext = AuthServer.createSSLContext(Optional.empty());
		
		assertNotNull(sslContext); // should be a default one
	}
}

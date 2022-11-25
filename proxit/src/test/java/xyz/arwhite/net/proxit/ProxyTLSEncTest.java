package xyz.arwhite.net.proxit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProxyTLSEncTest {

	@BeforeAll
	static void setUpBeforeClass() throws Exception {

	}

	@Test
	void loadValidPEMFileTest() {
		
		var keyStore = ProxyTLS.createKeyStoreFromPEM(
				getClass().getClassLoader().getResource("test-enc-pkey.pem").getFile()
				, Optional.of("encrpkey"));
		
		assertTrue(keyStore.isPresent());
	}

	@Test
	void loadEmptyPEMFileTest() {
		
		var keyStore = ProxyTLS.createKeyStoreFromPEM(
				getClass().getClassLoader().getResource("empty.pem").getFile()
				, Optional.of("encrpkey"));
		
		assertTrue(keyStore.isEmpty());
	}
}

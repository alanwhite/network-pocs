package xyz.arwhite.net.proxit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProxyTLSTest {

	@BeforeAll
	static void setUpBeforeClass() throws Exception {

	}

	@Test
	void loadValidPEMFileTest() {
		
		var keyStore = ProxyTLS.getKeyStoreFromFile(
				getClass().getClassLoader().getResource("tlscertkey.pem").getFile()
				, null);
		
		assertTrue(keyStore.isPresent());
	}

}

package xyz.arwhite.net.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.fusionauth.jwt.Verifier;
import io.fusionauth.jwt.rsa.RSAVerifier;

class CertsCacheTest {

	private static CertsCache cache = new CertsCache();
	private static OpenIDConnectCerts refreshList = new OpenIDConnectCerts();
	
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		
		String pub = "-----BEGIN PUBLIC KEY-----\n"
				+ "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuI/ZOqqwGLOXfDZQ04b6\n"
				+ "boMWKrv1OW9a3A7VtirUwPGsMaK8v2MlSEmV7NyKs0Wt1osXsb8y+izJTLYS6sS0\n"
				+ "suuDj43CNmC0XIvH0Az9+KmZ98neRA4T5mKrDsGC54GeQtbk2yE5rdZpq2/FT05v\n"
				+ "WgmG9Qr3Xgjjb79qfLA+n55qRgvlkxfeLghPvB0/WBEEJ48MoS4McZHdWQ+jHCJP\n"
				+ "mvqWTIF9fYatsB5WJFw3ZbqFQaid2S41pI0mEO57SstiMXpowy51XK2BKV4Glx/N\n"
				+ "mC4rBXi39bgqi3q1oXvxlVCBFpY+O2F+sCK4pvooo35R9e92i0qZaKRP/Dl6LaJs\n"
				+ "YwIDAQAB\n"
				+ "-----END PUBLIC KEY-----";
		Verifier ver = RSAVerifier.newVerifier(pub);
		
		/*
		 * Set cache to have 2 entries beforehand e1 & e2
		 */
		cache.put("e1", ver);
		cache.put("e2", ver);
		
		/*
		 * Create refresh list with e2 and e3 entries
		 */
		var re2 = new OpenIDConnectKey();
		re2.kid = "e2";
		re2.x5c = new ArrayList<String>();
		re2.x5c.add(pub);
		var re3 = new OpenIDConnectKey();
		re3.kid = "e3";
		re3.x5c = new ArrayList<String>();
		re3.x5c.add(pub);
		
		refreshList.keys = new ArrayList<>();
		refreshList.keys.add(re2);
		refreshList.keys.add(re3);
		
	}

	@Test
	void test() {
		assertEquals(2,cache.entrySet().size());
		assertEquals(2,refreshList.keys.size());
		
		CertsCache.refresh(cache, refreshList);
		
		assertEquals(2,cache.entrySet().size());
		assertEquals(2,refreshList.keys.size());
		
		assertNull(cache.get("e1"));
		assertNotNull(cache.get("e2"));
		assertNotNull(cache.get("e3"));
		
	}

}

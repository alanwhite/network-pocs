package xyz.arwhite.net.auth;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;

import io.fusionauth.jwt.Verifier;
import io.fusionauth.jwt.rsa.RSAVerifier;

@SuppressWarnings("serial")
public class CertsCache extends ConcurrentHashMap<String, Verifier>{

	/*
	 * Testable method to update a key cache based on required list of keys.
	 * We cannot completely renew the concurrent hashmap based on a list without
	 * causing potential for inaccurate JWT signature validation, hence this
	 * piecemeal approach.
	 */
	public static void refresh(CertsCache cache, OpenIDConnectCerts certs) {
		// add or refresh existing entries
		certs.keys.forEach(key -> { 
			try {
				String pub = "-----BEGIN CERTIFICATE-----\n"
						+ key.x5c.get(0)+"\n"
						+ "-----END CERTIFICATE-----";

				cache.putIfAbsent(key.kid, RSAVerifier.newVerifier(pub)); 
			} catch(Exception e) {
				e.printStackTrace();
				System.out.println("ignoring public key for "+key.kid);
			}

		});

		// remove any entries that are not in the refreshed certs list of keys
		cache.entrySet().removeIf(e -> certs.keys.stream()
				.filter(key -> key.kid == e.getKey())
				.findFirst()
				.isEmpty() );
	}

	public static void refresh(CertsCache cache, OpenIDConfiguration oidConfig, SSLContext sslContext) throws Exception {
		refresh(cache, OpenIDConnectCerts.fetchFrom(URI.create(oidConfig.jwksUri), sslContext));
	}

}

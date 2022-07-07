package xyz.arwhite.net.auth;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.fusionauth.jwt.Verifier;

public class AuthServer {

	private URI authServerURI;

	private OpenIDConfiguration oidConfig;

	private CertsCache sigVerifiers = new CertsCache();

	public AuthServer(URI authServerURI) throws Exception {
		this.authServerURI = authServerURI;

		/*
		 * Obtain the metadata that defines how this Auth Server operates
		 */
		oidConfig = OpenIDConfiguration.fetchFrom(this.authServerURI);
		
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
					CertsCache.refresh(sigVerifiers, oidConfig);
				} catch (Exception e) {
					// report this with whatever logger we use
					e.printStackTrace();
				}
			}
		}, 0, 6, TimeUnit.HOURS);

	}

	public CertsCache getSigVerifiers() {
		return sigVerifiers;
	}
	
}

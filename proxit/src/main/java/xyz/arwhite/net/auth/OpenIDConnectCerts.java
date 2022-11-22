package xyz.arwhite.net.auth;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;

import javax.net.ssl.SSLContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OpenIDConnectCerts {
	
	public List<OpenIDConnectKey> keys;
	
	public static OpenIDConnectCerts fetchFrom(URI jwksURI, SSLContext sslContext) throws Exception {
		
		HttpClient client = HttpClient.newBuilder()
				.version(Version.HTTP_1_1)
				.followRedirects(Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(20))
				.sslContext(sslContext)
				.build();
		
		HttpRequest request = HttpRequest.newBuilder()
				.uri(jwksURI)
				.header("Content-Type", "application/json")
				.GET()
				.build();

		HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
		if ( response.statusCode() != HttpURLConnection.HTTP_OK )
			throw new Exception("Failed to retrieve Auth Server cert keys "+response.statusCode());
		
		OpenIDConnectCerts certs = new ObjectMapper()
				.readerFor(OpenIDConnectCerts.class)
				.readValue(response.body());
		
		return certs;
	}


}

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
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OpenIDConfiguration {

	public String issuer;
	public String authorizationEndpoint; 
	public String tokenEndpoint; 
	public String introspectionEndpoint;
	public String userinfoEndpoint;
	public String endSessionEndpoint; 
	public boolean frontchannelLogoutSessionSupported; 
	public boolean frontchannelLogoutSupported; 
	public String jwksUri; 
	public String checkSessionIframe; 
	public List<String> grantTypesSupported; 
	public List<String> acrValuesSupported; 
	public List<String> responseTypesSupported; 
	public List<String> subjectTypesSupported; 
	public List<String> idTokenSigningAlgValuesSupported; 
	public List<String> idTokenEncryptionAlgValuesSupported; 
	public List<String> idTokenEncryptionEncValuesSupported; 
	public List<String> userinfoSigningAlgValuesSupported; 
	public List<String> userinfoEncryptionAlgValuesSupported; 
	public List<String> userinfoEncryptionEncValuesSupported; 
	public List<String> requestObjectSigningAlgValuesSupported; 
	public List<String> requestObjectEncryptionAlgValuesSupported; 
	public List<String> requestObjectEncryptionEncValuesSupported; 
	public List<String> responseModesSupported; 
	public String registrationEndpoint; 
	public List<String> tokenEndpointAuthMethodsSupported; 
	public List<String> tokenEndpointAuthSigningAlgValuesSupported; 
	public List<String> introspectionEndpointAuthMethodsSupported; 
	public List<String> introspectionEndpointAuthSigningAlgValuesSupported; 
	public List<String> authorizationSigningAlgValuesSupported; 
	public List<String> authorizationEncryptionAlgValuesSupported; 
	public List<String> authorizationEncryptionEncValuesSupported; 
	public List<String> claimsSupported; 
	public List<String> claimTypesSupported; 
	public boolean claimsParameterSupported; 
	public List<String> scopesSupported; 
	public boolean requestParameterSupported; 
	public boolean requestUriParameterSupported; 
	public boolean requireRequestUriRegistration;
	public List<String> codeChallengeMethodsSupported; 
	public boolean tlsClientCertificateBoundAccessTokens; 
	public String revocationEndpoint;
	public List<String> revocationEndpointAuthMethodsSupported; 
	public List<String> revocationEndpointAuthSigningAlgValuesSupported; 
	public boolean backchannelLogoutSupported; 
	public boolean backchannelLogoutSessionSupported; 
	public String device_authorization_endpoint; 
	public List<String> backchannelTokenDeliveryModesSupported; 
	public String backchannelAuthenticationEndpoint; 
	public List<String> backchannelAuthenticationRequestSigningAlgValuesSupported; 
	public boolean requirePushedAuthorizationRequests; 
	public String pushedAuthorizationRequestEndpoint; 
	public String mtlsTokenEndpoint;
	public String mtlsRevocationEndpoint;
	public String mtlsIntrospectionEndpoint;
	public String mtlsDeviceAuthorizationEndpoint;
	public String mtlsRegistrationEndpoint;
	public String mtlsUserinfoEndpoint;
	public String mtlsPushedAuthorizationRequestEndpoint;
	public String mtlsBackchannelAuthenticationEndpoint;

	@JsonProperty("mtls_endpoint_aliases")
    private void unpackMTLS(Map<String,Object> mtls_endpoint_aliases) {
        this.mtlsTokenEndpoint = (String)mtls_endpoint_aliases.get("token_endpoint");
        this.mtlsRevocationEndpoint = (String)mtls_endpoint_aliases.get("revocation_endpoint");
        this.mtlsIntrospectionEndpoint = (String)mtls_endpoint_aliases.get("introspection_endpoint");
        this.mtlsDeviceAuthorizationEndpoint = (String)mtls_endpoint_aliases.get("device_authorization_endpoint");
        this.mtlsRegistrationEndpoint = (String)mtls_endpoint_aliases.get("registration_endpoint");
        this.mtlsUserinfoEndpoint = (String)mtls_endpoint_aliases.get("userinfo_endpoint");
        this.mtlsPushedAuthorizationRequestEndpoint = (String)mtls_endpoint_aliases.get("pushed_authorization_request_endpoint");
        this.mtlsBackchannelAuthenticationEndpoint = (String)mtls_endpoint_aliases.get("backchannel_authentication_endpoint");
    }
	
	public static OpenIDConfiguration fetchFrom(URI authServerURI) throws Exception {
		
		HttpClient client = HttpClient.newBuilder()
				.version(Version.HTTP_1_1)
				.followRedirects(Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(20))
				.build();
		
		var uri = URI.create(authServerURI+"/.well-known/openid-configuration");
		
		HttpRequest request = HttpRequest.newBuilder()
				.uri(uri)
				.header("Content-Type", "application/json")
				.GET()
				.build();

		HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
		if ( response.statusCode() != HttpURLConnection.HTTP_OK )
			throw new Exception("Failed to retrieve Auth Server config "+response.statusCode());
		
		OpenIDConfiguration config = new ObjectMapper()
				.readerFor(OpenIDConfiguration.class)
				.readValue(response.body());
				
		return config;
		
	}

}

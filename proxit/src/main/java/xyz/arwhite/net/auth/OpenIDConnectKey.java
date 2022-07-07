package xyz.arwhite.net.auth;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OpenIDConnectKey {

	public String kid;
	public String kty;
	public String alg;
	public String use;
	public String n;
	public String e;
	public List<String> x5c;
	public String x5t;
	
	@JsonProperty("x5t#S256")
	public String x5tS256;

}

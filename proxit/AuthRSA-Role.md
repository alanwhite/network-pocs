# Using a KeyCloak Identity and Role to Authorise Proxy Access

Proxit only accepts proxy requests where the password token in http basic auth is a valid jwt
signed by a trusted auth server.

This example uses keycloak as the Auth Server and FusionAuth's
Java JWT library. 

This is an example of the Resource Owners Password Credentials Flow (RFC 6749), where Keycloak acts as the Authorization Server for proxit, which is the Resource Server. The requesting client application, that requests the users credentials and uses them to obtain the Access Token is represented by curl.

The resource that proxit provides is the Proxied-URLs. It represents all URLs that can be proxied to. There is an authorization policy that is common to all Proxied-URLs.

We have a policy that says access to the Proxied-URL group of resources requires the requesting identity to have a role of 'connector-role'.

Proxit must be defined as a confidential client in OAuth terms to be defined in Keycloak as a Resource Server.

The Scope for the Proxied-URLs resource that matters is the 'connect' scope. 

For permissions, the role 'connector-role' can perform the action 'connect' on the protected resource Proxied-URLs. 

## Set up a Root CA

Generate key file and self-sign a cert with it

```
$ openssl genrsa 2048 > ca-key.pem
$ openssl req -new -x509 -nodes -days 365000 -key ca-key.pem -out ca-cert.pem
```

This results in a file called ca.key that contains the EC private key and parameters.

```
$ openssl req -x509 -new -SHA384 -nodes -key ca.key -days 3650 -out ca.crt
You are about to be asked to enter information that will be incorporated
into your certificate request.
What you are about to enter is what is called a Distinguished Name or a DN.
There are quite a few fields but you can leave some blank
For some fields there will be a default value,
If you enter '.', the field will be left blank.
-----
Country Name (2 letter code) []:GB
State or Province Name (full name) []:Scotland
Locality Name (eg, city) []:Glasgow
Organization Name (eg, company) []:Awesome
Organizational Unit Name (eg, section) []:Tech
Common Name (eg, fully qualified host name) []:theroot.arwhite.xyz
Email Address []:alan@arwhite.xyz
```

Set up an intermediate CA - eventually

Set up Identity for Auth Server

```
$ openssl req -newkey rsa:2048 -nodes -days 365000 -keyout server-key.pem -out server-req.pem

You are about to be asked to enter information that will be incorporated
into your certificate request.
What you are about to enter is what is called a Distinguished Name or a DN.
There are quite a few fields but you can leave some blank
For some fields there will be a default value,
If you enter '.', the field will be left blank.
-----
Country Name (2 letter code) []:GB
State or Province Name (full name) []:Scotland
Locality Name (eg, city) []:Glasgow
Organization Name (eg, company) []:Awesome
Organizational Unit Name (eg, section) []:Tech
Common Name (eg, fully qualified host name) []:auth.arwhite.xyz
Email Address []:auth@arwhite.xyz

Please enter the following 'extra' attributes
to be sent with your certificate request
A challenge password []:challenge
```

Sign the CSR

```
$ openssl x509 -req -days 365000 -set_serial 01 -in server-req.pem -out server-cert.pem -CA ca-cert.pem -CAkey ca-key.pem
Signature ok
subject=/C=GB/ST=Scotland/L=Glasgow/O=Awesome/OU=Tech/CN=auth.arwhite.xyz/emailAddress=auth@arwhite.xyz
Getting CA Private Key
```
This produces server-cert.pem containing the signed server certificate.


## Set up the Auth Server 

Create the PEM files needed for Keycloak

```
$ cp server-cert.pem auth.arwhite.xyz.pem
$ cat ca-cert.pem >> auth.arwhite.xyz.pem
$ cp server-key.pem auth.arwhite.xyz.key.pem
```
The first file, auth.arwhite.xyz.pem contains the certificate that the server will present during the TLS exchange including the cert chain to the signing CA. The second file, auth.arwhite.xyz.key.pem contains the private key the server will need to be able to prove it owns the certificate it is presenting during TLS. 

Download & unpack Keycloak

```
$ curl -LJO https://github.com/keycloak/keycloak/releases/download/18.0.0/keycloak-18.0.0.tar.gz
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
  0     0    0     0    0     0      0      0 --:--:-- --:--:-- --:--:--     0
100  164M  100  164M    0     0  20.6M      0  0:00:07  0:00:07 --:--:-- 25.7M
curl: Saved to filename 'keycloak-18.0.0.tar.gz'

$ tar -zxvf keycloak-18.0.0.tar.gz
```
Edit /etc/hosts to alias 127.0.0.1 localhost to the name in the server-cert above, auth.arwhite.xyz

For our command line login to work, we need to tell it to trust the cert presented by keycloak, namely the server-cert above. We do this by adding the CA's cert to a truststore and telling kcadm to use that trust store

```
TS_PASSWORD=fredbl
keytool -keystore cacerts -importcert -file ca-cert.pem -storepass fredbl
```

Start keycloak creating initial admin user in the main realm

```
$ cd keycloak-18.0.0
$ export KEYCLOAK_ADMIN=megaboss
$ export KEYCLOAK_ADMIN_PASSWORD=maniacal
$ bin/kc.sh start-dev --https-certificate-file=../auth.arwhite.xyz.pem --https-certificate-key-file=../auth.arwhite.xyz.key.pem &
Updating the configuration and installing your custom providers, if any. Please wait.
2022-06-28 10:45:26,467 INFO  [io.quarkus.deployment.QuarkusAugmentor] (main) Quarkus augmentation completed in 7052ms
2022-06-28 10:45:30,442 INFO  [org.keycloak.quarkus.runtime.hostname.DefaultHostnameProvider] (main) Hostname settings: FrontEnd: <request>, Strict HTTPS: false, Path: <request>, Strict BackChannel: false, Admin: <request>, Port: -1, Proxied: false
2022-06-28 10:45:31,412 WARN  [org.infinispan.PERSISTENCE] (keycloak-cache-init) ISPN000554: jboss-marshalling is deprecated and planned for removal
2022-06-28 10:45:31,645 WARN  [org.infinispan.CONFIG] (keycloak-cache-init) ISPN000569: Unable to persist Infinispan internal caches as no global state enabled
2022-06-28 10:45:31,717 INFO  [org.infinispan.CONTAINER] (keycloak-cache-init) ISPN000556: Starting user marshaller 'org.infinispan.jboss.marshalling.core.JBossUserMarshaller'
2022-06-28 10:45:32,212 INFO  [org.infinispan.CONTAINER] (keycloak-cache-init) ISPN000128: Infinispan version: Infinispan 'Triskaidekaphobia' 13.0.8.Final
2022-06-28 10:45:34,057 INFO  [org.keycloak.quarkus.runtime.storage.database.liquibase.QuarkusJpaUpdaterProvider] (main) Initializing database schema. Using changelog META-INF/jpa-changelog-master.xml
2022-06-28 10:45:37,086 INFO  [org.keycloak.connections.infinispan.DefaultInfinispanConnectionProviderFactory] (main) Node name: node_446728, Site name: null
2022-06-28 10:45:37,203 INFO  [org.keycloak.services] (main) KC-SERVICES0050: Initializing master realm
2022-06-28 10:45:40,312 INFO  [org.keycloak.services] (main) KC-SERVICES0009: Added user 'megaboss' to realm 'master'
2022-06-28 10:45:40,713 INFO  [io.quarkus] (main) Keycloak 18.0.0 on JVM (powered by Quarkus 2.7.5.Final) started in 13.924s. Listening on: http://0.0.0.0:8080 and https://0.0.0.0:8443
2022-06-28 10:45:40,714 INFO  [io.quarkus] (main) Profile dev activated.
2022-06-28 10:45:40,714 INFO  [io.quarkus] (main) Installed features: [agroal, cdi, hibernate-orm, jdbc-h2, jdbc-mariadb, jdbc-mssql, jdbc-mysql, jdbc-oracle, jdbc-postgresql, keycloak, narayana-jta, reactive-routes, resteasy, resteasy-jackson, smallrye-context-propagation, smallrye-health, smallrye-metrics, vault, vertx]
2022-06-28 10:45:40,720 WARN  [org.keycloak.quarkus.runtime.KeycloakMain] (main) Running the server in development mode. DO NOT use this configuration in production.
```

Create realm for our users to exist in, and a regular user, and a user that is permissioned to connect

```
$ bin/kcadm.sh config truststore --trustpass $TS_PASSWORD ../cacerts
$ bin/kcadm.sh config credentials --server https://auth.arwhite.xyz:8443 --realm master --user $KEYCLOAK_ADMIN --password $KEYCLOAK_ADMIN_PASSWORD
Logging into https://auth.arwhite.xyz:8443/ as user megaboss of realm master
$ bin/kcadm.sh create realms -s realm=proxit-realm -s enabled=true
Created new realm with id 'proxit-realm'
$ bin/kcadm.sh create users -r proxit-realm -s username=regularjoe -s enabled=true
Created new user with id 'ab1e1e6f-d4db-4029-9e5b-66042f387c86'
$ bin/kcadm.sh set-password -r proxit-realm --username regularjoe --new-password bloggs
$ bin/kcadm.sh create users -r proxit-realm -s username=connectorjoe -s enabled=true
Created new user with id '4d738994-18ba-44f0-b727-e01bfe40a3a8'
$ bin/kcadm.sh set-password -r proxit-realm --username connectorjoe --new-password letaxi
$ CONNECTUSER=`bin/kcadm.sh get users -r proxit-realm --fields id --format csv --noquotes -q username=connecterjoe`
```

Create a client app to represent the proxit proxy, and a shared secret for it to use (like most of this don't do it this way in production). In OAuth terms proxit is a Resource Server, and the keycloak client represents it. 


```
$ bin/kcadm.sh create clients -r proxit-realm -s clientId=proxit-proxy -s enabled=true -s clientAuthenticatorType=client-secret -s secret=proxit-shhh -s directAccessGrantsEnabled=true -s authorizationServicesEnabled=true
Created new client with id '8b8d2095-b496-42c3-8de3-0e63a3967765'

$ CLIENTID=`bin/kcadm.sh get clients -r proxit-realm --fields id --format csv --noquotes -q clientId=proxit-proxy`
```

Create a client role that allows connections

```
$ bin/kcadm.sh create clients/$CLIENTID/roles -r proxit-realm -s name=connector-role -s 'description=Connector can use the proxy to connect'
Created new role with id 'connector-role'
```
Create a group that users must be in to connect via the proxit proxy

```
$ bin/kcadm.sh create groups -r proxit-realm -s name=connector-group
Created new group with id '751b12fe-67c3-44d9-a608-1edd4f0817f1'

GROUPID=`bin/kcadm.sh get groups -r proxit-realm --fields id --format csv --noquotes -q groupId=connector-group`
```

Associate the client role that allows connections with the group

```
$ bin/kcadm.sh add-roles -r proxit-realm --gname connector-group --cclientid proxit-proxy --rolename connector-role
```

Associate the user with the group

```
$ bin/kcadm.sh update users/$CONNECTUSER/groups/$GROUPID -r proxit-realm -s realm=proxit-realm -s userId=$CONNECTUSER -s groupId=$GROUPID -n
```

Proxit requires an environment variable PROXIT_AUTH_SERVER set to the URI of the Auth Server, in our case https://auth.arwhite.xyz:8443/realms/proxit-realm

```
$ export PROXIT_AUTH_SERVER=https://auth.arwhite.xyz:8443/realms/proxit-realm
$ (run proxit whatever way)
```

Proxit will retrieve the config metadata from the keycloak realm, then using the jwks_uri returned build
a local cache of the keys required to validate JWTs issued by keycloa for this realm. A timer is set up to refresh the cache every 6 hours. Future versions may update on cache miss as a backstop in case keycloak admins rotate keys and start using them within 6 hours. 



Get curl to make proxy request with identity as a proxy header. 

### Useful Commands
Get public keys used by keycloak realm

```
$ curl https://auth.arwhite.xyz:8443/realms/proxit-realm/.well-known/openid-configuration --cacert ../ca-cert.pem
{"issuer":"https://auth.arwhite.xyz:8443/realms/proxit-realm","authorization_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/auth","token_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/token","introspection_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/token/introspect","userinfo_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/userinfo","end_session_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/logout","frontchannel_logout_session_supported":true,"frontchannel_logout_supported":true,"jwks_uri":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/certs","check_session_iframe":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/login-status-iframe.html","grant_types_supported":["authorization_code","implicit","refresh_token","password","client_credentials","urn:ietf:params:oauth:grant-type:device_code","urn:openid:params:grant-type:ciba"],"acr_values_supported":["0","1"],"response_types_supported":["code","none","id_token","token","id_token token","code id_token","code token","code id_token token"],"subject_types_supported":["public","pairwise"],"id_token_signing_alg_values_supported":["PS384","ES384","RS384","HS256","HS512","ES256","RS256","HS384","ES512","PS256","PS512","RS512"],"id_token_encryption_alg_values_supported":["RSA-OAEP","RSA-OAEP-256","RSA1_5"],"id_token_encryption_enc_values_supported":["A256GCM","A192GCM","A128GCM","A128CBC-HS256","A192CBC-HS384","A256CBC-HS512"],"userinfo_signing_alg_values_supported":["PS384","ES384","RS384","HS256","HS512","ES256","RS256","HS384","ES512","PS256","PS512","RS512","none"],"userinfo_encryption_alg_values_supported":["RSA-OAEP","RSA-OAEP-256","RSA1_5"],"userinfo_encryption_enc_values_supported":["A256GCM","A192GCM","A128GCM","A128CBC-HS256","A192CBC-HS384","A256CBC-HS512"],"request_object_signing_alg_values_supported":["PS384","ES384","RS384","HS256","HS512","ES256","RS256","HS384","ES512","PS256","PS512","RS512","none"],"request_object_encryption_alg_values_supported":["RSA-OAEP","RSA-OAEP-256","RSA1_5"],"request_object_encryption_enc_values_supported":["A256GCM","A192GCM","A128GCM","A128CBC-HS256","A192CBC-HS384","A256CBC-HS512"],"response_modes_supported":["query","fragment","form_post","query.jwt","fragment.jwt","form_post.jwt","jwt"],"registration_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/clients-registrations/openid-connect","token_endpoint_auth_methods_supported":["private_key_jwt","client_secret_basic","client_secret_post","tls_client_auth","client_secret_jwt"],"token_endpoint_auth_signing_alg_values_supported":["PS384","ES384","RS384","HS256","HS512","ES256","RS256","HS384","ES512","PS256","PS512","RS512"],"introspection_endpoint_auth_methods_supported":["private_key_jwt","client_secret_basic","client_secret_post","tls_client_auth","client_secret_jwt"],"introspection_endpoint_auth_signing_alg_values_supported":["PS384","ES384","RS384","HS256","HS512","ES256","RS256","HS384","ES512","PS256","PS512","RS512"],"authorization_signing_alg_values_supported":["PS384","ES384","RS384","HS256","HS512","ES256","RS256","HS384","ES512","PS256","PS512","RS512"],"authorization_encryption_alg_values_supported":["RSA-OAEP","RSA-OAEP-256","RSA1_5"],"authorization_encryption_enc_values_supported":["A256GCM","A192GCM","A128GCM","A128CBC-HS256","A192CBC-HS384","A256CBC-HS512"],"claims_supported":["aud","sub","iss","auth_time","name","given_name","family_name","preferred_username","email","acr"],"claim_types_supported":["normal"],"claims_parameter_supported":true,"scopes_supported":["openid","roles","address","phone","acr","email","offline_access","web-origins","microprofile-jwt","profile"],"request_parameter_supported":true,"request_uri_parameter_supported":true,"require_request_uri_registration":true,"code_challenge_methods_supported":["plain","S256"],"tls_client_certificate_bound_access_tokens":true,"revocation_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/revoke","revocation_endpoint_auth_methods_supported":["private_key_jwt","client_secret_basic","client_secret_post","tls_client_auth","client_secret_jwt"],"revocation_endpoint_auth_signing_alg_values_supported":["PS384","ES384","RS384","HS256","HS512","ES256","RS256","HS384","ES512","PS256","PS512","RS512"],"backchannel_logout_supported":true,"backchannel_logout_session_supported":true,"device_authorization_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/auth/device","backchannel_token_delivery_modes_supported":["poll","ping"],"backchannel_authentication_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/ext/ciba/auth","backchannel_authentication_request_signing_alg_values_supported":["PS384","ES384","RS384","ES256","RS256","ES512","PS256","PS512","RS512"],"require_pushed_authorization_requests":false,"pushed_authorization_request_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/ext/par/request","mtls_endpoint_aliases":{"token_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/token","revocation_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/revoke","introspection_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/token/introspect","device_authorization_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/auth/device","registration_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/clients-registrations/openid-connect","userinfo_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/userinfo","pushed_authorization_request_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/ext/par/request","backchannel_authentication_endpoint":"https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/ext/ciba/auth"}}%
```


Login as non-connect authorised user

```
$ curl -L -X POST 'https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/token' \
--cacert ../ca-cert.pem \
-H 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'client_id=proxit-proxy' \
--data-urlencode 'grant_type=password' \
--data-urlencode 'client_secret=proxit-shhh' \
--data-urlencode 'scope=openid' \
--data-urlencode 'username=regularjoe' \
--data-urlencode 'password=bloggs'

```

Copying the access token to https://jwt.io to decode it gets:

```
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "Hd-Ir-ct5quQJpnzaTOqwHzPbJrAMzR4aMDyLveH_FM"
}
{
  "exp": 1656411882,
  "iat": 1656411582,
  "jti": "30606a1e-c11a-42cb-ad97-276e3051895d",
  "iss": "https://auth.arwhite.xyz:8443/realms/proxit-realm",
  "aud": "account",
  "sub": "edffbca4-b1ce-4197-a0bd-b8ff64d5cab0",
  "typ": "Bearer",
  "azp": "proxit-proxy",
  "session_state": "140b38d0-b9aa-492c-9b1e-57f41cfe70f7",
  "acr": "1",
  "realm_access": {
    "roles": [
      "default-roles-proxit-realm",
      "offline_access",
      "uma_authorization"
    ]
  },
  "resource_access": {
    "account": {
      "roles": [
        "manage-account",
        "manage-account-links",
        "view-profile"
      ]
    }
  },
  "scope": "openid email profile",
  "sid": "140b38d0-b9aa-492c-9b1e-57f41cfe70f7",
  "email_verified": false,
  "preferred_username": "regularjoe"
}
```

Login as user with connect role

```
$ curl -L -X POST 'https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/token' \
--cacert ../ca-cert.pem \
-H 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'client_id=proxit-proxy' \
--data-urlencode 'grant_type=password' \
--data-urlencode 'client_secret=proxit-shhh' \
--data-urlencode 'scope=openid' \
--data-urlencode 'username=connectorjoe' \
--data-urlencode 'password=letaxi'
{"access_token":"eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJIZC1Jci1jdDVxdVFKcG56YVRPcXdIelBiSnJBTXpSNGFNRHlMdmVIX0ZNIn0.eyJleHAiOjE2NTY0MTEwNTksImlhdCI6MTY1NjQxMDc1OSwianRpIjoiMjE1ZTgzNmMtOTlmMC00NGZhLWI1YWYtYTFkNjY5MGY1MDc2IiwiaXNzIjoiaHR0cHM6Ly9hdXRoLmFyd2hpdGUueHl6Ojg0NDMvcmVhbG1zL3Byb3hpdC1yZWFsbSIsImF1ZCI6ImFjY291bnQiLCJzdWIiOiIxNmM1OTY3My1jNWQ1LTRhYzUtYWQ1NS0xMTRiMzk3MWJjYWYiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJwcm94aXQtcHJveHkiLCJzZXNzaW9uX3N0YXRlIjoiNjIwY2U4MzItYTIyYS00NjZjLTlkZTMtMDY0MDY1MTIyMmM1IiwiYWNyIjoiMSIsInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJkZWZhdWx0LXJvbGVzLXByb3hpdC1yZWFsbSIsIm9mZmxpbmVfYWNjZXNzIiwidW1hX2F1dGhvcml6YXRpb24iXX0sInJlc291cmNlX2FjY2VzcyI6eyJwcm94aXQtcHJveHkiOnsicm9sZXMiOlsiY29ubmVjdG9yLXJvbGUiXX0sImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoib3BlbmlkIGVtYWlsIHByb2ZpbGUiLCJzaWQiOiI2MjBjZTgzMi1hMjJhLTQ2NmMtOWRlMy0wNjQwNjUxMjIyYzUiLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsInByZWZlcnJlZF91c2VybmFtZSI6ImNvbm5lY3RvcmpvZSJ9.YuC5rURReUxWQLV_JbtmLKsfLl71UTj7EFTkKBufq94OuLNPafxAzPvvK-jjfYCGyZd05k5gUZ0MzDB1V1_PZ8sN7VpE0EnggfyiKEqdpcAslp1_EeSkVYX47NOo3jqMJhLUTa22ZWm39LZGkFNFpuiZPu96HZsoLVSD-zO3aurTHpH1PAdwAPtYNwIfaDIB9oCMJcTfeFRfan-zPQk3CRwzcOy2cbJvqDunqihja4q4qU2zKeSz4G5lSY6-z3ZfAyiY3YpTfCplrdOayzgwS0oa61DWdY32ItFLtXmgXSZNlNRLjtMzj56MvBN9f9AHhOP5OKybFDvePpFbKJpcHw","expires_in":300,"refresh_expires_in":1800,"refresh_token":"eyJhbGciOiJIUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJiZGViY2IwMy03ODFjLTRlMjUtYmRjNC00MDQzOWNiMzQ3ZjcifQ.eyJleHAiOjE2NTY0MTI1NTksImlhdCI6MTY1NjQxMDc1OSwianRpIjoiMjY3MzY5YTEtNzVkMC00MWY2LThmMDEtMDM3YTYwZjJmZTI0IiwiaXNzIjoiaHR0cHM6Ly9hdXRoLmFyd2hpdGUueHl6Ojg0NDMvcmVhbG1zL3Byb3hpdC1yZWFsbSIsImF1ZCI6Imh0dHBzOi8vYXV0aC5hcndoaXRlLnh5ejo4NDQzL3JlYWxtcy9wcm94aXQtcmVhbG0iLCJzdWIiOiIxNmM1OTY3My1jNWQ1LTRhYzUtYWQ1NS0xMTRiMzk3MWJjYWYiLCJ0eXAiOiJSZWZyZXNoIiwiYXpwIjoicHJveGl0LXByb3h5Iiwic2Vzc2lvbl9zdGF0ZSI6IjYyMGNlODMyLWEyMmEtNDY2Yy05ZGUzLTA2NDA2NTEyMjJjNSIsInNjb3BlIjoib3BlbmlkIGVtYWlsIHByb2ZpbGUiLCJzaWQiOiI2MjBjZTgzMi1hMjJhLTQ2NmMtOWRlMy0wNjQwNjUxMjIyYzUifQ.w6z6QDRxGytqrxNnOD1NQvrCh98wiQWP8rYDE-iel74","token_type":"Bearer","id_token":"eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJIZC1Jci1jdDVxdVFKcG56YVRPcXdIelBiSnJBTXpSNGFNRHlMdmVIX0ZNIn0.eyJleHAiOjE2NTY0MTEwNTksImlhdCI6MTY1NjQxMDc1OSwiYXV0aF90aW1lIjowLCJqdGkiOiJhNzQwY2UxMi0yM2JjLTRiOTgtODc3MC1iMGUzOGQ3NDA1NmMiLCJpc3MiOiJodHRwczovL2F1dGguYXJ3aGl0ZS54eXo6ODQ0My9yZWFsbXMvcHJveGl0LXJlYWxtIiwiYXVkIjoicHJveGl0LXByb3h5Iiwic3ViIjoiMTZjNTk2NzMtYzVkNS00YWM1LWFkNTUtMTE0YjM5NzFiY2FmIiwidHlwIjoiSUQiLCJhenAiOiJwcm94aXQtcHJveHkiLCJzZXNzaW9uX3N0YXRlIjoiNjIwY2U4MzItYTIyYS00NjZjLTlkZTMtMDY0MDY1MTIyMmM1IiwiYXRfaGFzaCI6InZuNEJuNDdXQW0wMGoyMWtwVjF2c3ciLCJhY3IiOiIxIiwic2lkIjoiNjIwY2U4MzItYTIyYS00NjZjLTlkZTMtMDY0MDY1MTIyMmM1IiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJjb25uZWN0b3Jqb2UifQ.ATBMILNLKbrX22cs7jWUSLDZZdXuBNEgGUDptBIZsfQhKbmOYR1TEeAyEnhxQnw8TKPFE86GPSpjtzQ5sCT6ViYpKGHWGr2_bWlhHECwmieTY46fd1jyzuF2TTdVHpaYbBzniK_VKge7XtLdci9aP91i2w2lAcnHNPDGGm02KZDAPapT1bivXJsL2tqIi9BGJreydgvOw3GJl6U840Ke-kkH-qdJQnDsJVUdcHzkTGPaRkZFPgNyvqD-CK1RwuMHw5BSG-mmJDmbyoP2wN9ksvfCRjPOaKW-Yt14u0Btdic0czebmiInk6z8kjFSsn5bB0DEAr4Z4Q9Ld-J9aY381w","not-before-policy":0,"session_state":"620ce832-a22a-466c-9de3-0640651222c5","scope":"openid email profile"}%
```

Copying the access token to https://jwt.io to decode it gets:

```
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "Hd-Ir-ct5quQJpnzaTOqwHzPbJrAMzR4aMDyLveH_FM"
}
{
  "exp": 1656411059,
  "iat": 1656410759,
  "jti": "215e836c-99f0-44fa-b5af-a1d6690f5076",
  "iss": "https://auth.arwhite.xyz:8443/realms/proxit-realm",
  "aud": "account",
  "sub": "16c59673-c5d5-4ac5-ad55-114b3971bcaf",
  "typ": "Bearer",
  "azp": "proxit-proxy",
  "session_state": "620ce832-a22a-466c-9de3-0640651222c5",
  "acr": "1",
  "realm_access": {
    "roles": [
      "default-roles-proxit-realm",
      "offline_access",
      "uma_authorization"
    ]
  },
  "resource_access": {
    "proxit-proxy": {
      "roles": [
        "connector-role"
      ]
    },
    "account": {
      "roles": [
        "manage-account",
        "manage-account-links",
        "view-profile"
      ]
    }
  },
  "scope": "openid email profile",
  "sid": "620ce832-a22a-466c-9de3-0640651222c5",
  "email_verified": false,
  "preferred_username": "connectorjoe"
}
```
Token content shows the connectorjoe users access token containing the resource_access statement that includes the connector-role.

Useful test website for proxies

```
$ curl https://httpbin.org/ip
{
  "origin": "78.157.216.4"
}
```

Using an explicit proxy

```
$ curl -s -x localhost:2580 https://httpbin.org/ip
{
  "origin": "78.157.216.4"
}
```

In order to pass the token to the proxy, we use the http basic auth syntax

```
$ export TOK=ejy...
$ curl -x user:$TOK@localhost:2580 https://httpbin.org/ip
```

Note: ensure you have a relatively recent version of curl, prior versions limited password 
length to 255 characters.

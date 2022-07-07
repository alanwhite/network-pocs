
# Using a KeyCloak Identity and Granular Resource Permissions to Authorise Proxy Access

Work in progress .....

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
$ bin/kcadm.sh config truststore --trustpass fredbl ../cacerts
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

Create a confidential client in keycloak to represent the proxit proxy, and a shared secret for it to use. In OAuth terms proxit is a Resource Server, and the keycloak client represents it. 


```
$ bin/kcadm.sh create clients -r proxit-realm -s clientId=proxit-proxy -s enabled=true -s clientAuthenticatorType=client-secret -s secret=proxit-shhh -s directAccessGrantsEnabled=true
Created new client with id '8b8d2095-b496-42c3-8de3-0e63a3967765'

$ CLIENTID=`bin/kcadm.sh get clients -r proxit-realm --fields id --format csv --noquotes -q clientId=proxit-proxy`
```

Create the Resource that is served by the Resource Server, in our case it's URLs that are served.
Should we create this so specifically that only URLs specified can be connected to? Probably! 
Something like /connect/* is no good as that means we apply permissions etc to everything below that.
Needs to be something like httpbin.org/ip, somehow specifying only with TLS, and only that URI, not anything below it


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

Update the proxy itself to use this security model
- what is the public key that keycloak uses to sign JWTs?

Proxy checks for variable with Auth Server public key and if present enforces Auth on proxy request by validating proxy headers for token

Get curl to make proxy request with identity as a proxy header. 

Get public keys
curl -L -X GET 'https://auth.arwhite.xyz:8443/realms/proxit-realm/protocol/openid-connect/certs' --cacert ../ca-cert.pem


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

Need to add auth jwt as proxy header, or (ab)use the basic auth syntax on the authority
# Using a KeyCloak Identity and Role to Authorise Proxy Access

This fails because KeyCloak doesn't support the use of EC based TLS certs at 18.0.0

## Set up a Root CA

Props to https://www.erianna.com/ecdsa-certificate-authorities-and-certificates-with-openssl/

Generate key file and self-sign a cert with it

```
$ openssl ecparam -genkey -name secp384r1 -out ca.key
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
$ openssl ecparam -genkey -name secp384r1 -out server.key
```

This results in a file called server.key containing the EC private key and parameters

```
$ openssl req -new -SHA384 -key server.key -nodes -out server.csr
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
$ openssl x509 -req -SHA384 -days 365 -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt
Signature ok
subject=/C=GB/ST=Scotland/L=Glasgow/O=Awesome/OU=Tech/CN=auth.arwhite.xyz/emailAddress=auth@arwhite.xyz
Getting CA Private Key
```
This produces two files: ca.srl, containing the serial number of the signed certificate; and server.crt containing the actual server certificate.


## Set up the Auth Server 

Create the PEM files needed for Keycloak

```
$ cp server.crt auth.arwhite.xyz.pem
$ cat ca.crt >> auth.arwhite.xyz.pem
$ cp server.key auth.arwhite.xyz.key.pem
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

Start keycloak creating initial admin user in the main realm

```
$ cd keycloak-18.0.0
$ export KEYCLOAK_ADMIN=megaboss
$ export KEYCLOAK_ADMIN_PASSWORD=maniacal
$ bin/kc.sh start-dev --https-certificate-file=../auth.arwhite.xyz.pem --https-certificate-key-file=../auth.arwhite.xyz.key.pem
```

Create realm for our users to exist in, and a regular user, and a user that is permissioned to connect

```
$ bin/kcadm.sh config credentials --server http://localhost:8180/auth --realm master --user megaboss --password maniacal
$ bin/kcadm.sh create realms -s realm=proxit-realm -s enabled=true -o
$ bin/kcadm.sh create users -r proxit-realm -s username=regularjoe -s enabled=true
$ bin/kcadm.sh set-password -r proxit-realm --username regularjoe --new-password bloggs
$ bin/kcadm.sh create users -r proxit-realm -s username=connectorjoe -s enabled=true
$ bin/kcadm.sh set-password -r proxit-realm --username connectorjoe --new-password letaxi
```

Create a client app to represent the proxit proxy, and a shared secret for it to use (like most of this don't do it this way in production)
$ kcadm.sh create clients -r proxit-realm -s clientId=proxit-proxy -s enabled=true
$ kcadm.sh create clients -r proxit-realm -s clientId=proxit-proxy -s enabled=true -s clientAuthenticatorType=client-secret -s secret=d0b8122f-8dfb-46b7-b68a-f5cc4e25d000

Create a client role that allows connections
$ kcadm.sh get clients -r proxit-realm --fields id,clientId
$ kcadm.sh create clients/a95b6af3-0bdc-4878-ae2e-6d61a4eca9a0/roles -r proxit-realm -s name=connector-role -s 'description=Connector can use the proxy to connect'

Create a group that users must be in to connect via the proxit proxy
$ kcadm.sh create groups -r proxit-realm -s name=connector-group

Associate the client role that allows connections with the group
$ kcadm.sh add-roles -r proxit-realm --gname connector-group --cclientid proxit-proxy --rolename connector-role

Associate the user with the group
kcadm.sh update users/b544f379-5fc4-49e5-8a8d-5cfb71f46f53/groups/ce01117a-7426-4670-a29a-5c118056fe20 -r proxit-realm -s realm=proxit-realm -s userId=b544f379-5fc4-49e5-8a8d-5cfb71f46f53 -s groupId=ce01117a-7426-4670-a29a-5c118056fe20 -n


Update the proxy itself to use this security model
Proxy checks for variable with Auth Server public key and if present enforced Auth
On proxy request validate headers for token

Get curl to make proxy request with identity. 
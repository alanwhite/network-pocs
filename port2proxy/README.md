# port2proxy

Simple port relay, listens on a localport, relays all IO to remote host:port via an http proxy

port2proxy port=<local port> proxy=<proxy address:port> OPTIONS <target address:port>

Implemented OPTIONS 
	proxyuser=<name> proxypass=<password>
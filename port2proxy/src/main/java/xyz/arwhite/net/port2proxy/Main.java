package xyz.arwhite.net.port2proxy;

import java.util.Objects;

/**
 * Basic port forward relay over an HTTP proxy
 * 
 * @author Alan R. White
 *
 */
public class Main {

	public static class Builder {
		int localPort = 0;
		String proxy;
		String proxyUser = "";
		String proxyPass = "";
		String remoteHost;

		public PortListener build() {
			Objects.nonNull(proxy);
			Objects.nonNull(remoteHost);

			if ( localPort < 1024 )
				throw new IllegalArgumentException("Local TCP port must be greater than 1024");

			return new PortListener(this);
		}
	}

	private static void usage() {
		System.out.println("port2proxy port=<local port> proxy=<proxy address:port> " +
				"OPTIONS <target address:port>\n");
		System.out.println("OPTIONS proxyuser=<name> proxypass=<password>");
	}

	public static void main(String[] args) {
		var config = new Main.Builder();

		for ( String arg : args ) {
			var words = arg.split("=");
			if ( words.length < 1 || words.length > 2 ) {
				usage();
				throw new IllegalArgumentException(arg);
			}  

			if ( words.length == 1 ) {
				config.remoteHost = arg;
				continue;
			}

			switch( words[0] ) {
			case "port" -> config.localPort = Integer.parseInt(words[1]);
			case "proxy" -> config.proxy = words[1];
			case "proxyuser" -> config.proxyUser = words[1];
			case "proxypass" -> config.proxyPass = words[1];
			default -> { usage(); throw new IllegalArgumentException(words[0]); }
			}
		}
		
		config.build().execute();
	}

}

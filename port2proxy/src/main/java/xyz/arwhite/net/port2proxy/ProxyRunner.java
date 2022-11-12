package xyz.arwhite.net.port2proxy;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

public class ProxyRunner implements Runnable {

	Socket conn;
	String proxy, proxyUser, proxyPass, remoteHost;

	public ProxyRunner(Socket conn, String proxy, String proxyUser, String proxyPass, String remoteHost) {
		this.conn = conn;
		this.proxy = proxy;
		this.proxyUser = proxyUser;
		this.proxyPass = proxyPass;
		this.remoteHost = remoteHost;
	}

	@Override
	public void run() {

		try {
			// because some bright spark at Oracle thinks nobody should use basic auth for anything ever
			System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
			Authenticator.setDefault( new Authenticator() {
				public PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(proxyUser, proxyPass.toCharArray());
				}
			});

			var proxyUri = new URI(null,proxy,null,null,null);
			SocketAddress proxyAddr = new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort());
			Proxy proxy = new Proxy(Proxy.Type.HTTP, proxyAddr);

			try (var remoteSocket = new Socket(proxy)) {
				var uri = new URI(null,remoteHost,null,null,null);

				remoteSocket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()));
				remoteSocket.setSoTimeout(60000);

				// relay stuff
			}			

		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}

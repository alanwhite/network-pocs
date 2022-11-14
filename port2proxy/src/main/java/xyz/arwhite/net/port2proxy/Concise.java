package xyz.arwhite.net.port2proxy;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/**
 * Exists only to have a concise app to submit a bug for
 * Never do anything you see written in this class
 * @author Alan R. White
 *
 */
public class Concise {

	int localPort = 0;
	String proxy;
	String proxyUser = "";
	String proxyPass = "";
	String remoteHost;

	public void execute() {
		try (ServerSocket server = new ServerSocket(localPort)) {	
			while(true) {
				Socket localSocket = server.accept();
				Thread.startVirtualThread(() -> {
					try {
						System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
						// Never do this for real!!
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

							Runnable a = () -> {
								try { localSocket.getInputStream().transferTo(remoteSocket.getOutputStream()); } 
								catch (IOException e) {}
							};

							Runnable b = () -> {
								try { remoteSocket.getInputStream().transferTo(localSocket.getOutputStream()); } 
								catch (IOException e) {}
							};

							List<Callable<Object>> tasks = Arrays.asList(Executors.callable(a), Executors.callable(b));
							Executors.newVirtualThreadPerTaskExecutor().invokeAll(tasks); 
							localSocket.close(); 
						}			

					} catch (Exception e) {
						e.printStackTrace();
					} 
				});
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		var app = new Concise();

		for ( String arg : args ) {
			var words = arg.split("=");
			if ( words.length < 1 || words.length > 2 ) {
				throw new IllegalArgumentException(arg);
			}  

			if ( words.length == 1 ) {
				app.remoteHost = arg;
				continue;
			}

			switch( words[0] ) {
			case "port" -> app.localPort = Integer.parseInt(words[1]);
			case "proxy" -> app.proxy = words[1];
			case "proxyuser" -> app.proxyUser = words[1];
			case "proxypass" -> app.proxyPass = words[1];
			default -> throw new IllegalArgumentException(words[0]); 
			}
		}

		Objects.nonNull(app.proxy);
		Objects.nonNull(app.remoteHost);
		app.execute();
	}
}

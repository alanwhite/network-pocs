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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public class ProxyRunner implements Runnable {

	Socket localSocket;
	String proxy, proxyUser, proxyPass, remoteHost;

	public ProxyRunner(Socket localsocket, String proxy, String proxyUser, String proxyPass, String remoteHost) {
		this.localSocket = localsocket;
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
				
				List<Callable<Object>> tasks = new ArrayList<>(2);
				tasks.add(Executors.callable(new IORelay(localSocket,remoteSocket)));
				tasks.add(Executors.callable(new IORelay(remoteSocket,localSocket)));
				var exec = Executors.newVirtualThreadPerTaskExecutor();
				
				// not much we can do re any error during relaying 
				try {
					exec.invokeAll(tasks);
				} catch (InterruptedException e1) {}
				
				// ignore any error, this is close if we can, if not, it's probably already closed
				try { 
					localSocket.close();
				} catch (IOException e) {}
				
			}			

		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	private class IORelay implements Runnable {

		Socket in, out;

		public IORelay(Socket in, Socket out) {
			this.in = in;
			this.out = out;
		}

		@Override
		public void run() {
			try {
				var input = in.getInputStream();
				var output = out.getOutputStream();

				input.transferTo(output);

//				byte[] buffer = new byte[4096];
//				int bytesRead = input.read(buffer);
//
//				while ( bytesRead != 0 ) {
//					output.write(buffer, 0, bytesRead);
//
//					// if will block, flush out stream
//					if ( input.available() < 1 )
//						output.flush();
//
//					bytesRead = input.read(buffer);					
//				}

			} catch (IOException e) {
				if ( !e.getMessage().equals("Connection reset") ) 
					e.printStackTrace();
			}
		}

	}

}

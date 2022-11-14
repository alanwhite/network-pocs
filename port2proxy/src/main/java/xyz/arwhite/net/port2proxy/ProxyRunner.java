package xyz.arwhite.net.port2proxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Base64;
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
			var proxyUri = new URI(null,proxy,null,null,null);
			try (var remoteSocket = new Socket(proxyUri.getHost(),proxyUri.getPort())) {

				var uri = new URI(null,remoteHost,null,null,null);

				String connect = "CONNECT " + uri.getHost() + ":" + uri.getPort() + " HTTP/1.1\n";
				remoteSocket.getOutputStream().write(connect.getBytes());

				if ( !proxyUser.isBlank() ) {
					var creds = proxyUser + ":" + proxyPass;
					String auth = "Proxy-Authorization: Basic " 
							+ Base64.getEncoder().encodeToString(creds.getBytes()) + "\n";
					remoteSocket.getOutputStream().write(auth.getBytes());
				}

				remoteSocket.getOutputStream().write("\n".getBytes());

				var reader = new BufferedReader(
						new InputStreamReader(remoteSocket.getInputStream(), "US-ASCII"));
				var httpResponse = reader.readLine();
				var words = httpResponse.split(" ");
				if ( words.length != 3 || !"200".equals(words[1]) ) {
					System.err.println("Unhandled response from proxy "+httpResponse);
					return;
				}
				
				// drain and ignore any response headers
				int len = -1;
				while ( len != 0 ) {
					var line = reader.readLine();
					len = line.length();
				}
				
				// bi-directionally relay data between sockets
				List<Callable<Object>> tasks = new ArrayList<>(2);
				tasks.add(Executors.callable(new IORelay(localSocket,remoteSocket)));
				tasks.add(Executors.callable(new IORelay(remoteSocket,localSocket)));
				var exec = Executors.newVirtualThreadPerTaskExecutor();

				// not much we can do with error during relaying 
				try {
					exec.invokeAll(tasks);
				} catch (InterruptedException e1) {}

				// ignore any error, this is "close if we can", if not, it's probably already closed
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
				
			} catch (IOException e) {
				if ( !e.getMessage().equals("Connection reset") ) 
					e.printStackTrace();
			}
		}

	}

}

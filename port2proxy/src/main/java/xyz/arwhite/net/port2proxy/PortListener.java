package xyz.arwhite.net.port2proxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class PortListener {

	private int localPort;
	private String proxy;
	private String proxyUser = "";
	private String proxyPass = "";
	private String remoteHost;

	public PortListener(Main.Builder builder) {
		localPort = builder.localPort;
		proxy = builder.proxy;
		proxyUser = builder.proxyUser;
		proxyPass = builder.proxyPass;
		remoteHost = builder.remoteHost;
	}
	
	public void execute() {
		System.out.println("port2proxy listening on port "+localPort);
		
		try (ServerSocket server = new ServerSocket(localPort)) {	
			while(true) {
				Socket conn = server.accept();
				Thread.startVirtualThread(new ProxyRunner(conn,proxy,proxyUser,proxyPass,remoteHost));
			}

		} catch (IOException e) {
			e.printStackTrace();
		}


	}

}

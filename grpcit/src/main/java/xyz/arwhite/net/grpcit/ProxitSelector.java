package xyz.arwhite.net.grpcit;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class ProxitSelector extends ProxySelector {

	private ProxySelector defaultproxySelector = ProxySelector.getDefault();
	private List<Proxy> proxit = new ArrayList<>();
	
	public ProxitSelector() {
		proxit.add(new Proxy(Type.HTTP, new InetSocketAddress(
	            "127.0.0.1", 2580)));
//		proxit.add(new Proxy(Type.DIRECT, new InetSocketAddress(
//	            "127.0.0.1", 2580)));
		proxit.add(new Proxy(Type.SOCKS, new InetSocketAddress(
	            "127.0.0.1", 2580)));
	}

	@Override
	public List<Proxy> select(URI uri) {
		return proxit;
	}

	@Override
	public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
		System.out.println("Connect Failed: "+uri+" "+sa.toString()+" "+ioe.getLocalizedMessage());
	}

}

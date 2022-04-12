package xyz.arwhite.net.proxit;

public class Main {
	
	public Main() {
		var serverThread = new Thread(new ProxyServer(10));
		serverThread.start();
		
		System.out.println("going dark");
		try {
			serverThread.join();
		} catch (InterruptedException e) {
			System.out.println("Interrupted");
		}
		System.out.println("Seen the light");
		
	}

	public static void main(String[] args) {
		new Main();
	}

}

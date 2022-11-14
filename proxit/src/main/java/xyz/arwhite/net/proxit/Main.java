package xyz.arwhite.net.proxit;

public class Main {
	
	public Main(boolean vThreads) throws Exception {
		
		Thread serverThread = null;
		
		if ( vThreads ) {
			System.out.println("Using virtual threads");
			serverThread = Thread.startVirtualThread(new ProxyServer(10, vThreads));
		} else {
			System.out.println("Using platform threads");
			serverThread = new Thread(new ProxyServer(10));
			serverThread.start();
		}
		
		try {
			serverThread.join();
		} catch (InterruptedException e) {
			System.out.println("Interrupted");
		}
		
	}

	public static void main(String[] args) throws Exception {
		new Main(args.length > 0 && args[0].equals("-vt"));
	}

}

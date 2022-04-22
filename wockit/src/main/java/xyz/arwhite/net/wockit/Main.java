package xyz.arwhite.net.wockit;

public class Main {

	private String wockURI = "wock";
	private int wockSrvPort = 2581;
	
	public Main() throws Exception {
		
		var fullURI = "http://127.0.0.1:"+wockSrvPort+"//"+wockURI;
		
		var serverThread = new Thread(new WebSocketServer(10,fullURI,new WockitServer()));
		serverThread.start();
		
		// create client object passing a websocket handler
		var client = new WebSocketClient(fullURI, new WockitClient());
		
		System.out.println("All done");
	}

	public static void main(String[] args) throws Exception {
		new Main();
	}
}

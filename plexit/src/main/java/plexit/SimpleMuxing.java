package plexit;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import javax.net.ServerSocketFactory;

public class SimpleMuxing {

	@SuppressWarnings("preview")
	public SimpleMuxing() throws InterruptedException {
	
		var connectionPool = Executors.newVirtualThreadPerTaskExecutor();

		// create a thread which will listen for connection on port 2590
		// each connection accepted will be treated as a multiplexing socket
		// and so will result in a virtual thread being dispatched to 
		// process all IO

		connectionPool.execute(new Runnable() {

			@Override
			public void run() {
				try {
					var serverSocketFactory = ServerSocketFactory.getDefault();
					var server = serverSocketFactory.createServerSocket(2590, 0, InetAddress.getLoopbackAddress());

					System.out.println("SimpleMux: Listening for incoming tunnel connection requests");
					while(true) {
						var socket = server.accept();
						System.out.println("SimpleMux: Connection received and launching tunnel server");	
						connectionPool.execute(new MuxServer(socket));
					}
				} catch(Exception e) {
					e.printStackTrace();
					System.err.println("Listener Terminated");
				}
			}});

		// launch a service that will listen on port 2591 and implement a 
		// simple reflection server used to demonstrate messages are 
		// exchanged in sequence and to the correct client
		
		
		
		
		// create a socket connected to the MuxServer
		
		
		// send multiple parallel requests to the reflection server 
		// via the mux'd connection
		List<Callable<Object>> tasks = new ArrayList<>();
		
		for ( int i = 0; i < 10; i++ )
			tasks.add(Executors.callable(new Requestor(i)));
		
		var status = Executors.newVirtualThreadPerTaskExecutor().invokeAll(tasks);
	}

	private class Requestor implements Runnable {
		private int id;
		
		public Requestor(int id) {
			this.id = id;
		}

		@Override
		public void run() {
			// write HELLO
			// read HELLO THERE
			// write BYE
			// read BYE THEN
			
		}
		
	}
	
	public static void main(String[] args) throws IOException, InterruptedException {
		new SimpleMuxing();
	}
	

}

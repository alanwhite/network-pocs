package plexit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

public class SimpleMuxing {

	@SuppressWarnings("preview")
	public SimpleMuxing() throws InterruptedException, IOException {

		var connectionPool = Executors.newVirtualThreadPerTaskExecutor();

		/*
		 * launch a service that will listen on port 2591 and implement a 
		 * simple reflection server used to demonstrate messages are
		 * exchanged in sequence and to the correct client
		 */

		connectionPool.execute(new Runnable() {

			@Override
			public void run() {
				try {
					var serverSocketFactory = ServerSocketFactory.getDefault();
					var server = serverSocketFactory.createServerSocket(2591, 0, InetAddress.getLoopbackAddress());

					System.out.println("SimpleMux: Reflection service listening for requests");
					while(true) {
						var socket = server.accept();
						System.out.println("SimpleMux: Reflection request received and launching handler");	
						connectionPool.execute(new ReflectionServer(socket));
					}

				} catch(Exception e) {
					e.printStackTrace();
					System.err.println("SimpleMux: Reflection Listener Terminated");
				}
			}});

		/* 
		 * send multiple parallel requests to the reflection server 
		 * directly - not using mux
		 */
		List<Callable<Object>> directTasks = new ArrayList<>();
		var directFactory = SocketFactory.getDefault();
		
		for ( int i = 0; i < 10; i++ )
			directTasks.add(Executors.callable(new RequestClient(i, 
					directFactory.createSocket(InetAddress.getLoopbackAddress(), 2591))));

		var directStatus = Executors.newVirtualThreadPerTaskExecutor().invokeAll(directTasks);
		
		// directStatus.forEach(System.out::println);
		
		System.exit(0);
		// control test complete - clients can talk directly, in parallel, to server
		
		/*
		 * create a thread which will listen for connection on port 2590
		 * each connection accepted will be treated as a multiplexing socket
		 * and so will result in a virtual thread being dispatched to process all
		 * connections tunneled over that connection
		 */

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
					System.err.println("SimpleMux: Tunnel Listener Terminated");
				}
			}});



		/*
		 * create a socket connected to the MuxServer
		 */
		var tunnel = directFactory.createSocket(InetAddress.getLoopbackAddress(), 2590);
		
		/* 
		 * send multiple parallel requests to the reflection server 
		 * via the mux'd connection
		 */
		List<Callable<Object>> tunelledTasks = new ArrayList<>();
		// TODO: get muxfactory on established socket
		
		for ( int i = 0; i < 10; i++ )
			tunelledTasks.add(Executors.callable(new RequestClient(i, 
					directFactory.createSocket(InetAddress.getLoopbackAddress(), 2591))));; // TODO: use muxfactory

		var tunneledStatus = Executors.newVirtualThreadPerTaskExecutor().invokeAll(tunelledTasks);
		
		tunnel.close();
		Thread.sleep(5_000);
	}

	private class RequestClient implements Runnable {
		private final int AWAIT_HELLO_THERE = 1;
		private final int AWAIT_BYE_THEN = 2;
		
		private int id;
		private Socket socket;
		private int state;

		public RequestClient(int id, Socket socket) {
			this.id = id;
			this.socket = socket;
		}

		@Override
		public void run() {
			System.out.println("RequestClient: Instance "+id+" Running");
			
			try {
				var out = new PrintWriter(socket.getOutputStream(), true);
				var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				out.println("HELLO");
				state = AWAIT_HELLO_THERE;
				
				String reply = in.readLine();
				if ("HELLO THERE".equals(reply) && state == AWAIT_HELLO_THERE ) {
					out.println("BYE");
					state = AWAIT_BYE_THEN;
				}
				else if ("BYE THEN".equals(reply) && state == AWAIT_BYE_THEN ) {
					socket.close();
				} else {
					System.err.println("RequestClient: unknown message or state transition "+reply);
					out.println("ERROR");
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}

			System.out.println("RequestClient: Instance "+id+" Closed");
		}

	}

	private class ReflectionServer implements Runnable {
		private final int AWAIT_HELLO = 1;
		private final int AWAIT_BYE = 2;
		
		private Socket socket;
		private int state = AWAIT_HELLO;

		public ReflectionServer(Socket socket) {
			this.socket = socket;
		}

		@Override
		public void run() {
			System.out.println("ReflectionServer: Instance Running");
			
			try {
				var out = new PrintWriter(socket.getOutputStream(), true);
				var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				String greeting = in.readLine();
				if ("HELLO".equals(greeting) && state == AWAIT_HELLO ) {
					out.println("HELLO THERE");
					state = AWAIT_BYE;
				}
				else if ("BYE".equals(greeting) && state == AWAIT_BYE ) {
					out.println("BYE THEN");
				} else {
					System.err.println("ReflectionServer: unknown message or state transition "+greeting);
					out.println("ERROR");
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("ReflectionServer: Instance Closed");
		}
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		new SimpleMuxing();
	}


}

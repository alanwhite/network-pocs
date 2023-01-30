package plexit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import javax.naming.LimitExceededException;

public class MuxServer implements Runnable {
	public static final byte CONNECT_REQUEST = 1;
	public static final byte CONNECT_CONFIRM = 2;
	public static final byte CONNECT_FAIL = 3;
	public static final byte CLOSE = 4;
	public static final byte DATA = 5;

	private static final int BUFSIZE = 4096;
	private static final int MAX_STREAMS = 100;

	/**
	 * The socket we are multiplexing on
	 */
	private Socket tunnelSocket;

	/**
	 * The individual sockets we are demultiplexing to
	 */
	final ConcurrentHashMap<Integer, Socket> sockets = new ConcurrentHashMap<Integer, Socket>();

	/**
	 * Queue that mux sockets will put buffers on to be sent across the tunnel
	 */
	// private final BlockingQueue<byte[]> sendQ = new PriorityBlockingQueue<>();
	private final BlockingQueue<ByteBuffer> sendQ = new ArrayBlockingQueue<>(10);

	public MuxServer(Socket tunnelSocket) {
		this.tunnelSocket = tunnelSocket;
	}

	@SuppressWarnings("preview")
	@Override
	public void run() {
		try {
			
			/*
			 * Reads data off the priority queue for sending to the other side of a tunnel socket
			 *
			 */

			Executors.newVirtualThreadPerTaskExecutor().submit(new Runnable() {

				@Override
				public void run() {
					try {
						var muxOut = tunnelSocket.getOutputStream();
						
						var buffer = sendQ.take();
						
						muxOut.write(buffer.array(),0,buffer.limit());
						
					}	catch( Exception e) {
						e.printStackTrace();
						System.err.println("MuxServer: Tunnel disconnected");
					}
					
				}});


			var muxIn = tunnelSocket.getInputStream();

			// process incoming data on the tunnel - do we care if this blocks? Other threads handle per stream IO
			byte[] buffer = new byte[BUFSIZE];

			int bytesRead = muxIn.read(buffer);
			while ( bytesRead != 0 ) {

				if ( bytesRead < 3 )
					throw( new IllegalArgumentException("Mux protocol error - no stream & command identifiers provided") );

				// stream_id/command/data
				var stream = Integer.valueOf(buffer[0]);

				switch( buffer[1] ) { // this will block
				case CONNECT_REQUEST -> handleConnect(buffer, bytesRead, stream);

				case CLOSE -> { // prevent blocking
					Executors.newVirtualThreadPerTaskExecutor().submit(new Runnable() {

						@Override
						public void run() {
							try {
								sockets.get(stream).close();
								sockets.remove(stream);
							} catch (Exception closeEx ) {
								closeEx.printStackTrace();
							}
						}});
	
				}

				case DATA -> { // nope - need to prevent blocking
					try {
						sockets.get(stream).getOutputStream().write(buffer, 2, bytesRead - 2);
					} catch (Exception dataEx ) {
						dataEx.printStackTrace();
					}
				}

				default -> throw( new IllegalArgumentException("Mux protocol error - unknown command identifier") );
				}

				bytesRead = muxIn.read(buffer);					
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Mux tunnel terminated");
		}

	}

	/**
	 * 
	 * @param buffer
	 * @param bytesRead
	 * @param muxOut
	 * @param stream
	 * @throws IOException 
	 */
	@SuppressWarnings("preview")
	private void handleConnect(byte[] buffer, int bytesRead, OutputStream muxOut, int stream) throws IOException {
		
		
		try {
			// extract the target host and port
			var target = new String(buffer, 2, bytesRead - 2, StandardCharsets.UTF_8);
			URI uri = new URI(null,target,null,null,null);

			// allocate the stream to be used

			var targetSocket = new Socket(InetAddress.getByName(uri.getHost()), uri.getPort()); // this will block the whole stream!!!!!!
			System.out.println("MuxServer: Connection opened to "+target+" for stream "+stream);	

			targetSocket.setSoTimeout(60000);

			// adding it to the local map of connections
			sockets.put(stream, targetSocket);

			// handle any input from the socket, relaying across the allocated stream
			Executors.newVirtualThreadPerTaskExecutor().execute(new Runnable() {

				@Override
				public void run() {
					try {
						var targetIn = targetSocket.getInputStream();
						byte[] targetBuf = new byte[BUFSIZE];
						targetBuf[0] = (byte) stream;
						targetBuf[1] = DATA;

						// we read into the buffer past the control data
						int targetRead = targetIn.read(targetBuf,2,BUFSIZE - 2);
						while( targetRead != 0 ) {
							sendQ.put(ByteBuffer.wrap(targetBuf, 0, targetRead +2));

							// TODO: implement flow control here
							
							targetRead = targetIn.read(targetBuf,2,BUFSIZE - 2);
						}
					} catch ( Exception inpEx ) {
						inpEx.printStackTrace();
						// TODO: do we need to perform housekeeping on the stream table?
					}
					System.out.println("MuxServer: input thread for stream "+stream+" closed");
				}
			});

			// send confirm back
			byte[] confirm = { (byte) stream, CONNECT_CONFIRM };
			muxOut.write(confirm);
			muxOut.flush();

		} catch (Exception connectEx) {
			System.err.println("MuxServer: Failure opening connection");
			connectEx.printStackTrace();
		
			// then send connect failed to requestor - if this causes an exception it's 
			// on the mux socket so we should let it fail the whole tunnel
			byte[] fail = { buffer[0], CONNECT_FAIL };
			muxOut.write(fail);
			muxOut.flush();
		}
	}

}

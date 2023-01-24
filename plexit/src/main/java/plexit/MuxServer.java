package plexit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class MuxServer implements Runnable {
	public static final byte CONNECT_REQUEST = 1;
	public static final byte CONNECT_CONFIRM = 2;
	public static final byte CONNECT_FAIL = 3;
	public static final byte CLOSE = 4;
	public static final byte DATA = 5;

	private static final int BUFSIZE = 4096;

	/**
	 * The socket we are multiplexing on
	 */
	private Socket socket;

	/**
	 * The individual sockets we are demultiplexing to
	 */
	final ConcurrentHashMap<Integer, Socket> sockets = new ConcurrentHashMap<Integer, Socket>();

	public MuxServer(Socket socket) {
		this.socket = socket;
	}

	@Override
	public void run() {
		try {
			var muxIn = socket.getInputStream();
			var muxOut = socket.getOutputStream();

			// process incoming data on the tunnel - do we care if this blocks? Other threads handle per stream IO
			byte[] buffer = new byte[BUFSIZE];
			
			int bytesRead = muxIn.read(buffer);
			while ( bytesRead != 0 ) {
			
				if ( bytesRead < 3 )
					throw( new IllegalArgumentException("Mux protocol error - no stream & command identifiers provided") );

				// stream_id/command/data
				var stream = Integer.valueOf(buffer[0]);

				switch( buffer[1] ) {
				case CONNECT_REQUEST -> handleConnect(buffer, bytesRead, muxOut, stream);
				
				case CLOSE -> {
					try {
						sockets.get(stream).close();
						sockets.remove(stream);
					} catch (Exception closeEx ) {
						closeEx.printStackTrace();
					}
				}

				case DATA -> {
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

			var targetSocket = new Socket(InetAddress.getByName(uri.getHost()), uri.getPort());
			System.out.println("MuxServer: Connection opened to "+target+" for stream "+stream);	
			
			targetSocket.setSoTimeout(60000);

			// adding it to the local map of connections
			sockets.put(stream, targetSocket);

			// need to get some kind of handler on the input stream on that targetSocket?
			// needs to close down the socket and remove from the map if there's an IO error
			// should it send a control frame saying IO error on target? Or just close it?
			Executors.newVirtualThreadPerTaskExecutor().execute(new Runnable() {

				@Override
				public void run() {
					try {
						var targetIn = targetSocket.getInputStream();
						byte[] targetBuf = new byte[BUFSIZE];
						targetBuf[0] = buffer[0];
						targetBuf[1] = DATA;
						
						// we read into the buffer past the control data
						int targetRead = targetIn.read(targetBuf,2,BUFSIZE - 2);
						while( targetRead != 0 ) {
							muxOut.write(targetBuf, 0, targetRead + 2);
							targetRead = targetIn.read(targetBuf,2,BUFSIZE - 2);
						}
					} catch ( Exception inpEx ) {
						inpEx.printStackTrace();
					}
					System.out.println("MuxServer: input thread for stream "+stream+" closed");
				}
			});

			// send confirm back
			byte[] confirm = { buffer[0], CONNECT_CONFIRM };
			muxOut.write(confirm);
			muxOut.flush();
			
		} catch (Exception connectEx) {
			System.err.println("MuxServer: Failed to open connection for stream "+stream);
			connectEx.printStackTrace();
			
			// then send connect failed to requestor - if this causes an exception it's 
			// on the mux socket so we should let it fail the whole tunnel
			byte[] fail = { buffer[0], CONNECT_FAIL };
			muxOut.write(fail);
			muxOut.flush();
		}
	}

}

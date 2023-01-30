package plexit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.BitSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import javax.naming.LimitExceededException;
import javax.net.SocketFactory;

public class MuxSocketFactory extends SocketFactory {
	private static final int BUFSIZE = 4096;
	private static final int MAX_STREAMS = 100;

	private SocketFactory factory;
	private Socket tunnelSocket;

	/**
	 * Stores all the sockets created by this factory that share the tunnel
	 */
	private final ConcurrentHashMap<Integer, MuxSocket> sockets = new ConcurrentHashMap<>();

	/**
	 * Tracks which entries in the map of sockets are used/free
	 */
	private final BitSet slots = new BitSet(128); 

	/**
	 * Ensure atomicity when accessing bitset tracking allocation of map entries
	 */
	private final ReentrantLock slotLock = new ReentrantLock();
	
	/**
	 * Queue that mux sockets will put buffers on to be sent across the tunnel
	 */
	// private final BlockingQueue<byte[]> sendQ = new PriorityBlockingQueue<>();
	private final BlockingQueue<ByteBuffer> sendQ = new ArrayBlockingQueue<>(10);

	@SuppressWarnings("preview")
	public MuxSocketFactory(SocketFactory factory, Socket tunnelSocket) {
		this.factory = factory;
		this.tunnelSocket = tunnelSocket;

		/*
		 * Reads data off the tunnels outputstream and dispatch
		 * without blocking, or the whole tunnel is blocked
		 * 
		 */

		Executors.newVirtualThreadPerTaskExecutor().submit(new Runnable() {

			@Override
			public void run() {
				try {
					var muxIn = tunnelSocket.getInputStream();
					byte[] buffer = new byte[BUFSIZE];

					int bytesRead = muxIn.read(buffer);
					while ( bytesRead != 0 ) {

						if ( bytesRead < 3 )
							throw( new IllegalArgumentException("Mux protocol error - no stream & command identifiers provided") );

						// stream_id/command/data
						var stream = Integer.valueOf(buffer[0]);

						var socket = sockets.get(stream);
						if ( socket == null ) {
							System.err.println("MuxSocket: Stream "+stream+" is not in use, ignoring data");
						} else {
							var q = socket.getReceiveQueue();

							// copy buffer and queue it
							byte[] socketBuffer = new byte[bytesRead];
							System.arraycopy(buffer, 0, socketBuffer, 0, bytesRead);

							if ( !q.offer(ByteBuffer.wrap(socketBuffer, 0, bytesRead)) ) {
								System.err.println("MuxSocket: Discarding data for blocked stream "+stream);
							}
						}

					}

					System.out.println("MuxSocket: Tunnel has closed");
					// should we close any open sockets or let them time out?

				} catch( Exception e) {
					e.printStackTrace();
					System.err.println("MuxSocket: Tunnel disconnected");
				}

			}});

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
					System.err.println("MuxSocket: Tunnel disconnected");
				}
				
			}});

	}

	public MuxSocketFactory(Socket tunnelSocket) {
		this(SocketFactory.getDefault(),tunnelSocket);
	}

	@Override
	public Socket createSocket() throws IOException {
		return new MuxSocket();
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
		return new MuxSocket(host, port);
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
		return new MuxSocket(host, port, localHost, localPort);
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		return new MuxSocket(host, port);
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
		return new MuxSocket(address, port, localAddress, localPort);
	}

	/**
	 * 
	 * @author Alan R. White
	 *
	 */
	private class MuxSocket extends Socket {
		private int stream = -1;
		private ArrayBlockingQueue<ByteBuffer> tunnelIn = new ArrayBlockingQueue<>(10);

		private Runnable queueReader = null;

		private MuxInputStream inputStream;
		private MuxOutputStream outputStream;

		private CompletableFuture<byte []> connectResult, closeRequested;

		public MuxSocket() throws IOException {
			factory.createSocket();
		}

		public MuxSocket(String host, int port) throws IOException {
			factory.createSocket(host, port);
		}

		public MuxSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
			factory.createSocket(host, port, localHost, localPort);
		}

		public MuxSocket(InetAddress host, int port) throws IOException {
			factory.createSocket(host, port);
		}

		public MuxSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
			factory.createSocket(address, port, localAddress, localPort);
		}

		@Override
		public void connect(SocketAddress endpoint) throws IOException {
			connect(endpoint, 0);
		}

		@SuppressWarnings("preview")
		@Override
		public void connect(SocketAddress endpoint, int timeout) throws IOException {

			if ( !(endpoint instanceof InetSocketAddress) ) 
				throw ( new IOException( new UnsupportedAddressTypeException() ) );

			// start the thread than handles all input from the tunnel for this socket
			if ( queueReader == null ) {
				queueReader = new Runnable() {

					@Override
					public void run() {
						while( true ) {
							try {
								var incoming = getReceiveQueue().take();
								tunnelReceiver(incoming);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}

					}};
					Executors.newVirtualThreadPerTaskExecutor().submit(queueReader);	
			}

			try {
				slotLock.lock();
				try {
					stream = slots.nextClearBit(0);
					if ( stream == -1 || stream >= MAX_STREAMS ) 
						throw ( new IOException(new LimitExceededException("MuxSocket: concurrent streams limit reached")) );

					slots.set(stream);
				} finally { // as soon as poss.
					slotLock.unlock();
				}

				sockets.put(stream, this);
				connectResult = new CompletableFuture<>();

				var targetAddr = (InetSocketAddress) endpoint;
				var buffer = new byte[1024];
				buffer[0] = (byte) stream;
				buffer[1] = MuxServer.CONNECT_REQUEST;

				byte[] target = (targetAddr.getHostString() + ":" + Integer.toString(targetAddr.getPort())).getBytes();
				System.arraycopy(target, 0, buffer, 2, target.length);

				// do we need a lock on the tunnels outputstream?
				// tunnelSocket.getOutputStream().write(buffer);
				sendQ.put(ByteBuffer.wrap(target));

				var outcome = connectResult.get();

				var opcode = outcome[1];
				if ( opcode == MuxServer.CONNECT_FAIL )
					throw ( new IOException("MuxSocket: Connection failed") );

				closeRequested = new CompletableFuture<>();
				closeRequested.thenAccept(this::handleCloseRequest);

				this.inputStream = new MuxInputStream(stream);
				this.outputStream  = new MuxOutputStream(stream);

			} catch (InterruptedException | ExecutionException e) {
				throw ( new IOException(e) );

			} finally {

				if ( slotLock.isHeldByCurrentThread() ) {
					System.err.println("MuxSocket: attempted exit with lock on socket table");
					slotLock.unlock();
				}

				if ( stream != -1 ) {
					sockets.remove(stream);
					stream = -1;
				}
			}
		}

		/**
		 * Should maybe make this more robust, with an ack back to the server
		 * 
		 * @param data
		 */
		private void handleCloseRequest(byte[] data) {
			try {
				this.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/**
		 * Called by our socket thread that's reading buffers staged
		 * by the tunnel reader for us.
		 * 
		 * Blocking here will eventually cause any IO from the tunnel
		 * to be discarded.
		 * 
		 * @param incoming
		 */
		public void tunnelReceiver(ByteBuffer incoming) {

			if ( incoming.limit() < 3 ) {
				// tell the world
				return;
			}

			switch( incoming.array()[1] ) {
			case MuxServer.CONNECT_CONFIRM, MuxServer.CONNECT_FAIL -> connectResult.complete(incoming.array());
			case MuxServer.CLOSE -> closeRequested.complete(incoming.array());
			case MuxServer.DATA -> {

				if ( inputStream.supplyData(incoming) ) {
					// tell other end flow to continue
				} else {
					// tell other end flow is held
				}

				// maybe we return in the connect confirm how many buffers can be
				// sent initially, and the flow control here is like
				// when sending decrement the buff count.
				// you can get messages back saying incr buff count or not
				// you'll get an incr message if supplyData returns true

			}
			default -> {
				// wtf?
			}
			}
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return inputStream;
		}

		@Override
		public OutputStream getOutputStream() throws IOException {
			return outputStream;
		}

		@Override
		public void close() throws IOException {
			byte[] buffer = { (byte) stream, MuxServer.CLOSE };
			try {
				sendQ.put(ByteBuffer.wrap(buffer));
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		public ArrayBlockingQueue<ByteBuffer> getReceiveQueue() {
			return tunnelIn;
		}

		/**
		 * Reads data for specified stream
		 * 
		 * @author Alan R. White
		 *
		 */
		private class MuxInputStream extends InputStream {
			private int stream;

			private ArrayBlockingQueue<ByteBuffer> writeQ = new ArrayBlockingQueue<>(3);
			private ArrayBlockingQueue<ByteBuffer> readQ = new ArrayBlockingQueue<>(3);

			private ByteBuffer readBuffer = null;
			private ByteBuffer writeBuffer = null;

			public MuxInputStream(int stream) {
				super();
				this.stream = stream;

				// be ready to receive data
				writeBuffer = ByteBuffer.allocate(BUFSIZE);

				// and pop one in the tank for when the one above is used
				writeQ.add(ByteBuffer.allocate(BUFSIZE));

				// could possibly add more .....
			}

			@SuppressWarnings("preview")
			public boolean supplyData(ByteBuffer buffer) {

				// we really don't want this to block
				// if it does it's because of a protocol error

				// should only by null first time through
				if ( writeBuffer == null ) {
					try {
						writeBuffer = writeQ.take();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				writeBuffer.put(buffer.array());
				writeBuffer.flip();

				try {
					readQ.put(writeBuffer);
					writeBuffer = null;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				// if there's more writeQ buffers available, get one
				// ready for next time and return true otherwise
				// watch for one becoming available and release flow
				// and return false say stop to the flow

				writeBuffer = writeQ.poll();

				if ( writeBuffer == null ) {
					// thread to wait, get, notify
					Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
						try {
							writeBuffer = writeQ.take();

							// TODO: send a message to the other end to release the flow

						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					});
					return false;
				}

				return true;
			}

			@Override
			public int read() throws IOException {

				// do I have a read buffer to read from, and if not get one
				// possibly blocking, because that's what read does

				if ( readBuffer == null ) {
					try {
						readBuffer = readQ.take();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				// at this point assume we have a buffer to read from
				var data = readBuffer.get();

				// if we emptied the buffer make it available for writing
				if ( !readBuffer.hasRemaining() ) {
					readBuffer.rewind();
					try {
						writeQ.put(readBuffer);
						readBuffer = null;
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				return data;
			}

		}

		/**
		 * Writes data to specified stream
		 * 
		 * @author Alan R. White
		 *
		 */
		private class MuxOutputStream extends OutputStream {
			private int stream;

			public MuxOutputStream(int stream) {
				super();
				this.stream = stream;
			}

			@Override
			public void write(int b) throws IOException {
				byte[] buffer = { (byte) stream, MuxServer.DATA, (byte) b  };
				try {
					sendQ.put(ByteBuffer.wrap(buffer));
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			// TODO: add write(byte[], offset, length) to optimize.
		}
	}
}

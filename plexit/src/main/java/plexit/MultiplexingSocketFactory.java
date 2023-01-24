package plexit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.SocketFactory;

public class MultiplexingSocketFactory extends SocketFactory {
	private final SocketFactory factory;
	private final Socket multiplexingSocket;
	private final ConcurrentHashMap<Integer, CompletableFuture<byte[]>> responseFutures = new ConcurrentHashMap<Integer, CompletableFuture<byte[]>>();
	private final AtomicInteger idCounter = new AtomicInteger(0);

	public MultiplexingSocketFactory(SocketFactory factory, Socket multiplexingSocket) {
		this.factory = factory;
		this.multiplexingSocket = multiplexingSocket;
	}

	@Override
	public Socket createSocket() throws IOException {
		return new MultiplexingSocket();
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
		return new MultiplexingSocket(host, port);
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
		return new MultiplexingSocket(host, port, localHost, localPort);
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		return new MultiplexingSocket(host, port);
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
		return new MultiplexingSocket(address, port, localAddress, localPort);
	}

	private class MultiplexingSocket extends Socket {
		private int id;

		public MultiplexingSocket() throws IOException {
			factory.createSocket();
		}

		public MultiplexingSocket(String host, int port) throws IOException {
			factory.createSocket(host, port);
		}

		public MultiplexingSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
			factory.createSocket(host, port, localHost, localPort);
		}

		public MultiplexingSocket(InetAddress host, int port) throws IOException {
			factory.createSocket(host, port);
		}

		public MultiplexingSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
			factory.createSocket(address, port, localAddress, localPort);
		}

		@Override
		public void connect(SocketAddress endpoint) throws IOException {
			connect(endpoint, 0);
		}

		@Override
		public void connect(SocketAddress endpoint, int timeout) throws IOException {
			id = idCounter.incrementAndGet();
			CompletableFuture<byte[]> responseFuture = new CompletableFuture<byte[]>();
			responseFutures.put(id, responseFuture);
			multiplexingSocket.getOutputStream().write((id + ":" + endpoint.toString() + ":" + timeout).getBytes());
			byte[] response = null;
			try {
				response = responseFuture.get();
			} catch (InterruptedException e) {
				throw new IOException("Interrupted while waiting for response", e);
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (!new String(response).equals("OK")) {
				throw new IOException("Error connecting socket: " + new String(response));
			}
		}



		@Override
		public InputStream getInputStream() throws IOException {
			return multiplexingSocket.getInputStream();
		}

		@Override
		public OutputStream getOutputStream() throws IOException {
			return multiplexingSocket.getOutputStream();
		}

		@Override
		public void close() throws IOException {
			multiplexingSocket.getOutputStream().write((id + ":CLOSE").getBytes());
		}
	}
}


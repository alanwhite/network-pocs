package plexit;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class MultiplexingServer {
	protected final ServerSocket serverSocket;
	final ConcurrentHashMap<Integer, Socket> sockets = new ConcurrentHashMap<Integer, Socket>();

	public MultiplexingServer(int port) throws IOException {
		serverSocket = new ServerSocket(port);

		Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					while (true) {
						final Socket socket = serverSocket.accept();
						Thread thread = new Thread(new Runnable() {
							public void run() {
								try {
									InputStream in = socket.getInputStream();
									byte[] buffer = new byte[4096];
									while (true) {
										int read = in.read(buffer);
										if (read == -1) {
											break;
										}
										String message = new String(buffer, 0, read);
										String[] parts = message.split(":", 3);
										int id = Integer.parseInt(parts[0]);
										String command = parts[1];
										if (command.equals("CONNECT")) {
											String[] endpointParts = parts[2].split(":", 2);
											String host = endpointParts[0];
											int port = Integer.parseInt(endpointParts[1]);
											int timeout = Integer.parseInt(parts[3]);
											Socket multiplexedSocket = new Socket(host, port);
											multiplexedSocket.connect(new InetSocketAddress(host, port), timeout);
											sockets.put(id, multiplexedSocket);
											socket.getOutputStream().write("OK".getBytes());
										} else if (command.equals("CLOSE")) {
											sockets.get(id).close();
											sockets.remove(id);
										} else {
											Socket multiplexedSocket = sockets.get(id);
											multiplexedSocket.getOutputStream().write(buffer, 0, read);
											int multiplexedRead = multiplexedSocket.getInputStream().read(buffer);
											socket.getOutputStream().write(buffer, 0, multiplexedRead);
										}
									}

								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						});
						thread.start();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		thread.start();
	}

	public void close() throws IOException {
		serverSocket.close();
	}
}

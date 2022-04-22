package xyz.arwhite.net.wockit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import xyz.arwhite.net.wockit.WebSocketSession.WebSocketState;

public class WebSocketClient {


 
	private Socket serverConn;
	private String resource;
	private String secKey64;

	private WebSocketRunner webSocketRunner;
	private WebSocketSession session;
	// private WebSocketHandler handler;
	
	private URI uri;
	
	public WebSocketClient(String path, WebSocketAdapter handler) throws Exception {
		// this.handler = handler;
		
		uri = new URI(path);

		// temp session
		session = new WebSocketSession(null, false);
		
		try {
			InetAddress address = InetAddress.getByName(uri.getHost());
			serverConn = new Socket(address, uri.getPort());
			serverConn.setSoTimeout(2000);
			
			makeWebSocketRequest(); 

		} catch (Exception e) {
			session.setState(WebSocketState.CLOSED);
			handler.onError(session);
			throw(e);
		}	

		// overwrite session now we have a socket
		session = new WebSocketSession(serverConn, true);
		webSocketRunner = new WebSocketRunner(session, handler);
		
		// validate response from server
		try {
			awaitWebSocketConfirm();
			
		} catch (Exception e) {
			try { serverConn.close(); } catch (Exception e1) {}
			session.setState(WebSocketState.CLOSED);
			handler.onError(session);
			throw(e);
		}

		webSocketRunner.runConnection(serverConn);

	}

	private boolean makeWebSocketRequest() throws IOException {

			// buffered writer
			var serverWriter = new BufferedWriter(new OutputStreamWriter(serverConn.getOutputStream(), "US-ASCII"));

			// request line
			serverWriter.write("GET "+resource+" HTTP/1.1");

			// headers
			serverWriter.write("Host: "+uri.getHost()+":"+uri.getPort());
			serverWriter.write("Upgrade: websocket");
			serverWriter.write("Connection: Upgrade");

			var secKey = UUID.randomUUID();
			secKey64 = new String(Base64.getEncoder().encode(asBytes(secKey)));
			serverWriter.write("Sec-WebSocket-Key: "+secKey64);
			serverWriter.write("Sec-WebSocket-Version: 13");

			// empty line
			serverWriter.write("\r\n");
			serverWriter.flush();
			return true;
		
	}

	private boolean awaitWebSocketConfirm() {

		try {
			var serverReader = new BufferedReader(new InputStreamReader(serverConn.getInputStream(), "US-ASCII"));

			// reads
			String response = serverReader.readLine();
			var responseWords = response.split(" ");

			if ( responseWords.length != 3 ) {
				// Too Many Words In Request
				return false;
			}

			// Grab headers
			List<String> headerStrings = new ArrayList<>();
			String headerLine = serverReader.readLine();
			while(headerLine.length() != 0 ) {
				headerStrings.add(headerLine);
				headerLine = serverReader.readLine();
			}

			// make them a map
			Map<String, String> headers = headerStrings.stream()
					.map(s -> s.split(" "))
					.collect(Collectors.toMap(a -> a[0], a -> a.length > 1 ? a[1] : "")); 

			// validate headers
			var upgrade = headers.get("Upgrade:");
			if ( upgrade == null || !upgrade.equals("websocket") ) 
				return false;

			var connection = headers.get("Connection:");
			if ( connection == null || !connection.equals("Upgrade") ) 
				return false;

			var secWebSocketAccept = headers.get("Sec-WebSocket-Accept:");
			if ( secWebSocketAccept == null ) 
				return false;

			// validate sec key
			var acceptKey = Base64.getEncoder().encodeToString(
					MessageDigest.getInstance("SHA-1")
					.digest( (secKey64 + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
							.getBytes("US-ASCII")));

			if ( !secWebSocketAccept.equals(acceptKey) )
				return false;
			
			return true;
			
		} catch(Exception e) {
			return false;
		}

	}

	public static UUID asUuid(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		long firstLong = bb.getLong();
		long secondLong = bb.getLong();
		return new UUID(firstLong, secondLong);
	}

	public static byte[] asBytes(UUID uuid) {
		ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
		bb.putLong(uuid.getMostSignificantBits());
		bb.putLong(uuid.getLeastSignificantBits());
		return bb.array();
	}

}

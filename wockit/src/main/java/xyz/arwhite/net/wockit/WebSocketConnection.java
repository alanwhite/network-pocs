package xyz.arwhite.net.wockit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import xyz.arwhite.net.wockit.WebSocketSession.WebSocketState;

public class WebSocketConnection implements Runnable {

	private record WebSocketRequest(String key, int response) {}

	private Socket clientConn;
	private WebSocketAdapter handler;

	public WebSocketConnection(Socket conn, WebSocketAdapter handler) {
		this.handler = handler;
		clientConn = conn;
	}

	@Override
	public void run() {

		try {

			clientConn.setSoTimeout(60000);

			var session = new WebSocketSession(clientConn, false);
			session.setState(WebSocketState.CLOSED);
			
			var webSocketRequest = readRequest(clientConn);
			if (webSocketRequest.response != 200 ) {
				writeErrorResponseAndClose(clientConn,webSocketRequest.response,webSocketRequest.key);
				return;
			}

			var acceptKey = calculateAcceptKey(webSocketRequest.key);
			if ( acceptKey.isEmpty() ) {
				writeErrorResponseAndClose(clientConn,500,"Cannot Compute Accept Key");
				return;
			}

			if ( !writeSwitchingProtocolsResponse(clientConn,acceptKey.get()) ) 
				return;
			
			var webSocketRunner = new WebSocketRunner(session,handler);
			webSocketRunner.runConnection(clientConn);

		} catch (IOException e) {
			e.printStackTrace();
		} 

	}

	private WebSocketRequest readRequest(Socket clientConn) {

		try {
			var clientReader = new BufferedReader(new InputStreamReader(clientConn.getInputStream(), "US-ASCII"));

			String httpRequest = clientReader.readLine();

			String[] requestWords = httpRequest.split(" ");
			if ( requestWords.length != 3 ) {
				// HTTP/1.1 400 Too Many Words In Request
				return new WebSocketRequest("Too Many Words In Request",400);
			}

			if ( !requestWords[0].equals("GET") ) {
				// HTTP/1.1 501 Only GET Implemented
				return new WebSocketRequest("Only GET Implemented",501);
			}

			if ( !requestWords[1].equals("/wock") ) {
				// HTTP/1.1 404 Not found
				return new WebSocketRequest("Not found",404);
			}

			// Grab headers - buggy - a client can include some headers more than once - ours won't
			List<String> headerStrings = new ArrayList<>();
			String headerLine = clientReader.readLine();
			while(headerLine.length() != 0 ) {
				headerStrings.add(headerLine);
				headerLine = clientReader.readLine();
			}

			// make them a map
			Map<String, String> headers = headerStrings.stream()
					.map(s -> s.split(" "))
					.collect(Collectors.toMap(a -> a[0], a -> a.length > 1 ? a[1] : "")); 

			// validate headers
			var upgrade = headers.get("Upgrade:");
			if ( upgrade == null || !upgrade.equals("websocket") ) 
				return new WebSocketRequest("Bad Upgrade Header",400);

			var connection = headers.get("Connection:");
			if ( connection == null || !connection.equals("Upgrade") ) 
				return new WebSocketRequest("Bad Connection Header",400);

			var secWebSocketKey = headers.get("Sec-WebSocket-Key:");
			if ( secWebSocketKey == null ) 
				return new WebSocketRequest("Missing Sec-WebSocket-Key Header",400);

			var secWebSocketVersion = headers.get("Sec-WebSocket-Version:");
			if ( secWebSocketVersion == null || !secWebSocketVersion.equals("13") ) 
				return new WebSocketRequest("Sec-WebSocket-Version Header Not 13",400);

			return new WebSocketRequest(secWebSocketKey,200);

		} catch (IOException e) {
			e.printStackTrace();
			return new WebSocketRequest("",500);
		} catch (Exception e) {
			return new WebSocketRequest("Bad Request",400);
		}
	}

	private void writeErrorResponseAndClose(Socket clientConn, int response, String reason) {

		try {
			var clientWriter = new BufferedWriter(new OutputStreamWriter(clientConn.getOutputStream(), "US-ASCII"));
			clientWriter.write("HTTP/1.1 "+response+" "+reason);
			clientWriter.flush();

			clientConn.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Optional<String> calculateAcceptKey(String clientKey) {

		try {
			var acceptKey = Base64.getEncoder().encodeToString(
					MessageDigest.getInstance("SHA-1")
					.digest( (clientKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
							.getBytes("US-ASCII")));

			return Optional.of(acceptKey);
			
		} catch (Exception e) {
			e.printStackTrace();
			return Optional.empty();
		}

	}

	private boolean writeSwitchingProtocolsResponse(Socket clientConn, String acceptKey) {

		try {
			var clientWriter = new BufferedWriter(new OutputStreamWriter(clientConn.getOutputStream(), "US-ASCII"));
			clientWriter.write("HTTP/1.1 101 Switching Protocols\r\n");
			clientWriter.write("Upgrade: websocket\r\n");
			clientWriter.write("Connection: Upgrade\r\n");
			clientWriter.write("Sec-WebSocket-Accept: "+acceptKey+"\r\n");
			clientWriter.write("\r\n");
			clientWriter.flush();

			return true;

		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
}

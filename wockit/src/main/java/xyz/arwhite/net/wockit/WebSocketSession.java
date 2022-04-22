package xyz.arwhite.net.wockit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class WebSocketSession {

	public enum WebSocketState { CONNECTING, OPEN, CLOSING, CLOSED };
	public enum FragmentState { TEXT, BINARY, NONE }
	
	private WebSocketState state = WebSocketState.CLOSED;
	private Socket socket;
	private OutputStream writer;

	/*
	 * if client is set to true then frames sent will be masked and any masked frames 
	 * received will fail the connection.
	 * 
	 * if client is set to false we assume server behaviour whic means never mask a 
	 * frame when you send it, if you receive an unmaske frame then fail the connection
	 */
	private boolean client;
	
	private FragmentState fragmentState = FragmentState.NONE;	
	
	public WebSocketSession(Socket socket, boolean client) {
		this.socket = socket;
		this.client = client;
	}

	public WebSocketState getState() {
		return state;
	}

	protected void setState(WebSocketState state) {
		this.state = state;
	}
	
	public void sendText(String text) throws IOException {
		
	}
	
	public void sendBinary(ByteBuffer data) throws IOException {
		
	}
	
	public void sendPing(ByteBuffer data) throws IOException {
		
	}
	
	public void sendPing() throws IOException {
		
	}

	public void sendPong(ByteBuffer data) throws IOException {
		
	}
	
	public void sendPong() throws IOException {
		
	}
	
	public void close(ByteBuffer data) throws IOException {
		this.setState(WebSocketState.CLOSING);
	}
	
	public void close(short reasonCode, String reason) throws IOException {
		// use a bytebuffer to set up the data 
		// close(bytebuffer);
	}
	
	public void close() throws IOException {
		this.setState(WebSocketState.CLOSING);
		
		// probs call the bytebuffer close with a zero length buffer
	}
	
	private void sendFrame(ByteBuffer data) throws IOException {
		if ( writer == null )
			writer = socket.getOutputStream();
		
		if ( isClient() ) {
			// need to mask the frame
		}
	
		// structure frameheader
		// structure framepayload
		// writer.write()
		
		writer.flush();
	}

	public boolean isClient() {
		return client;
	}

	public FragmentState getFragmentState() {
		return fragmentState;
	}

	protected void setFragmentState(FragmentState fragmentState) {
		this.fragmentState = fragmentState;
	}
}

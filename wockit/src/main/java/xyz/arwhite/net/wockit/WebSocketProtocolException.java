package xyz.arwhite.net.wockit;

@SuppressWarnings("serial")
public class WebSocketProtocolException extends Exception {

	short reasonCode;
	
	public WebSocketProtocolException(short reasonCode, String message) {
		super(message);
		this.reasonCode = reasonCode;
	}
	
	public short getReasonCode() {
		return reasonCode;
	}
}

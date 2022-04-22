package xyz.arwhite.net.wockit;

import java.nio.ByteBuffer;

public class WebSocketUtils {

	public enum OpCodeType { CONTINUATION_FRAME, TEXT_FRAME, BINARY_FRAME, CLOSE, PING, PONG, ERROR }
	
	protected record FrameHeader(boolean finalFragment, OpCodeType opCodeType, 
			byte rsv, boolean isMasked, byte simpleLength) {}
	
	protected record FramePayload(byte[] extensionData, int extensionDataLength, byte[] maskingKey,
			ByteBuffer applicationData) {}
	
	public WebSocketUtils() {
		// TODO Auto-generated constructor stub
	}

	public static void applyMask(FramePayload framePayload) {
		
		var data = framePayload.applicationData();
		for ( int i=0; i < data.limit(); i++ ) {
			var j = i % 4;
			data.array()[i] ^= framePayload.maskingKey()[j];
		}
		
	}
}

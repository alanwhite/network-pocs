package xyz.arwhite.net.wockit;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import xyz.arwhite.net.wockit.WebSocketSession.FragmentState;
import xyz.arwhite.net.wockit.WebSocketSession.WebSocketState;
import xyz.arwhite.net.wockit.WebSocketUtils.FrameHeader;
import xyz.arwhite.net.wockit.WebSocketUtils.FramePayload;
import xyz.arwhite.net.wockit.WebSocketUtils.OpCodeType;

/**
 * Manages an RFC 6455 style WebSocket
 * 
 * @author Alan R. White
 */
public class WebSocketRunner {

	private WebSocketSession session;
	private WebSocketAdapter handler;

	public WebSocketRunner(WebSocketSession session, WebSocketAdapter handler) {
		this.session = session;
		this.handler = handler;
	}

	public void runConnection(Socket socket) {

		try (socket) {
			var dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

			session.setState(WebSocketState.OPEN);
			handler.onOpen(session);

			ByteBuffer buffer = ByteBuffer.allocate(8192);
			ByteBuffer fragmentBuffer = ByteBuffer.allocate(Integer.MAX_VALUE);

			boolean done = false;
			int protocolErrorCount = 0;

			while ( !done ) {
				try {
					byte frameControl = dis.readByte();
					byte maskAndSimpleLength = dis.readByte();
					var frameHeader = unpackFrameHeader(frameControl, maskAndSimpleLength);
					validateMasking(frameHeader);

					var framePayload = readFramePayload(frameHeader, dis, buffer);

					if ( frameHeader.isMasked() ) 
						WebSocketUtils.applyMask(framePayload);

					switch(frameHeader.opCodeType()) {
					case PING -> handler.pingMessage(session, framePayload.applicationData());
					case PONG -> handler.pongMessage(session, framePayload.applicationData());
					case CLOSE -> done = handleCloseFrame(frameHeader,framePayload,socket); 
					case TEXT_FRAME, BINARY_FRAME, CONTINUATION_FRAME -> {
						handleMessageFrame(frameHeader,framePayload,fragmentBuffer);
					}
					default -> throw new WebSocketProtocolException((short)1002,"Unknown OpCode");
					}

				} catch (WebSocketProtocolException e) {
					if ( protocolErrorCount++ > 0 ) { // malicious peer?
						done = true;
					} else if ( session.getState() == WebSocketState.OPEN ) {
						session.setState(WebSocketState.CLOSING);
						handler.onError(session);
						session.close(e.getReasonCode(),e.getLocalizedMessage());
					}
				}
			}

		} catch (IOException e) {

			if ( session.getState() != WebSocketState.CLOSED )
				handler.onError(session);

			if ( !e.getMessage().equals("Connection reset") ) 
				e.printStackTrace();
		} 

	}

	private void validateMasking(FrameHeader frameHeader) throws WebSocketProtocolException {
		if ( session.isClient() ) {
			if ( frameHeader.isMasked() ) 
				throw new WebSocketProtocolException((short)1002,"Server shouldn't sent masked frames");
		} else if ( !frameHeader.isMasked() ) {
			throw new WebSocketProtocolException((short)1002,"Client should send masked frames");
		}
	}

	private void handleMessageFrame(FrameHeader frameHeader, FramePayload framePayload, 
			ByteBuffer fragmentBuffer) 
					throws WebSocketProtocolException {

		try {
			switch(frameHeader.opCodeType()) {
			case TEXT_FRAME -> {
				if ( frameHeader.finalFragment() )
					handler.textMessage(session, new String(framePayload.applicationData().array())); 
				else {
					session.setFragmentState(FragmentState.TEXT);
					fragmentBuffer.put(framePayload.applicationData());
				}
			}
			case BINARY_FRAME -> {
				if ( frameHeader.finalFragment() )
					handler.binaryMessage(session, framePayload.applicationData());
				else {
					session.setFragmentState(FragmentState.BINARY);
					fragmentBuffer.put(framePayload.applicationData());
				}
			}
			case CONTINUATION_FRAME ->
			{
				fragmentBuffer.put(framePayload.applicationData());

				if ( frameHeader.finalFragment() ) {
					switch( session.getFragmentState() ) {
					case TEXT -> handler.textMessage(session, new String(fragmentBuffer.array()));
					case BINARY -> handler.binaryMessage(session, fragmentBuffer);
					default -> throw new WebSocketProtocolException((short)1009,"Fragmentation sequence error");
					}

					fragmentBuffer.clear();
				}
				session.setFragmentState(FragmentState.NONE);
			}
			default -> throw new WebSocketProtocolException((short)1002,"Unknown OpCode");
			}
		} catch (BufferOverflowException e) {
			throw new WebSocketProtocolException((short)1009,"Caolesced fragments exceeds max buffer size");
		}
	}


	private boolean handleCloseFrame(FrameHeader frameHeader, FramePayload framePayload, Socket socket) 
			throws IOException {

		if ( session.getState() == WebSocketState.CLOSING ) {
			// we had sent a close frame, so time to close the socket
			handler.onClose(session);
			session.setState(WebSocketState.CLOSED);
			if ( session.isClient() ) {
				// we wait for the server to close the TCP connection
			} else
				socket.close();
		} else {
			// other end has issued a close
			session.setState(WebSocketState.CLOSING);
			handler.onClose(session);
			session.close(framePayload.applicationData());
			session.setState(WebSocketState.CLOSED);
			if ( session.isClient() ) {
				// we should wait for the server to close the TCP connection
			} else
				socket.close();
		}

		return true;
	}

	private FramePayload readFramePayload(FrameHeader frameHeader, DataInputStream dis, ByteBuffer buffer) 
			throws WebSocketProtocolException, IOException {

		long payloadLength = frameHeader.simpleLength();
		if ( frameHeader.simpleLength() == 126 )
			payloadLength = Short.toUnsignedLong(dis.readShort());
		else if ( frameHeader.simpleLength() == 127 )
			payloadLength = dis.readLong();

		byte[] maskingKey = { 0,0,0,0 };
		if ( frameHeader.isMasked() )
			maskingKey = dis.readNBytes(4);

		if ( buffer.capacity() < payloadLength ) {
			if ( payloadLength > Integer.MAX_VALUE ) {
				throw new WebSocketProtocolException((short)1009,"Can't make buffers as big as this payload");
			}
			buffer = ByteBuffer.allocate((int) payloadLength);
		}

		/*
		 * May need to come back to this as we effectively ignore extension data
		 */

		var bytesRead = 0;
		while ( bytesRead < payloadLength )
			dis.read(buffer.array(), bytesRead, (int) payloadLength - bytesRead);

		ByteBuffer applicationData = ByteBuffer.wrap(buffer.array(),0,(int) payloadLength);

		return new FramePayload(buffer.array(),0,maskingKey,applicationData);

	}

	private FrameHeader unpackFrameHeader(byte frameControl, byte maskAndSimpleLength) {

		boolean finalFragment = (frameControl & 0b10000000) == 0b10000000;
		byte opCode = (byte) (frameControl & 0b00001111);

		OpCodeType opCodeType = switch(opCode) {
		case 0 -> OpCodeType.CONTINUATION_FRAME;
		case 1 -> OpCodeType.TEXT_FRAME;
		case 2 -> OpCodeType.BINARY_FRAME;
		case 8 -> OpCodeType.CLOSE;
		case 9 -> OpCodeType.PING;
		case 10 -> OpCodeType.PONG;
		default -> OpCodeType.ERROR;
		};
		// if opCodeType == OpCodeType.ERROR .....

		byte rsvValue = (byte) ((frameControl & 0b01110000) >>> 4);
		// if rsvValue not 0 then some extensions should have been negotiate

		boolean isMasked = (maskAndSimpleLength & 0b10000000) == 0b10000000;

		byte simpleLength = (byte) (maskAndSimpleLength & 0b01111111);

		return new FrameHeader(finalFragment,opCodeType,rsvValue,isMasked,simpleLength);
	}

}




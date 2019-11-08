package com.example.mediasoupandroidsample.socket;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Socket Events Enum
 */
class ActionEvent {
	// Socket connected to the server
	public static final String OPEN = "open";
	// Mediasoup router rtpCapabilities
	private static final String ROOM_RTP_CAPABILITIES = "roomRtpCapabilities";
	// Room Login
	private static final String LOGIN_ROOM = "loginRoom";
	// Create new WebRtcTransport
	private static final String CREATE_WEBRTC_TRANSPORT = "createWebRtcTransport";
	// Connect WebRtcTransport
	private static final String CONNECT_WEBRTC_TRANSPORT = "connectWebRtcTransport";
	// Send media to mediasoup
	private static final String PRODUCE = "produce";

	@StringDef({ OPEN, ROOM_RTP_CAPABILITIES, LOGIN_ROOM, CREATE_WEBRTC_TRANSPORT, CONNECT_WEBRTC_TRANSPORT, PRODUCE })
	@Retention(RetentionPolicy.SOURCE)
	public @interface Event {}
}

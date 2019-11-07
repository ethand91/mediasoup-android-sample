package com.example.mediasoupandroidsample.socket;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class ActionEvent {
	public static final String ROOM_RTP_CAPABILITIES = "roomRtpCapabilities";
	public static final String LOGIN_ROOM = "loginRoom";
	public static final String CREATE_WEBRTC_TRANSPORT = "createWebRtcTransport";
	public static final String CONNECT_WEBRTC_TRANSPORT = "connectWebRtcTransport";
	public static final String PRODUCE = "produce";

	@StringDef({ ROOM_RTP_CAPABILITIES, LOGIN_ROOM, CREATE_WEBRTC_TRANSPORT, CONNECT_WEBRTC_TRANSPORT, PRODUCE })
	@Retention(RetentionPolicy.SOURCE)
	public @interface Event {}
}

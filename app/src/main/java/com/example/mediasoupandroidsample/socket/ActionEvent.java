package com.example.mediasoupandroidsample.socket;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Socket Events Enum
 */
public class ActionEvent {
	// Socket connected to the server
	public static final String OPEN = "open";
	// Mediasoup router rtpCapabilities
	public static final String ROOM_RTP_CAPABILITIES = "roomRtpCapabilities";
	// Room Login
	public static final String LOGIN_ROOM = "loginRoom";
	// Create new WebRtcTransport
	public static final String CREATE_WEBRTC_TRANSPORT = "createWebRtcTransport";
	// Connect WebRtcTransport
	public static final String CONNECT_WEBRTC_TRANSPORT = "connectWebRtcTransport";
	// Send media to mediasoup
	public static final String PRODUCE = "produce";
	// newuser notification
	public static final String NEW_USER = "newuser";
	// newconsumer notification
	public static final String NEW_CONSUMER = "newconsumer";
	// pause producer
	public static final String PAUSE_PRODUCER = "pauseProducer";
	// resume producer
	public static final String RESUME_PRODUCER = "resumeProducer";
	// pause consumer
	public static final String PAUSE_CONSUMER = "pauseConsumer";
	// resume consumer
	public static final String RESUME_CONSUMER = "resumeConsumer";
	// rtc stats
	public static final String RTC_STATS = "rtcStats";

	@StringDef({ OPEN, ROOM_RTP_CAPABILITIES, LOGIN_ROOM, CREATE_WEBRTC_TRANSPORT, CONNECT_WEBRTC_TRANSPORT, PRODUCE, NEW_USER, NEW_CONSUMER, PAUSE_PRODUCER, RESUME_PRODUCER, PAUSE_CONSUMER, RESUME_CONSUMER, RTC_STATS })
	@Retention(RetentionPolicy.SOURCE)
	public @interface Event {}
}

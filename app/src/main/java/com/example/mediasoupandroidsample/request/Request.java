package com.example.mediasoupandroidsample.request;

import com.example.mediasoupandroidsample.socket.ActionEvent;
import com.example.mediasoupandroidsample.socket.EchoSocket;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Socket Request util class
 */
public class Request {
	/**
	 * Socket request acknowledgement response timeout
	 */
	private static final int REQUEST_TIMEOUT_SECONDS = 3000;

	// Send getRoomRtpCapabilities request
	public static JSONObject sendGetRoomRtpCapabilitiesRequest(EchoSocket socket, String roomId)
	throws JSONException, InterruptedException, ExecutionException, TimeoutException {
		JSONObject getRoomRtpCapabilitiesRequest = new JSONObject();
		getRoomRtpCapabilitiesRequest.put("action", "getRoomRtpCapabilities");
		getRoomRtpCapabilitiesRequest.put("roomId", roomId);

		return socket.sendWithFuture(getRoomRtpCapabilitiesRequest).get(Request.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	// Send loginRoom request
	public static JSONObject sendLoginRoomRequest(EchoSocket socket, String roomId, String deviceRtpCapabilities)
	throws JSONException, InterruptedException, ExecutionException, TimeoutException{
		JSONObject loginRoomRequest = new JSONObject();
		loginRoomRequest.put("action", ActionEvent.LOGIN_ROOM);
		loginRoomRequest.put("roomId", roomId);
		loginRoomRequest.put("rtpCapabilities", new JSONObject(deviceRtpCapabilities));

		return socket.sendWithFuture(loginRoomRequest).get(Request.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	// Send createWebRtcTransport request
	public static JSONObject sendCreateWebRtcTransportRequest(EchoSocket socket, String roomId, String direction)
	throws JSONException, InterruptedException, ExecutionException, TimeoutException {
		JSONObject createWebRtcTransportRequest = new JSONObject();
		createWebRtcTransportRequest.put("action", ActionEvent.CREATE_WEBRTC_TRANSPORT);
		createWebRtcTransportRequest.put("roomId", roomId);
		createWebRtcTransportRequest.put("direction", direction);

		return socket.sendWithFuture(createWebRtcTransportRequest).get(Request.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	// Send connectWebRtcTransport request
	public static void sendConnectWebRtcTransportRequest(EchoSocket socket, String roomId, String transportId, String dtlsParameters)
	throws JSONException {
		JSONObject connectWebRtcTransportRequest = new JSONObject();
		connectWebRtcTransportRequest.put("action", ActionEvent.CONNECT_WEBRTC_TRANSPORT);
		connectWebRtcTransportRequest.put("roomId", roomId);
		connectWebRtcTransportRequest.put("transportId", transportId);
		connectWebRtcTransportRequest.put("dtlsParameters", new JSONObject(dtlsParameters));

		socket.send(connectWebRtcTransportRequest);
	}

	// Send produce request
	public static JSONObject sendProduceWebRtcTransportRequest(EchoSocket socket, String roomId, String transportId, String kind, String rtpParameters)
	throws JSONException, InterruptedException, ExecutionException, TimeoutException {
		JSONObject produceWebRtcTransportRequest = new JSONObject();
		produceWebRtcTransportRequest.put("action", ActionEvent.PRODUCE);
		produceWebRtcTransportRequest.put("roomId", roomId);
		produceWebRtcTransportRequest.put("transportId", transportId);
		produceWebRtcTransportRequest.put("kind", kind);
		produceWebRtcTransportRequest.put("rtpParameters", new JSONObject(rtpParameters));

		return socket.sendWithFuture(produceWebRtcTransportRequest).get(Request.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}
}

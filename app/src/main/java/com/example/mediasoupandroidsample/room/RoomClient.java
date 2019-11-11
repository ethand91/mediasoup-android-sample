package com.example.mediasoupandroidsample.room;

import android.content.Context;
import android.util.Log;

import com.example.mediasoupandroidsample.media.MediaCapturer;
import com.example.mediasoupandroidsample.request.Request;
import com.example.mediasoupandroidsample.socket.EchoSocket;

import org.json.JSONException;
import org.json.JSONObject;
import org.mediasoup.droid.Consumer;
import org.mediasoup.droid.Device;
import org.mediasoup.droid.Producer;
import org.mediasoup.droid.RecvTransport;
import org.mediasoup.droid.SendTransport;
import org.mediasoup.droid.Transport;
import org.webrtc.EglBase;
import org.webrtc.MediaStreamTrack;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Mediasoup room client
 */
public class RoomClient {
	private static final String TAG = "RoomClient";

	private final EchoSocket mSocket;
	private final String mRoomId;
	private final MediaCapturer mMediaCapturer;

	private Device mDevice;
	private Boolean mJoined;
	private SendTransport mSendTransport;
	private RecvTransport mRecvTransport;
	private ConcurrentHashMap<String, Producer> mProducers;
	private ConcurrentHashMap<String, Consumer> mConsumers;

	public RoomClient(EchoSocket socket, Device device, String roomId) {
		mSocket = socket;
		mRoomId = roomId;
		mDevice = device;
		mProducers = new ConcurrentHashMap<>();
		mConsumers = new ConcurrentHashMap<>();
		mMediaCapturer = new MediaCapturer();
		mJoined = false;
	}

	/**
	 * Join remote room
	 * @throws Exception Login Room Failed
	 */
	public void join()
	throws Exception {
		// Check if the device is loaded
		if (!mDevice.isLoaded()) {
			throw new IllegalStateException("Device is not loaded");
		}

		// User is already joined so return
		if (mJoined) {
			Log.w(TAG, "join() room already joined");
			return;
		}

		Request.sendLoginRoomRequest(mSocket, mRoomId, mDevice.getRtpCapabilities());
		mJoined = true;
		Log.d(TAG, "join() room joined");
	}

	/**
	 * Create local send transport
	 * @throws Exception create transport request failed
	 */
	public void createSendTransport()
	throws Exception {
		// Do nothing if send transport is already created
		if (mSendTransport != null) {
			Log.w(TAG, "createSendTransport() send transport is already created..");
			return;
		}

		createWebRtcTransport("send");
	}

	/**
	 * Create local recv Transport
	 * @throws Exception create transport request failed
	 */
	public void createRecvTransport()
	throws Exception {
		// Do nothing if recv transport is already created
		if (mRecvTransport != null) {
			Log.w(TAG, "createRecvTransport() recv transport is already created..");
			return;
		}

		createWebRtcTransport("recv");
	}

	/**
	 * Start producing video
	 * @param context Context
	 * @param localVideoView Local Video View
	 * @param eglContext EGLContext
	 * @return VideoTrack
	 * @throws Exception Video produce failed
	 */
	public VideoTrack produceVideo(Context context, SurfaceViewRenderer localVideoView, EglBase.Context eglContext)
	throws Exception {
		if (mSendTransport == null) {
			throw new IllegalStateException("Send Transport not created");
		}

		if (!mDevice.canProduce("video")) {
			throw new IllegalStateException("Device cannot produce video");
		}

		mMediaCapturer.initCamera(context);
		VideoTrack videoTrack = mMediaCapturer.createVideoTrack(context, localVideoView, eglContext);
		createProducer(videoTrack);
		Log.d(TAG, "produceVideo() video produce initialized");

		return videoTrack;
	}

	/**
	 * Start producing Audio
	 */
	public void produceAudio() {
		if (mSendTransport == null) {
			throw new IllegalStateException("Send Transport not created");
		}

		if (!mDevice.canProduce("audio")) {
			throw new IllegalStateException("Device cannot produce audio");
		}

		createProducer(mMediaCapturer.createAudioTrack());
		Log.d(TAG, "produceAudio() audio produce initialized");
	}

	/**
	 * Start consuming remote consumer
	 * @param consumerInfo Consumer Info
	 * @return Consumer
	 * @throws JSONException Failed to parse consumer info
	 */
	public Consumer consumeTrack(JSONObject consumerInfo)
	throws JSONException  {
		if (mRecvTransport == null) {
			throw new IllegalStateException("Recv Transport not created");
		}

		String id = consumerInfo.getString("id");
		String producerId = consumerInfo.getString("producerId");
		String kind = consumerInfo.getString("kind");
		String rtpParameters = consumerInfo.getJSONObject("rtpParameters").toString();

		final Consumer.Listener listener = consumer -> Log.d(TAG, "consumer::onTransportClose");

		Consumer kindConsumer = mRecvTransport.consume(listener, id, producerId, kind, rtpParameters);
		mConsumers.put(kindConsumer.getId(), kindConsumer);
		Log.d(TAG, "consumerTrack() consuming id=" + kindConsumer.getId());

		return kindConsumer;
	}

	/**
	 * Create local WebRtcTransport
	 * @param direction send/recv
	 * @throws Exception Create transport request failed
	 */
	private void createWebRtcTransport(String direction)
	throws Exception {
		JSONObject createWebRtcTransportResponse = Request.sendCreateWebRtcTransportRequest(mSocket, mRoomId, direction);
		JSONObject webRtcTransportData = createWebRtcTransportResponse.getJSONObject("webRtcTransportData");

		String id = webRtcTransportData.getString("id");
		String iceParametersString = webRtcTransportData.getJSONObject("iceParameters").toString();
		String iceCandidatesArrayString = webRtcTransportData.getJSONArray("iceCandidates").toString();
		String dtlsParametersString = webRtcTransportData.getJSONObject("dtlsParameters").toString();

		switch(direction) {
			case "send":
				createLocalWebRtcSendTransport(id, iceParametersString, iceCandidatesArrayString, dtlsParametersString);
				break;
			case "recv":
				createLocalWebRtcRecvTransport(id, iceParametersString, iceCandidatesArrayString, dtlsParametersString);
				break;
			default: throw new IllegalStateException("Invalid Direction");
		}
	}

	/**
	 * Create local send WebRtcTransport
	 */
	private void createLocalWebRtcSendTransport(String id, String remoteIceParameters, String remoteIceCandidatesArray, String remoteDtlsParameters) {
		final SendTransport.Listener listener = new SendTransport.Listener() {
			@Override
			public void onConnect(Transport transport, String dtlsParameters) {
				Log.d(TAG, "sendTransport::onConnect");
				handleLocalTransportConnectEvent(transport, dtlsParameters);
			}

			@Override
			public String onProduce(Transport transport, String kind, String rtpParameters, String s2) {
				Log.d(TAG, "sendTransport::onProduce kind=" + kind);
				return handleLocalTransportProduceEvent(transport, kind, rtpParameters, s2);
			}

			@Override
			public void onConnectionStateChange(Transport transport, String newState) {
				Log.d(TAG, "sendTransport::onConnectionStateChange newState=" + newState);
			}
		};

		mSendTransport = mDevice.createSendTransport(listener, id, remoteIceParameters, remoteIceCandidatesArray, remoteDtlsParameters);
		Log.d(TAG, "Send Transport Created id=" + mSendTransport.getId());
	}

	/**
	 * Create local recv WebRtcTransport
	 */
	private void createLocalWebRtcRecvTransport(String id, String remoteIceParameters, String remoteIceCandidatesArray, String remoteDtlsParameters) {
		final RecvTransport.Listener listener = new RecvTransport.Listener() {
			@Override
			public void onConnect(Transport transport, String dtlsParameters) {
				Log.d(TAG, "recvTransport::onConnect");
				handleLocalTransportConnectEvent(transport, dtlsParameters);
			}

			@Override
			public void onConnectionStateChange(Transport transport, String newState) {
				Log.d(TAG, "recvTransport::onConnectionStateChange newState=" + newState);
			}
		};

		mRecvTransport = mDevice.createRecvTransport(listener, id, remoteIceParameters, remoteIceCandidatesArray, remoteDtlsParameters);
		Log.d(TAG, "Recv Transport Created id=" + mRecvTransport.getId());
	}

	/**
	 * Handle local Transport connect event
	 */
	private void handleLocalTransportConnectEvent(Transport transport, String dtlsParameters) {
		try {
			Request.sendConnectWebRtcTransportRequest(mSocket, mRoomId, transport.getId(), dtlsParameters);
		} catch (Exception e) {
			Log.e(TAG, "transport::onConnect failed", e);
		}
	}

	/**
	 * Handle local Transport produce event
	 */
	private String handleLocalTransportProduceEvent(Transport transport, String kind, String rtpParameters, String s2) {
		try {
			JSONObject transportProduceResponse = Request.sendProduceWebRtcTransportRequest(mSocket, mRoomId, transport.getId(), kind, rtpParameters);
			return transportProduceResponse.getString("producerId");
		} catch (Exception e) {
			Log.e(TAG, "transport::onProduce failed", e);
			return null;
		}
	}

	/**
	 * Create local Producer
	 */
	private void createProducer(MediaStreamTrack track) {
		final Producer.Listener listener = producer -> Log.d(TAG, "producer::onTransportClose kind=" + track.kind());

		Producer kindProducer = mSendTransport.produce(listener, track, null, null);
		mProducers.put(kindProducer.getId(), kindProducer);
		Log.d(TAG, "createProducer created id=" + kindProducer.getId() + " kind=" + kindProducer.getKind());
	}
}

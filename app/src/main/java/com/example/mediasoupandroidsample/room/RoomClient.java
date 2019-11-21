package com.example.mediasoupandroidsample.room;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
import org.webrtc.RTCUtils;
import org.webrtc.RtpParameters;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mediasoup room client
 */
public class RoomClient {
	private static final int STATS_INTERVAL_MS = 3000;
	private static final String TAG = "RoomClient";

	private final EchoSocket mSocket;
	private final String mRoomId;
	private final MediaCapturer mMediaCapturer;
	private final ConcurrentHashMap<String, Producer> mProducers;
	private final ConcurrentHashMap<String, Consumer> mConsumers;
	private final List<JSONObject> mConsumersInfo;
	private final Device mDevice;
	private final RoomListener mListener;

	private Boolean mJoined;
	private SendTransport mSendTransport;
	private RecvTransport mRecvTransport;

	public RoomClient(EchoSocket socket, Device device, String roomId, RoomListener listener) {
		mSocket = socket;
		mRoomId = roomId;
		mDevice = device;
		mProducers = new ConcurrentHashMap<>();
		mConsumers = new ConcurrentHashMap<>();
		mMediaCapturer = new MediaCapturer();
		mConsumersInfo = new ArrayList<>();
		mListener = listener;
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

		String codecOptions = "[{\"videoGoogleStartBitrate\":1000}]";
		List<RtpParameters.Encoding> encodings = new ArrayList<>();
		encodings.add(RTCUtils.genRtpEncodingParameters(false, 500000, 0, 60, 0, 0.0d, 0L));
		encodings.add(RTCUtils.genRtpEncodingParameters(false, 1000000, 0, 60, 0, 0.0d, 0L));
		encodings.add(RTCUtils.genRtpEncodingParameters(false, 1500000, 0, 60, 0, 0.0d, 0L));
		createProducer(videoTrack, codecOptions, encodings);
		Log.d(TAG, "produceVideo() video produce initialized");

		return videoTrack;
	}

	/**
	 * Pause local video
	 * @throws JSONException JSON error
	 */
	public void pauseLocalVideo()
	throws JSONException {
		Producer videoProducer = getProducerByKind("video");
		Request.sendPauseProducerRequest(mSocket, mRoomId, videoProducer.getId());
	}

	/**
	 * Resume local video
	 * @throws JSONException JSON error
	 */
	public void resumeLocalVideo()
	throws JSONException {
		Producer videoProducer = getProducerByKind("video");
		Request.sendResumeProducerRequest(mSocket, mRoomId, videoProducer.getId());
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

		String codecOptions = "[{\"opusStereo\":true},{\"opusDtx\":true}]";
		createProducer(mMediaCapturer.createAudioTrack(), codecOptions, null);
		Log.d(TAG, "produceAudio() audio produce initialized");
	}

	/**
	 * Pause local audio
	 * @throws JSONException JSON error
	 */
	public void pauseLocalAudio()
	throws JSONException {
		Producer audioProducer = getProducerByKind("audio");
		Request.sendPauseProducerRequest(mSocket, mRoomId, audioProducer.getId());
	}

	/**
	 * Resume local audio
	 * @throws JSONException JSON error
	 */
	public void resumeLocalAudio()
	throws JSONException {
		Producer audioProducer = getProducerByKind("audio");
		Request.sendResumeProducerRequest(mSocket, mRoomId, audioProducer.getId());
	}

	/**
	 * Start consuming remote consumer
	 * @param consumerInfo Consumer Info
	 * @throws JSONException Failed to parse consumer info
	 */
	public void consumeTrack(JSONObject consumerInfo)
	throws JSONException  {
		if (mRecvTransport == null) {
			// User has not yet created a transport for receiving so temporarily store it
			// and play it when the recv transport is created
			//mConsumersInfo.add(consumerInfo);
			mConsumersInfo.add(consumerInfo);
			return;
		}

		final String kind = consumerInfo.getString("kind");
		// If already consuming type of track remove it, TODO: support multiple remotes?
		for (Consumer consumer : mConsumers.values()) {
			if (consumer.getKind().equals(kind)) {
				Log.d(TAG, "Removing previous consumer of kind " + consumer.getKind());
				mConsumers.remove(consumer.getId());
			}
		}

		final String id = consumerInfo.getString("id");
		final String producerId = consumerInfo.getString("producerId");
		final String rtpParameters = consumerInfo.getJSONObject("rtpParameters").toString();

		final Consumer.Listener listener = consumer -> Log.d(TAG, "consumer::onTransportClose");

		Consumer kindConsumer = mRecvTransport.consume(listener, id, producerId, kind, rtpParameters);
		mConsumers.put(kindConsumer.getId(), kindConsumer);
		Log.d(TAG, "consumerTrack() consuming id=" + kindConsumer.getId());
		mListener.onNewConsumer(kindConsumer);

		// Consumer RTC Stats
		final Handler consumerStatsHandler = new Handler(Looper.getMainLooper());
		final Runnable consumerStatsRunnable = new Runnable() {
			@Override
			public void run() {
				try {
					Request.sendRTCStatsReport(mSocket, mRoomId, kindConsumer.getStats());
				} catch (Exception e) {
					Log.e(TAG, "Failed to get consumer stats", e);
				} finally {
					consumerStatsHandler.postDelayed(this,STATS_INTERVAL_MS);
				}
			}
		};

		consumerStatsHandler.post(consumerStatsRunnable);
	}

	/**
	 * Pause remote video
	 * @throws JSONException JSON error
	 */
	public void pauseRemoteVideo()
	throws JSONException {
		Consumer videoConsumer = getConsumerByKind("video");
		Request.sendPauseConsumerRequest(mSocket, mRoomId, videoConsumer.getId());
	}

	/**
	 * Resume remote video
	 * @throws JSONException JSON error
	 */
	public void resumeRemoteVideo()
	throws JSONException {
		Consumer videoConsumer = getConsumerByKind("video");
		Request.sendResumeConsumerRequest(mSocket, mRoomId, videoConsumer.getId());
	}

	/**
	 * Pause remote audio
	 * @throws JSONException JSON error
	 */
	public void pauseRemoteAudio()
	throws JSONException {
		Consumer audioConsumer = getConsumerByKind("audio");
		Request.sendPauseConsumerRequest(mSocket, mRoomId, audioConsumer.getId());
	}

	/**
	 * Resume remote audio
	 * @throws JSONException JSON error
	 */
	public void resumeRemoteAudio()
	throws JSONException {
		Consumer audioConsumer = getConsumerByKind("audio");
		Request.sendResumeConsumerRequest(mSocket, mRoomId, audioConsumer.getId());
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
	private void createLocalWebRtcRecvTransport(String id, String remoteIceParameters, String remoteIceCandidatesArray, String remoteDtlsParameters)
	throws JSONException {
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

		// Recv Transport created, consume any pending consumers
		for (JSONObject consumerInfo : mConsumersInfo) {
			consumeTrack(consumerInfo);
		}
	}

	/**
	 * Handle local Transport connect event
	 */
	private void handleLocalTransportConnectEvent(Transport transport, String dtlsParameters) {
		try {
			Request.sendConnectWebRtcTransportRequest(mSocket, mRoomId, transport.getId(), dtlsParameters);

			// Transport Stats
			final Handler transportStatsHandler = new Handler(Looper.getMainLooper());
			final Runnable transportStatsRunnable = new Runnable() {
				@Override
				public void run() {
					try {
						Request.sendRTCStatsReport(mSocket, mRoomId, transport.getStats());
					} catch (Exception e) {
						Log.e(TAG, "Failed to get transport stats");
					} finally {
						transportStatsHandler.postDelayed(this, STATS_INTERVAL_MS);
					}
				}
			};

			transportStatsHandler.post(transportStatsRunnable);
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
	private void createProducer(MediaStreamTrack track, String codecOptions, List<RtpParameters.Encoding> encodings) {
		final Producer.Listener listener = producer -> Log.d(TAG, "producer::onTransportClose kind=" + track.kind());

		Producer kindProducer = mSendTransport.produce(listener, track, encodings, codecOptions);
		mProducers.put(kindProducer.getId(), kindProducer);
		Log.d(TAG, "createProducer created id=" + kindProducer.getId() + " kind=" + kindProducer.getKind());

		// Periodically get RTC Stats
		final Handler producerStatsHandler = new Handler(Looper.getMainLooper());
		final Runnable producerStatsRunnable = new Runnable() {
			@Override
			public void run() {
				try {
					Request.sendRTCStatsReport(mSocket, mRoomId, kindProducer.getStats());
				} catch (Exception e) {
					Log.e(TAG, "Failed to get producer stats", e);
				} finally {
					if (!kindProducer.isClosed()) {
						producerStatsHandler.postDelayed(this, STATS_INTERVAL_MS);
					}
				}
			}
		};

		Log.d(TAG, "Producer stats start");
		producerStatsHandler.post(producerStatsRunnable);
	}

	/**
	 * @param kind Producer kind
	 * @return Producer by kind
	 */
	private Producer getProducerByKind(String kind) {
		for (Producer producer : mProducers.values()) {
			if (producer.getKind().equals(kind)) {
				return producer;
			}
		}

		throw new IllegalStateException("No " + kind + " Producer");
	}

	private Consumer getConsumerByKind(String kind) {
		for (Consumer consumer : mConsumers.values()) {
			if (consumer.getKind().equals(kind)) {
				return consumer;
			}
		}

		throw new IllegalStateException("No " + kind + " Consumer");
	}
}

package com.example.mediasoupandroidsample;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import com.example.mediasoupandroidsample.socket.ActionEvent;
import com.example.mediasoupandroidsample.socket.EchoSocket;
import com.example.mediasoupandroidsample.socket.MessageObserver;

import org.json.JSONArray;
import org.mediasoup.droid.Consumer;
import org.mediasoup.droid.Device;
import org.mediasoup.droid.Logger;
import org.mediasoup.droid.MediasoupClient;
import org.mediasoup.droid.Producer;

import org.json.JSONException;
import org.json.JSONObject;
import org.mediasoup.droid.RecvTransport;
import org.mediasoup.droid.SendTransport;
import org.mediasoup.droid.Transport;
import org.webrtc.AudioTrack;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements MessageObserver.Observer {
    private static final String TAG = "MainActivity";

    private Device mDevice;
    private SurfaceViewRenderer mVideoView;
    private SurfaceViewRenderer mRemoteVideoView;
    private PermissionFragment mPermissionFragment;
    private SendTransport mSendTransport;
    private RecvTransport mRecvTransport;
    private MediaCapturer mMediaCapturer;
    private EchoSocket mSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Mediasoup
        mVideoView = findViewById(R.id.local_video_view);
        mRemoteVideoView = findViewById(R.id.remote_video_view);

        addPermissionFragment();
        // FIX: race problem, asking for permissions before fragment is full attached..
        getSupportFragmentManager().executePendingTransactions();

        // Connect to the websocket server
        this.connectWebSocket();
    }

    private void connectWebSocket() {
        mSocket = new EchoSocket();
        mSocket.register(this);

        try {
            // Connect to server
            mSocket.connect("wss://192.168.60.99:443").get(3000, TimeUnit.SECONDS);

            // Initialize mediasoup client
            initializeMediasoupClient();

            // Get router rtp capabilities
            JSONObject request = new JSONObject();
            request.put("action", "getRoomRtpCapabilities");
            request.put("roomId", "android");
            JSONObject response = mSocket.sendWithFuture(request).get(3000, TimeUnit.SECONDS);
            Log.d(TAG, "Got router rtp response " + response);
            JSONObject roomRtpCapabilities = response.getJSONObject("roomRtpCapabilities");

            // Initialize mediasoup device
            mDevice = new Device();
            mDevice.load(roomRtpCapabilities.toString());
            Log.d(TAG, "Device loaded" + mDevice.isLoaded());
            Log.d(TAG, "Device parameters = " + mDevice.GetRtpCapabilities());

            // Join the room
            JSONObject loginRequest = new JSONObject();
            loginRequest.put("action", "loginRoom");
            loginRequest.put("roomId", "android");
            loginRequest.put("rtpCapabilities", new JSONObject(mDevice.GetRtpCapabilities()));

            JSONObject loginResponse = mSocket.sendWithFuture(loginRequest).get(3000, TimeUnit.SECONDS);
            Log.d(TAG, "loginResponse " + loginResponse);

            // Create Send WebRtcTransport
            JSONObject createSendWebRtcTransportRequest = new JSONObject();
            createSendWebRtcTransportRequest.put("roomId", "android");
            createSendWebRtcTransportRequest.put("action", "createWebRtcTransport");
            createSendWebRtcTransportRequest.put("direction", "send");

            JSONObject createSendWebRtcTransportResponse = mSocket.sendWithFuture(createSendWebRtcTransportRequest).get(3000, TimeUnit.SECONDS);
            Log.d(TAG, "createSendWebRtcTransportResponse " + createSendWebRtcTransportResponse);

            // Create local send webRtc transport
            createLocalWebRtcSendTransport(createSendWebRtcTransportResponse.getJSONObject("webRtcTransportData"));

            // Create Recv WebRtcTransport
	        JSONObject createRecvWebRtcTransportRequest = new JSONObject();
	        createRecvWebRtcTransportRequest.put("action", "createWebRtcTransport");
	        createRecvWebRtcTransportRequest.put("roomId", "android");
	        createRecvWebRtcTransportRequest.put("direction", "recv");

	        JSONObject createRecvWebRtcTransportResponse = mSocket.sendWithFuture(createRecvWebRtcTransportRequest).get(3000, TimeUnit.SECONDS);
	        Log.d(TAG, "createRecvWebRtcTransportResponse " + createRecvWebRtcTransportResponse);

	        // Create local recv webRtcTransport
	        createLocalWebRtcRecvTransport(createRecvWebRtcTransportResponse.getJSONObject("webRtcTransportData"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect to socket server error=", e);
        }
    }

    private void initializeMediasoupClient() {
        MediasoupClient.initialize(getApplicationContext());
        Log.d(TAG, "Mediasoup client initialized");

        // Set mediasoup log
        Logger.setLogLevel(Logger.LogLevel.LOG_TRACE);
        Logger.setDefaultHandler();
    }

    private void createLocalWebRtcSendTransport(JSONObject webRtcTransportData)
    throws JSONException {
        String id = webRtcTransportData.getString("id");
        String iceParameters = webRtcTransportData.getJSONObject("iceParameters").toString();
        JSONArray iceCandidatesArray = webRtcTransportData.getJSONArray("iceCandidates");
        String dtlsParametersString = webRtcTransportData.getJSONObject("dtlsParameters").toString();

        final SendTransport.Listener listener = new SendTransport.Listener() {
            @Override
            public String onProduce(Transport transport, String kind, String rtpParameters, String s2) {
                return handleSendTransportProduceEvent(transport, kind, rtpParameters, s2);
            }

            @Override
            public void onConnect(Transport transport, String dtlsParameters) {
                handleLocalTransportConnectEvent(transport, dtlsParameters, "send");
            }

            @Override
            public void onConnectionStateChange(Transport transport, String s) {
                Log.d(TAG, "sendTransport::onConnectionStateChange newState=" + s);
            }
        };

        mSendTransport = mDevice.createSendTransport(listener, id, iceParameters, iceCandidatesArray.toString(), dtlsParametersString);
        Log.d(TAG, "sendTransport created " + mSendTransport.getId());

        displayLocalVideo();
    }

    private void createLocalWebRtcRecvTransport (JSONObject webRtcTransportData)
    throws JSONException {
	    String id = webRtcTransportData.getString("id");
	    String iceParameters = webRtcTransportData.getJSONObject("iceParameters").toString();
	    JSONArray iceCandidatesArray = webRtcTransportData.getJSONArray("iceCandidates");
	    String dtlsParametersString = webRtcTransportData.getJSONObject("dtlsParameters").toString();

	    final RecvTransport.Listener listener = new RecvTransport.Listener() {
		    @Override
		    public void onConnect(Transport transport, String dtlsParameters) {
		    	Log.d(TAG, "recvTransport::onConnect");
		    	runOnUiThread(() -> {
				    // Below wont work unless ui thread?
				    handleLocalTransportConnectEvent(transport, dtlsParameters, "recv");
			    });
		    }

		    @Override
		    public void onConnectionStateChange(Transport transport, String s) {
			    Log.d(TAG, "recvTransport::onConnectionStateChange state=" + s);
		    }
	    };

	    mRecvTransport = mDevice.createRecvTransport(listener, id, iceParameters, iceCandidatesArray.toString(), dtlsParametersString);
	    Log.d(TAG, "RecvTransport created id=" + mRecvTransport.getId());
    }

    private void handleLocalTransportConnectEvent(Transport transport, String dtlsParameters, String direction) {
	    Log.d(TAG, "handleLocalTransportConnectEvent dtlsParameters=" + dtlsParameters);

	    try {
		    JSONObject connectTransportRequest = new JSONObject();
		    connectTransportRequest.put("action", "connectWebRtcTransport");
		    connectTransportRequest.put("roomId", "android");
		    connectTransportRequest.put("transportId", transport.getId());
		    connectTransportRequest.put("dtlsParameters", new JSONObject(dtlsParameters));
			Log.d(TAG, "connectRequest " + connectTransportRequest);

		    try {
			    JSONObject connectTransportResponse = mSocket.sendWithFuture(connectTransportRequest).get(3000, TimeUnit.SECONDS);
			    Log.d(TAG, "connect webrtc transport response" + connectTransportResponse);
		    } catch (Exception e) {
			    Log.e(TAG, "Failed to connect local transport with remote", e);
		    }
	    } catch (JSONException je) {
		    Log.e(TAG, "Failed to send connect transport request", je);
	    }
    }

    private String handleSendTransportProduceEvent(Transport transport, String kind, String rtpParameters, String s2) {
	    Log.d(TAG, "handleSendTransportProduceEvent kind=" + kind + " rtpParameters=" + rtpParameters + " s2=" + s2);

	    try {
		    JSONObject produceRequest = new JSONObject();
		    produceRequest.put("action", "produce");
		    produceRequest.put("roomId", "android");
		    produceRequest.put("transportId", transport.getId());
		    produceRequest.put("kind", kind);
		    produceRequest.put("rtpParameters", new JSONObject(rtpParameters));

		    JSONObject produceResponse = mSocket.sendWithFuture(produceRequest).get(3000, TimeUnit.SECONDS);
		    Log.d(TAG, "Got produce response " + produceResponse);

		    return produceResponse.getString("producerId");
	    } catch (JSONException je) {
		    Log.e(TAG, "Failed to send produce request", je);
		    return null;
	    } catch (Exception e) {
		    Log.e(TAG, "Failed to get producer response", e);
		    return null;
	    }
    }

    private void displayLocalVideo () {
        Log.d(TAG, "displayLocalVideo");

        mPermissionFragment.setPermissionCallback(new PermissionFragment.PermissionCallback() {
            @Override
            public void onPermissionGranted() {
                Log.d(TAG, "Got camera/mic permissions");
                if (mMediaCapturer == null) {
                    PeerConnectionFactory peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();
                    mMediaCapturer = new MediaCapturer(mVideoView, peerConnectionFactory);
                }

                try {
                    EglBase.Context context = mMediaCapturer.initCamera(getBaseContext());
                    runOnUiThread(() -> mVideoView.init(context, null));

                    VideoTrack videoTrack = mMediaCapturer.createVideoTrack(getBaseContext(), context);
                    AudioTrack audioTrack = mMediaCapturer.createAudioTrack();
                    Log.d(TAG, "Got video track");
                    mVideoView.bringToFront();

                    Producer.Listener listener = producer -> Log.d(TAG, "producer::onTransportClose");

                    try {
                        Producer videoProducer = mSendTransport.produce(listener, videoTrack, null, null);
                        Log.d(TAG, "Created video producer id=" + videoProducer.getId());

                        Producer audioProducer = mSendTransport.produce(listener, audioTrack, null, null);
                        Log.d(TAG, "created audio producer id=" + audioProducer.getId());
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to produce", e);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to initialize local stream e=" + e.getLocalizedMessage());
                }
            }

            @Override
            public void onPermissionDenied() {
                Log.w(TAG, "User denied camera/mic permission");
            }
        });

        mPermissionFragment.checkCameraMicPermission();
    }

    private void addPermissionFragment() {
        mPermissionFragment = (PermissionFragment) getSupportFragmentManager().findFragmentByTag(PermissionFragment.TAG);

        if(mPermissionFragment == null) {
            Log.d(TAG, "create headless fragment");
            mPermissionFragment = PermissionFragment.newInstance();
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(mPermissionFragment, PermissionFragment.TAG)
                    .commit();
        }

        Log.d(TAG, "Fragment added");
    }

	@Override
	public void on(@ActionEvent.Event String event, JSONObject data) {
		Log.d(TAG, "Received event " + event);

		try {
			switch(event) {
				case ActionEvent.NEW_USER:
					// data.userId
					Log.d(TAG, "NEW_USER id=" + data.getString("userId"));
					break;
				case ActionEvent.NEW_CONSUMER:
					// data.consumerData.consumerUserId - consumer user id
					// data.consumerData.producerUserId - producer user id
					// data.consumerData.producerId - producer id
					// data.consumerData.id // consumer id
					// data.consumerData.kind // consumer kind
					// data.consumerData.rtpParameters // consumer rtpParameters
					// data.consumerData.type // consumer type
					// data.consumerData.producerPaused // producer paused status
					Log.d(TAG, "NEW_CONSUMER data=" + data);
					handleNewConsumerEvent(data.getJSONObject("consumerData"));
					break;
			}
		} catch (JSONException je) {
			Log.e(TAG, "Failed to handle event", je);
		}
	}

	private void handleNewConsumerEvent(JSONObject consumerInfo)
	throws JSONException {
    	if (mRecvTransport == null) {
    		Log.w(TAG, "RecvTransport is not created...");
    		return;
	    }

    	String consumerId = consumerInfo.getString("id");
    	String producerId = consumerInfo.getString("producerId");
    	String kind = consumerInfo.getString("kind");
    	JSONObject rtpParameters = consumerInfo.getJSONObject("rtpParameters");

    	final Consumer.Listener listener = consumer -> Log.d(TAG, "Consumer::onTransportClosed");

		Consumer kindConsumer = mRecvTransport.consume(listener, consumerId, producerId, kind, rtpParameters.toString());
		Log.d(TAG, "handleNewConsumerEvent() consumer created id=" + kindConsumer.getId() + " kind=" + kindConsumer.getKind());
	}
}

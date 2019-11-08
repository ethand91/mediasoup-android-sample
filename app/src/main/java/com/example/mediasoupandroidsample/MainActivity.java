package com.example.mediasoupandroidsample;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import com.example.mediasoupandroidsample.socket.EchoSocket;

import org.json.JSONArray;
import org.mediasoup.droid.Device;
import org.mediasoup.droid.Logger;
import org.mediasoup.droid.MediasoupClient;
import org.mediasoup.droid.Producer;

import org.json.JSONException;
import org.json.JSONObject;
import org.mediasoup.droid.SendTransport;
import org.mediasoup.droid.Transport;
import org.webrtc.AudioTrack;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private Device mDevice;
    private SurfaceViewRenderer mVideoView;
    private PermissionFragment mPermissionFragment;
    private SendTransport mSendTransport;
    private MediaCapturer mMediaCapturer;
    private EchoSocket mSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Mediasoup
        mVideoView = findViewById(R.id.local_video_view);

        addPermissionFragment();
        // FIX: race problem, asking for permissions before fragment is full attached..
        getSupportFragmentManager().executePendingTransactions();

        // Connect to the websocket server
        this.connectWebSocket();
    }

    private void connectWebSocket() {
        mSocket = new EchoSocket();

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

            // Join the room
            JSONObject loginRequest = new JSONObject();
            loginRequest.put("action", "loginRoom");
            loginRequest.put("roomId", "android");
            loginRequest.put("rtpCapabilities", mDevice.GetRtpCapabilities());

            JSONObject loginResponse = mSocket.sendWithFuture(loginRequest).get(3000, TimeUnit.SECONDS);
            Log.d(TAG, "loginResponse " + loginResponse);

            // Create WebRtcTransport
            JSONObject createSendWebRtcTransportRequest = new JSONObject();
            createSendWebRtcTransportRequest.put("roomId", "android");
            createSendWebRtcTransportRequest.put("action", "createWebRtcTransport");
            createSendWebRtcTransportRequest.put("direction", "send");

            JSONObject createSendWebRtcTransportResponse = mSocket.sendWithFuture(createSendWebRtcTransportRequest).get(3000, TimeUnit.SECONDS);
            Log.d(TAG, "createSendWebRtcTransportResponse " + createSendWebRtcTransportResponse);

            // Create local send webRtc transport
            createLocalWebRtcSendTransport(createSendWebRtcTransportResponse.getJSONObject("webRtcTransportData"));
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
        String iceCandidates = iceCandidatesArray.getJSONObject(0).toString();
        String dtlsParameters = webRtcTransportData.getJSONObject("dtlsParameters").toString();

        Log.d(TAG, "iceCandidates length " + iceCandidatesArray.length());
        Log.d(TAG, "iceCandidates = " + iceCandidates);
        Log.d(TAG, "iceCandidates array to string" + iceCandidatesArray.toString());

        final SendTransport.Listener listener = new SendTransport.Listener() {
            @Override
            public String onProduce(Transport transport, String kind, String rtpParameters, String s2) {
                Log.d(TAG, "sendTransport:onProduce kind=" + kind + " rtpParameters=" + rtpParameters + " s2=" + s2);

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

            @Override
            public void onConnect(Transport transport, String dtlsParameters) {
                Log.d(TAG, "sendTransport::onConnect s=" + dtlsParameters);

                try {
                    JSONObject connectTransportRequest = new JSONObject();
                    connectTransportRequest.put("action", "connectWebRtcTransport");
                    connectTransportRequest.put("roomId", "android");
                    connectTransportRequest.put("transportId", transport.getId());
                    connectTransportRequest.put("dtlsParameters", new JSONObject(dtlsParameters));

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

            @Override
            public void onConnectionStateChange(Transport transport, String s) {
                Log.d(TAG, "sendTransport::onConnectionStateChange newState=" + s);
            }
        };

        mSendTransport = mDevice.createSendTransport(listener, id, iceParameters, iceCandidatesArray.toString(), dtlsParameters);
        Log.d(TAG, "sendTransport created " + mSendTransport.getId());

        displayLocalVideo();
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
}

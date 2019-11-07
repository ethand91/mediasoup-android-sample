package com.example.mediasoupandroidsample;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

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
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCUtils;
import org.webrtc.RtpParameters;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private Device mDevice;
    private WebSocket mSocket;
    private SurfaceViewRenderer mVideoView;
    private PermissionFragment mPermissionFragment;
    private SendTransport mSendTransport;
    private MediaCapturer mMediaCapturer;
    private ProduceResponse mProducerResponse;
    private String mProducerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Mediasoup
        mVideoView = findViewById(R.id.local_video_view);

        //mDevice = new Device();
        Log.d(TAG, "mediasoup initialized");
        this.connectWebSocket(this.getApplicationContext());
        addPermissionFragment();
    }

    private void connectWebSocket(final Context context) {
        final EchoSocket socket = new EchoSocket(new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
                mSocket = webSocket;
                Log.d(TAG, "WebSocket::onOpen");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        MediasoupClient.initialize(getApplicationContext());
                        Log.d(TAG, "Mediasoup client initialized");

                        // Set mediasoup log
                        Logger.setLogLevel(Logger.LogLevel.LOG_TRACE);
                        Logger.setDefaultHandler();
                    }
                });

                try {
                    // Get Room RTP Capabilitieis
                    JSONObject request = new JSONObject();
                    request.put("action", "getRoomRtpCapabilities");
                    request.put("roomId", "android");
                    Log.d(TAG, "send message request=" + request.toString());

                    webSocket.send(request.toString());
                } catch (JSONException je) {
                    Log.e(TAG, "Failed to send getRoomRtpCapabilities request error=" + je.getLocalizedMessage());
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                super.onClosed(webSocket, code, reason);
                Log.d(TAG, "WebSocket::onClosed code=" + code + " reason=" + reason);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                Log.d(TAG, "WebSocket::onMessage text=" + text);

                try {
                    JSONObject jsonObject = new JSONObject(text);
                    Log.d(TAG, "WebSocket::onMessage parsedJson=" + jsonObject);
                    handleEchoSocketMessage(jsonObject);
                } catch (JSONException je) {
                    Log.e(TAG, "WebSocket::onMessage invalid json error=" + je.getLocalizedMessage());
                } catch (Exception e) {
                    Log.e(TAG, "WebSocket::onMessage failed to handle echo socket message error =" + e.getLocalizedMessage());
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                super.onMessage(webSocket, bytes);
                Log.d(TAG, "WebSocket::onMessage byteString=" + bytes.toString());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                super.onClosing(webSocket, code, reason);
                Log.d(TAG, "WebSocket::onClosing code=" + code + " reason:" + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                Log.e(TAG, "WebSocket::OnFailure error=" + t.getLocalizedMessage());
            }
        });

        try {
            socket.connect("wss://192.168.60.99:443");
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect to socket server error=" + e.getLocalizedMessage());
        }
    }

    private void handleEchoSocketMessage(JSONObject message)
    throws JSONException, Exception {
        String action = message.getString("action");
        String producerId = "";

        switch(action) {
            case "getRoomRtpCapabilities":
                JSONObject roomRtpCapabilities = message.getJSONObject("roomRtpCapabilities");
                Log.d(TAG, "handleEchoSocketMessage() got roomRtpCapabilities " + roomRtpCapabilities);
                mDevice = new Device();
                mDevice.load(roomRtpCapabilities.toString());
                Log.d(TAG, "Device loaded" + mDevice.isLoaded());

                JSONObject loginRequest = new JSONObject();
                loginRequest.put("action", "loginRoom");
                loginRequest.put("roomId", "android");
                loginRequest.put("rtpCapabilities", mDevice.GetRtpCapabilities());

                mSocket.send(loginRequest.toString());
                break;
            case "loginRoom":
                Log.d(TAG, "handleEchoSocketMessage() logged in");

                JSONObject createSendWebRtcTransportRequest = new JSONObject();
                createSendWebRtcTransportRequest.put("roomId", "android");
                createSendWebRtcTransportRequest.put("action", "createWebRtcTransport");
                createSendWebRtcTransportRequest.put("direction", "send");

                mSocket.send(createSendWebRtcTransportRequest.toString());
                break;
            case "createWebRtcTransport":
                Log.d(TAG, "handleEchoSocketMessage() got createWebRtcTransport response message="+ message);
                JSONObject webRtcTransportData = message.getJSONObject("webRtcTransportData");
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
                            produceRequest.put("rtpParameters", rtpParameters);

                            // TODO queue
                            mSocket.send(produceRequest.toString());

                            Log.d(TAG, "Attempt Future");

                            // TODO: fix this..
                            CountDownLatch latch = new CountDownLatch(1);

                            mProducerResponse = new ProduceResponse() {
                                @Override
                                public void onResponse(String producerId) {
                                    mProducerId = producerId;
                                    latch.countDown();
                                }
                            };

                            latch.await();
                            return mProducerId;
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
                            connectTransportRequest.put("dtlsParameters", dtlsParameters);

                            mSocket.send(connectTransportRequest.toString());
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
                break;
            case "connectWebRtcTransport":
                Log.d(TAG, "connectWebRtcTransport!!");
                break;
            case "produce":
                Log.d(TAG, "produce id=" + message.getString("producerId"));

                producerId = message.getString("producerId");
                mProducerResponse.onResponse(producerId);
                break;
            default:
                throw new Exception("Unknown message action " + action);
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
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mVideoView.init(context, null);
                        }
                    });

                    VideoTrack videoTrack = mMediaCapturer.createVideoTrack(getBaseContext(), context);
                    Log.d(TAG, "Got video track");

                    Producer.Listener listener = new Producer.Listener() {
                        @Override
                        public void onTransportClose(Producer producer) {
                            Log.d(TAG, "producer::onTransportClose");
                        }
                    };

                    List<RtpParameters.Encoding> encodingList = new ArrayList<>();
                    encodingList.add(RTCUtils.genRtpEncodingParameters(false, 0, 0, 0, 0, 0.0d, 0L));

                    String codecOptions = "[{\"videoGoogleStartBitrate\":1000}]";
                    try {
                        Producer videoProducer = mSendTransport.produce(listener, videoTrack, null, null);
                        Log.d(TAG, "Created video producer");
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

        mPermissionFragment.checkCameraPermission();
    }

    private void addPermissionFragment() {
        mPermissionFragment = (PermissionFragment) getSupportFragmentManager().findFragmentByTag(PermissionFragment.TAG);

        if(mPermissionFragment == null) {
            mPermissionFragment = PermissionFragment.newInstance();
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(mPermissionFragment, PermissionFragment.TAG)
                    .commit();
        }
    }

    interface ProduceResponse {
        void onResponse(String producerId);
    }
}

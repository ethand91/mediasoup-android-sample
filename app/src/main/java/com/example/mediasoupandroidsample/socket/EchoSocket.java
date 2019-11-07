package com.example.mediasoupandroidsample.socket;

import android.util.Log;

import com.example.mediasoupandroidsample.utils.SelfSignedHttpClient;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class EchoSocket  {
	private static final String TAG = "EchoSocket";

	private OkHttpClient mClient;
	private WebSocketListener mListener;
	private WebSocket mSocket;

	public EchoSocket(WebSocketListener listener) {
		mClient = SelfSignedHttpClient.getSelfSignedHttpClient();
		mListener = listener;
	}

	public void connect (String wsUrl) {
		Log.d(TAG, "connect wsUrl=" + wsUrl);
		if (!wsUrl.startsWith("ws://") && !wsUrl.startsWith("wss://")) {
			throw new RuntimeException("Socket url must start with ws/wss");
		}

		Request request = new Request.Builder().url(wsUrl).build();
		mSocket = mClient.newWebSocket(request, mListener);
		Log.d(TAG, "Connecting webSocket to server");
	}

	public void disconnect () {
		mClient.dispatcher().executorService().shutdown();
		Log.d(TAG, "WebSocket service shutdown");
	}
}

package com.example.mediasoupandroidsample.socket;

import android.util.Log;

import com.example.mediasoupandroidsample.utils.SelfSignedHttpClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Socket that implements OkHttp3 WebSocket
 */
public class EchoSocket extends WebSocketListener implements MessageObserver.Subscriber {
	private static final String TAG = "EchoSocket";

	private final OkHttpClient mClient;
	private final CopyOnWriteArraySet<MessageObserver.Observer> mObservers;
	private CountDownLatch mLatch;
	private final ExecutorService mExecutorService;

	private WebSocket mSocket;

	public EchoSocket() {
		mClient = SelfSignedHttpClient.getSelfSignedHttpClient();
		mObservers = new CopyOnWriteArraySet<>();
		mExecutorService = Executors.newSingleThreadExecutor();
	}

	/**
	 * Connect to the socket server
	 * @param wsUrl ws/wss URL
	 * @return Future<Void>
	 */
	public Future<Void> connect (String wsUrl) {
		Log.d(TAG, "connect wsUrl=" + wsUrl);
		if (!wsUrl.startsWith("ws://") && !wsUrl.startsWith("wss://")) {
			throw new RuntimeException("Socket url must start with ws/wss");
		}

		if(mSocket != null) {
			throw new IllegalStateException("Socket is already defined");
		}

		Request request = new Request.Builder().url(wsUrl).build();
		mSocket = mClient.newWebSocket(request, this);
		Log.d(TAG, "Connecting webSocket to server");

		mLatch = new CountDownLatch(1);

		Callable<Void> callable = () -> {
			MessageObserver.Observer observer = (event, data) -> {
				Log.d(TAG, "GOT EVENT " + event);
				if (event.equals(ActionEvent.OPEN)) {
					mLatch.countDown();
				}
			};

			mObservers.add(observer);

			mLatch.await();
			Log.d(TAG, "Connected remove obs");
			mObservers.remove(observer);
			return null;
		};

		return mExecutorService.submit(callable);
	}

	/**
	 * Send a message to the server, without acknowledgement
	 * @param message JSON message to send
	 */
	public void send (JSONObject message) {
		mSocket.send(message.toString());
	}

	/**
	 * Send a message to the server, with acknowledgement
	 * @param message JSON message to send
	 * @return Acknowledgement response
	 * @throws JSONException Failed to parse message
	 */
	public Future<JSONObject> sendWithFuture (JSONObject message)
	throws JSONException {
		String action = message.getString("action");

		return new AckCall(action).sendAckRequest(message.toString());
	}

	/**
	 * Disconnect socket from server
	 */
	public void disconnect () {
		mClient.dispatcher().executorService().shutdown();
		Log.d(TAG, "WebSocket service shutdown");
	}

	/**
	 * Socket successfully connected to the server
	 * @param webSocket WebSocket
	 * @param response Response
	 */
	@Override
	public void onOpen(WebSocket webSocket, Response response) {
		super.onOpen(webSocket, response);
		notifyObservers(ActionEvent.OPEN, null);
	}

	/**
	 * Got a message from the server, notifies all observers listening
	 * @param webSocket WebSocket
	 * @param text message
	 */
	@Override
	public void onMessage(WebSocket webSocket, String text) {
		super.onMessage(webSocket, text);

		try {
			JSONObject jsonObject = new JSONObject(text);

			String action = jsonObject.getString("action");
			notifyObservers(action, jsonObject);
		} catch (JSONException je) {
			Log.e(TAG, "Failed to handle message", je);
		}
	}

	/**
	 * Socket connection closed
	 * @param webSocket WebSocket
	 * @param code ExitCode
	 * @param reason Reason
	 */
	@Override
	public void onClosed(WebSocket webSocket, int code, String reason) {
		super.onClosed(webSocket, code, reason);
		mSocket = null;
	}

	/**
	 * Socket Error
	 * @param webSocket WebSocket
	 * @param t Throwable
	 * @param response Response
	 */
	@Override
	public void onFailure(WebSocket webSocket, Throwable t, Response response) {
		super.onFailure(webSocket, t, response);
	}

	/**
	 * register an event observer
	 * @param observer Observer
	 */
	@Override
	public void register(MessageObserver.Observer observer) {
		mObservers.add(observer);
	}

	/**
	 * Unregister an event observer
	 * @param observer Observer
	 */
	@Override
	public void unregister(MessageObserver.Observer observer) {
		mObservers.remove(observer);
	}

	/**
	 * Send an event to all listening observers
	 * @param event Event
	 * @param data JSONData
	 */
	@Override
	public void notifyObservers(@ActionEvent.Event String event, JSONObject data) {
		for (final MessageObserver.Observer observer : mObservers) {
			observer.on(event, data);
		}
	}

	/**
	 * Class to listen for Acknowledgement response
	 */
	private class AckCall {
		private final String mEvent;

		private JSONObject mResponse;

		AckCall(@ActionEvent.Event String event) {
			mEvent = event;
		}

		/**
		 * Send request and wait for acknowledgement (action must be the same as response action)
		 * @param message message
		 * @return JSONObject
		 */
		Future<JSONObject> sendAckRequest(String message) {
			mLatch = new CountDownLatch(1);

			Callable<JSONObject> callable = () -> {
				MessageObserver.Observer observer = (event, data) -> {
					if (event.equals(mEvent)) {
						// Acknowledgement received
						mResponse = data;
						mLatch.countDown();
					}
				};

				// Add an observer and send the message
				mObservers.add(observer);
				mSocket.send(message);

				mLatch.await();
				// Got acknowledgement, remove observer and return the response
				mObservers.remove(observer);
				return mResponse;
			};

			return mExecutorService.submit(callable);
		}
	}
}

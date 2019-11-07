package com.example.mediasoupandroidsample.socket;

import org.json.JSONObject;

public interface SocketListener {
	public void onMessage(JSONObject data);
}

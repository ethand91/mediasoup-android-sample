package com.example.mediasoupandroidsample.socket;

import org.json.JSONObject;

/**
 * Socket Event Observer
 */
public interface MessageObserver {
	interface Observer {
		void on(@ActionEvent.Event String event, JSONObject data);
	}

	interface Subscriber {
		void register(Observer observer);
		void unregister(Observer observer);
		void notifyObservers(@ActionEvent.Event String event, JSONObject data);
	}
}

package com.example.mediasoupandroidsample.utils;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

/**
 * Util class to allow self signed certificates (WARNING: DO NOT USE IN PRODUCTION!!)
 */
public class SelfSignedHttpClient {
	public static OkHttpClient getSelfSignedHttpClient() {
		try {
			final TrustManager[] trustManagers = new TrustManager[] {
					new X509TrustManager() {
						@Override
						public void checkClientTrusted(X509Certificate[] chain, String authType) { }

						@Override
						public void checkServerTrusted(X509Certificate[] chain, String authType) { }

						@Override
						public X509Certificate[] getAcceptedIssuers() {
							return new X509Certificate[]{};
						}
					}
			};

			final SSLContext sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, trustManagers, new SecureRandom());

			final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

			OkHttpClient.Builder builder = new OkHttpClient.Builder();
			builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustManagers[0]);
			builder.hostnameVerifier((hostname, session) -> true);

			return builder.build();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}

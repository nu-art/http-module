/*
 * Copyright (c) 2016 to Adam van der Kruk (Zehavi) AKA TacB0sS - Nu-Art
 *
 * Restricted usage under specific license
 *
 */

package com.nu.art.http;

import com.nu.art.belog.BeLogged;
import com.nu.art.belog.Logger;
import com.nu.art.core.generics.Processor;
import com.nu.art.core.interfaces.ILogger;
import com.nu.art.core.tools.ArrayTools;
import com.nu.art.core.utils.PoolQueue;
import com.nu.art.modular.core.Module;
import com.nu.art.modular.core.ModuleManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@SuppressWarnings( {
											 "unused",
											 "WeakerAccess"
									 })
public final class HttpModule
		extends Module {
	//	private Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

	public static final HttpResponseListener EmptyResponseListener = new EmptyResponseListener();

	private static final int ThreadCount = 5;

	private int threadCount = ThreadCount;

	/**
	 * PoolQueue holding the requests to be executed by its thread pool
	 */
	private HttpPoolQueue httpAsyncQueue = new HttpPoolQueue();

	private HttpModule() { }

	public void setThreadCount(int threadCount) {
		this.threadCount = threadCount;
	}

	@Override
	protected void init() {
		httpAsyncQueue.createThreads("Http Thread Pool", threadCount);
	}

	public final void trustAllCertificates() {
		logWarning("Very bad idea... calling this is a debug feature ONLY!!!");
		try {
			// Create a trust manager that does not validate certificate chains
			final TrustManager[] trustAllCerts = new TrustManager[]{
					new X509TrustManager() {
						@Override
						public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
								throws CertificateException {
							// Workaround to silence the lint error... This is a debug feature only!
							int i = 0;
						}

						@Override
						public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
								throws CertificateException {
							// Workaround to silence the lint error... This is a debug feature only!
							int i = 0;
						}

						@Override
						public java.security.cert.X509Certificate[] getAcceptedIssuers() {
							return null;
						}
					}
			};

			HostnameVerifier hostnameVerifier = new HostnameVerifier() {

				@Override
				public boolean verify(String hostname, SSLSession session) {
					// Workaround to silence the lint error... This is a debug feature only!
					return hostname != null;
				}
			};

			setHostnameVerifier(hostnameVerifier);

			// Install the all-trusting trust manager
			final SSLContext sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			// Create an ssl socket factory with our all-trusting manager
			final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

			HttpsURLConnection.setDefaultSSLSocketFactory(sslSocketFactory);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public final void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
		HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
	}

	public static abstract class BaseTransaction
			extends Transaction {

		public BaseTransaction() {
			ModuleManager.ModuleManager.getModule(HttpModule.class).super();
			setBeLogged(ModuleManager.ModuleManager.getModule(BeLogged.class));
		}
	}

	@SuppressWarnings("unused")
	private abstract class Transaction
			extends Logger {

		private class HoopTiming {

			HoopTiming redirectHoop;

			URL finalUrl;

			long connectionInterval;

			long uploadInterval;

			long waitForServerInterval;

			long downloadingInterval;

			long getTotalTime() {
				return getTotalHoopTime() + (redirectHoop == null ? 0 : redirectHoop.getTotalTime());
			}

			long getTotalHoopTime() {
				return connectionInterval + uploadInterval + waitForServerInterval + downloadingInterval;
			}
		}

		private HoopTiming hoop = new HoopTiming();

		protected IHttpRequest createRequest() {
			return new HttpRequestIn();
		}

		protected final <Manager extends Module> Manager getModule(Class<Manager> moduleType) {
			return HttpModule.this.getModule(moduleType);
		}

		protected final <ListenerType> void dispatchModuleEvent(String message, Class<ListenerType> listenerType, Processor<ListenerType> processor) {
			HttpModule.this.dispatchModuleEvent(message, listenerType, processor);
		}

		protected final Throwable createException(HttpResponse httpResponse, String errorBody) {
			if (httpResponse.exception != null)
				return httpResponse.exception;

			return new HttpException(httpResponse, errorBody);
		}
	}

	private class HttpTransaction {

		private HttpRequest request;

		private HttpResponse response;

		private HttpResponseListener responseListener;

		private HoopTiming hoop;

		private HttpTransaction(HttpRequest request, HttpResponseListener responseListener) {
			super();
			this.request = request;
			this.responseListener = responseListener;
		}

		@SuppressWarnings("unchecked")
		private boolean execute() {
			HoopTiming originalHoop = hoop;
			hoop = new HoopTiming(originalHoop);
			hoop.redirectHoop = originalHoop;

			HttpURLConnection connection = null;
			boolean redirect = false;
			try {
				connection = connect();
				request.printRequest(HttpModule.this, hoop);
				postBody(connection, request.inputStream);

				response = waitForResponse(connection);

				if (processRedirect()) {
					return redirect = true;
				}

				if (response.processFailure(connection)) {
					responseListener.onError(response);
					return false;
				}

				processSuccess(connection);
			} catch (Throwable e) {
				logError("+---- Error: ", e);
				responseListener.onError(e);
			} finally {
				response.printResponse(HttpModule.this);
				printTiming(HttpModule.this, hoop, "");
				if (!redirect)
					logInfo("+-------------------------------------------------------------------------+");

				request.close();
				response.close();

				if (connection != null)
					connection.disconnect();
			}
			return false;
		}

		private void processSuccess(HttpURLConnection connection)
				throws IOException {
			long start = System.currentTimeMillis();

			response.processSuccess(connection);
			responseListener.onSuccess(response);

			hoop.downloadingAndProcessingInterval = System.currentTimeMillis() - start;
		}

		final HttpResponse waitForResponse(HttpURLConnection connection)
				throws IOException {
			long start = System.currentTimeMillis();

			response = new HttpResponse();
			response.responseCode = connection.getResponseCode();
			response.headers = new HashMap<>(connection.getHeaderFields());
			String[] keys = ArrayTools.asArray(response.headers.keySet(), String.class);

			for (String key : keys) {
				if (key == null)
					continue;

				List<String> value = response.headers.remove(key);
				List<String> olderValue = response.headers.put(key.toLowerCase(), value);
				if (olderValue != null)
					logWarning("POTENTIAL BUG... SAME HEADER NAME DIFFERENT CASING FOR KEY: " + key);
			}

			hoop.waitForServerInterval = System.currentTimeMillis() - start;
			return response;
		}

		final boolean processRedirect()
				throws IOException {
			if (!request.autoRedirect)
				return false;

			if (response.responseCode < 300 || response.responseCode >= 400)
				return false;

			List<String> locations = response.getHeader("location");
			if (locations.size() > 1)
				throw new IOException("redirect has ambiguous locations... cannot determine which!!");

			if (locations.size() == 0)
				return false;

			String location = locations.get(0);
			if (location.length() == 0)
				return false;

			request.url = location;
			return true;
		}

		private void printTiming(ILogger logger, HoopTiming hoop, String indentation) {
			logger.logDebug("+----" + indentation + " Timing, Url: " + hoop.finalUrl.toString());
			logger.logDebug("+----" + indentation + " Timing, Connection: " + hoop.connectionInterval);
			logger.logDebug("+----" + indentation + " Timing, Uploading: " + hoop.uploadInterval);
			logger.logDebug("+----" + indentation + " Timing, Waiting for response : " + hoop.waitForServerInterval);
			logger.logDebug("+----" + indentation + " Timing, Downloading & Processing: " + hoop.downloadingAndProcessingInterval);
			logger.logInfo("+----" + indentation + " Timing, Total Hoop: " + hoop.getTotalHoopTime());
		}

		final OutputStream postBody(HttpURLConnection connection, InputStream postStream)
				throws IOException {

			if (postStream == null)
				return null;

			long start = System.currentTimeMillis();
			byte[] buffer = new byte[1024];
			int length;
			long cached = 0;
			int uploaded = 0;

			OutputStream outputStream = connection.getOutputStream();
			while ((length = postStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, length);
				cached += length;
				uploaded += length;
				responseListener.onUploadProgress(uploaded, postStream.available());
				if (cached < 1024 * 1024)
					continue;

				outputStream.flush();
				cached = 0;
			}

			outputStream.flush();
			postStream.close();
			hoop.uploadInterval = System.currentTimeMillis() - start;

			return outputStream;
		}

		private HttpURLConnection connect()
				throws IOException {
			long start = System.currentTimeMillis();
			HttpURLConnection connection = request.connect(hoop.finalUrl = request.composeURL());
			hoop.connectionInterval = System.currentTimeMillis() - start;
			return connection;
		}
	}

	private class HttpRequestIn
			extends HttpRequest {

		@Override
		public void execute(HttpResponseListener listener) {
			httpAsyncQueue.addItem(new HttpTransaction(this, listener));
		}
	}

	private class HttpPoolQueue
			extends PoolQueue<HttpTransaction> {

		@Override
		protected void onExecutionError(HttpTransaction item, Throwable e) {
			item.responseListener.onError(e);
		}

		@Override
		protected void executeAction(HttpTransaction transaction) {
			while (transaction.execute())
				;
		}
	}

	static class HoopTiming {

		final int hoopIndex;

		HoopTiming redirectHoop;

		URL finalUrl;

		long connectionInterval;

		long uploadInterval;

		long waitForServerInterval;

		long downloadingAndProcessingInterval;

		public HoopTiming() {
			this(null);
		}

		public HoopTiming(HoopTiming originalHoop) {
			this.redirectHoop = originalHoop;
			if (originalHoop != null)
				hoopIndex = originalHoop.hoopIndex + 1;
			else
				hoopIndex = 0;
		}

		long getTotalTime() {
			return getTotalHoopTime() + (redirectHoop == null ? 0 : redirectHoop.getTotalTime());
		}

		long getTotalHoopTime() {
			return connectionInterval + uploadInterval + waitForServerInterval + downloadingAndProcessingInterval;
		}
	}
}

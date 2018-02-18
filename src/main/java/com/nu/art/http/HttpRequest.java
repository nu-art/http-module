/*
 * Copyright (c) 2016 to Adam van der Kruk (Zehavi) AKA TacB0sS - Nu-Art
 *
 * Restricted usage under specific license
 *
 */

package com.nu.art.http;

import com.nu.art.http.HttpModule.HoopTiming;
import com.nu.art.http.consts.HttpMethod;
import com.nu.art.core.exceptions.runtime.BadImplementationException;
import com.nu.art.core.interfaces.ILogger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Vector;

public abstract class HttpRequest
		implements IHttpRequest {

	// Request
	private HttpMethod method = HttpMethod.Get;

	String tag;

	String url;

	private String bodyAsString;

	private int connectionTimeout = 10000;

	private int readTimeout = 20000;

	private Vector<HttpKeyValue> urlParams = new Vector<>();

	boolean autoRedirect = true;

	InputStream inputStream;

	private Vector<HttpKeyValue> headers = new Vector<>();

	private int requestBodyLength;

	HttpRequest() {
		addHeader("accept-encoding", "gzip");
	}

	public final IHttpRequest setUrl(String url) {
		this.url = url;
		return this;
	}

	public final IHttpRequest setTag(String tag) {
		this.tag = tag;
		return this;
	}

	public final IHttpRequest addUrlPath(String path) {
		this.url += path;
		return this;
	}

	public final IHttpRequest addHeader(String key, String value) {
		HttpKeyValue header = new HttpKeyValue(key, value);
		headers.add(header);
		return this;
	}

	public final IHttpRequest addParameter(String key, String value) {
		HttpKeyValue parameter = new HttpKeyValue(key, value);
		if (urlParams.contains(parameter))
			throw new BadImplementationException("already have a parameter with key: " + key);

		urlParams.add(parameter);
		return this;
	}

	public final IHttpRequest setMethod(HttpMethod method) {
		this.method = method;
		return this;
	}

	public final IHttpRequest setBody(String body) {
		if (body == null)
			return this;

		this.bodyAsString = body;
		setBody(new ByteArrayInputStream(body.getBytes()));
		return this;
	}

	public final IHttpRequest setConnectTimeout(int connectedTimeout) {
		this.connectionTimeout = connectedTimeout;
		return this;
	}

	public final IHttpRequest setReadTimeout(int readTimeout) {
		this.readTimeout = readTimeout;
		return this;
	}

	public IHttpRequest setBody(InputStream bodyAsInputStream) {
		this.inputStream = bodyAsInputStream;
		return this;
	}

	public IHttpRequest followRedirect(boolean followRedirect) {
		this.autoRedirect = followRedirect;
		return this;
	}

	/*
	*
	*
	*
	*
	 */

	private HttpKeyValue[] getParameters() {
		Vector<HttpKeyValue> allParameters = new Vector<>();
		allParameters.addAll(urlParams);
		return allParameters.toArray(new HttpKeyValue[allParameters.size()]);
	}

	final URL composeURL()
			throws IOException {
		String urlPath = url;
		HttpKeyValue[] parameters = getParameters();
		StringBuilder params = new StringBuilder();
		if (parameters.length > 0)
			params.append(urlPath.contains("?") ? "&" : "?");

		for (int i = 0; i < parameters.length; i++) {
			HttpKeyValue parameter = parameters[i];
			params.append(parameter.key).append("=").append(URLEncoder.encode(parameter.value, "utf-8"));
			if (i < parameters.length - 1)
				params.append("&");
		}
		urlPath += params;

		return new URL(urlPath);
	}

	final HttpURLConnection connect(URL url)
			throws IOException {

		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod(method.method);
		connection.setInstanceFollowRedirects(autoRedirect);
		connection.setConnectTimeout(connectionTimeout);
		connection.setReadTimeout(readTimeout);
		connection.setDoOutput(inputStream != null);
		connection.setUseCaches(false);

		if (inputStream != null)
			connection.setFixedLengthStreamingMode(requestBodyLength = inputStream.available());

		for (HttpKeyValue header : headers) {
			if (header.value == null)
				continue;

			connection.addRequestProperty(header.key, header.value);
		}
		connection.connect();
		return connection;
	}

	final void printRequest(ILogger logger, HoopTiming hoop) {
		logger.logInfo("+----------------------------- HTTP REQUEST ------------------------------+");
		logger.logInfo("+-- URL(" + hoop.hoopIndex + "): " + method + " - " + url);
		logger.logDebug("+-- Connection-Timeout: " + connectionTimeout);

		logger.logVerbose("+-- Request Params: ");
		for (HttpKeyValue param : urlParams) {
			logger.logVerbose("+----  " + param.key + ": " + param.value);
		}

		logger.logVerbose("+-- Request Headers: ");
		for (HttpKeyValue header : headers) {
			logger.logVerbose("+----  " + header.key + ": " + header.value);
		}

		if (bodyAsString != null) {
			logger.logVerbose("+-- Body (" + bodyAsString.getBytes().length + "): ");
			logger.logVerbose(bodyAsString);
		} else if (requestBodyLength > 0)
			logger.logVerbose("+-- Body Length: " + requestBodyLength);
	}

	final void close() {
		try {
			if (inputStream != null)
				inputStream.close();
		} catch (IOException ignore) {
		}
	}
}
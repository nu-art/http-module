/*
 * Copyright (c) 2016 to Adam van der Kruk (Zehavi) AKA TacB0sS - Nu-Art
 *
 * Restricted usage under specific license
 *
 */

package com.nu.art.http;

import com.nu.art.core.exceptions.runtime.ImplementationMissingException;
import com.nu.art.core.file.Charsets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@SuppressWarnings("WeakerAccess")
public abstract class HttpResponseListener<ResponseType, ErrorType> {

	protected final Class<ResponseType> responseType;

	protected final Class<ErrorType> errorType;

	protected HttpResponseListener(Class<ResponseType> responseType, Class<ErrorType> errorType) {
		this.responseType = responseType;
		this.errorType = errorType;
	}

	@SuppressWarnings("unchecked")
	final <Type> Type convertToType(Class<Type> type, HttpResponse response)
			throws IOException {
		InputStream inputStream = response.inputStream;
		if (InputStream.class.isAssignableFrom(responseType))
			return (Type) inputStream;

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		int available = inputStream.available();
		int downloaded = 0;

		int length;
		byte[] buffer = new byte[1024];
		while ((length = inputStream.read(buffer)) != -1) {
			bos.write(buffer, 0, length);
			downloaded += length;
			onDownloadProgress(downloaded, available);
		}

		response.responseAsString = bos.toString(Charsets.UTF_8.encoding);
		if (responseType == String.class)
			return (Type) response.responseAsString;

		return deserialize(type, response.responseAsString);
	}

	protected <Type> Type deserialize(Class<Type> type, String responseAsString) {
		throw new ImplementationMissingException("if you got here, you probably meant to override this method!");
	}

	public abstract void onSuccess(HttpResponse httpResponse, ResponseType responseBody);

	public abstract void onError(HttpResponse httpResponse, ErrorType errorBody);

	final void onSuccess(HttpResponse httpResponse)
			throws IOException {
		onSuccess(httpResponse, convertToType(responseType, httpResponse));
	}

	final void onError(HttpResponse httpResponse)
			throws IOException {
		onError(httpResponse, convertToType(errorType, httpResponse));
	}

	protected void onUploadProgress(int uploaded, int available) {

	}

	protected void onDownloadProgress(int downloaded, int available) {

	}
}
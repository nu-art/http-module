package com.nu.art.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.UUID;
import java.util.Vector;

@SuppressWarnings("StringBufferReplaceableByString")
public class MultipartBody {

	public static final String Key_FileName = "FILE_NAME";

	public static final String Key_Name = "ITEM_NAME";

	public static class Multipart {

		final String name;

		final InputStream streamBody;

		final boolean isBinaryFile;

		public Multipart(String name, boolean isBinaryFile, String streamBody) {
			this(name, isBinaryFile, new ByteArrayInputStream(streamBody.getBytes()));
		}

		public Multipart(String name, boolean isBinaryFile, InputStream streamBody) {
			this.name = name;
			this.streamBody = streamBody;
			this.isBinaryFile = isBinaryFile;
		}
	}

	public void setMultipart(IHttpRequest request, Multipart... parts)
		throws IOException {
		String lineEnd = "\r\n";
		String twoHyphens = "--";
		String boundary = UUID.randomUUID().toString();

		StringBuilder multipartStart = new StringBuilder();
		multipartStart.append(lineEnd).append(twoHyphens).append(boundary).append(lineEnd);

		multipartStart.append("Content-Disposition: form-data; name=\"" + Key_Name + "\"" + Key_FileName).append(lineEnd);
		multipartStart.append(lineEnd);

		StringBuilder multipartEnd = new StringBuilder();
		multipartEnd.append(lineEnd).append(twoHyphens).append(boundary).append(twoHyphens).append(lineEnd);

		String start = multipartStart.toString();
		Vector<InputStream> inputStreams = new Vector<>();
		int lengthAvailable = 0;
		for (Multipart part : parts) {
			String replace = start.replace(Key_Name, part.name);
			replace = replace.replace(Key_FileName, part.isBinaryFile ? "; filename=\"" + part.name + "\"" : "");

			byte[] multipartStartBytes = replace.getBytes();
			ByteArrayInputStream is = new ByteArrayInputStream(multipartStartBytes);
			lengthAvailable += multipartStartBytes.length + part.streamBody.available();
			inputStreams.add(is);
			inputStreams.add(part.streamBody);
		}

		byte[] bytes = multipartEnd.toString().getBytes();
		lengthAvailable += bytes.length;
		inputStreams.add(new ByteArrayInputStream(bytes));

		final int finalLengthAvailable = lengthAvailable;
		request.setBody(new SequenceInputStream(inputStreams.elements()) {
			@Override
			public int available()
				throws IOException {
				return finalLengthAvailable;
			}
		}) //
		       .addHeader("Connection", "Keep-Alive") //
		       .addHeader("ENCTYPE", "multipart/form-data") //
		       .addHeader("Content-Type", "multipart/form-data;boundary=" + boundary); //
	}
}
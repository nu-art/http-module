package com.nu.art.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.UUID;
import java.util.Vector;

@SuppressWarnings("StringBufferReplaceableByString")
public class MultipartBody {

	public static class Multipart {

		final String fileName;

		final InputStream streamBody;

		public Multipart(String fileName, String streamBody) {
			this(fileName, new ByteArrayInputStream(streamBody.getBytes()));
		}

		public Multipart(String fileName, InputStream streamBody) {
			this.fileName = fileName;
			this.streamBody = streamBody;
		}
	}

	public void setMultipart(IHttpRequest request, Multipart... parts)
			throws IOException {
		String lineEnd = "\r\n";
		String twoHyphens = "--";
		String boundary = UUID.randomUUID().toString();

		StringBuilder multipartStart = new StringBuilder();
		multipartStart.append(lineEnd).append(twoHyphens).append(boundary).append(lineEnd);
		multipartStart.append("Content-Disposition: form-data; name=\"uploaded_file\";filename=\"REMOTE_FILE_NAME\"").append(lineEnd);
		multipartStart.append(lineEnd);

		StringBuilder multipartEnd = new StringBuilder();
		multipartEnd.append(lineEnd).append(twoHyphens).append(boundary).append(twoHyphens).append(lineEnd);

		String start = multipartStart.toString();
		Vector<InputStream> inputStreams = new Vector<>();
		int lengthAvailable = 0;
		for (Multipart part : parts) {
			byte[] multipartStartBytes = start.replace("REMOTE_FILE_NAME", part.fileName).getBytes();
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



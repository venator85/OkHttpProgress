package me.app.myapplication;

import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import java.io.IOException;
import java.util.Random;

import eu.alessiobianchi.log.Log;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ForwardingSink;
import okio.ForwardingSource;
import okio.Okio;
import okio.Sink;
import okio.Source;

public final class Progress {

	public void run(final long SIZE, final ProgressListener uploadProgress, final ProgressListener downloadProgress) throws Exception {

		RequestBody body = new RequestBody() {
			@Override
			@Nullable
			public MediaType contentType() {
				return MediaType.parse("application/octet-stream");
			}

			@Override
			public long contentLength() {
				return SIZE;
			}

			@Override
			public void writeTo(BufferedSink sink) throws IOException {
				Random r = new Random();
				byte[] buf = new byte[1024];
				for (int i = 0; i < SIZE; i += buf.length) {
					r.nextBytes(buf);
					sink.write(buf);
				}
			}
		};

		Request request = new Request.Builder().post(new ProgressRequestBody(body, uploadProgress))
				.url("http://10.0.2.91:8088/hello").build();

		OkHttpClient client = new OkHttpClient.Builder().addNetworkInterceptor(new Interceptor() {
			@Override
			public Response intercept(Chain chain) throws IOException {
				Response originalResponse = chain.proceed(chain.request());
				return originalResponse.newBuilder()
						.body(new ProgressResponseBody(originalResponse.body(), downloadProgress)).build();
			}
		}).build();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				Log.e("ERROR", e, this);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				try {
					response.body().source().readAll(Okio.blackhole());
				} finally {
					if (response != null) {
						response.close();
					}
				}
			}
		});
	}

	interface ProgressListener {
		@WorkerThread
		void update(long bytesCompleted, long contentLength);
	}

	private static class ProgressResponseBody extends ResponseBody {
		private final ResponseBody responseBody;
		private final ProgressListener progressListener;
		private BufferedSource bufferedSource;

		ProgressResponseBody(ResponseBody responseBody, ProgressListener progressListener) {
			this.responseBody = responseBody;
			this.progressListener = progressListener;
		}

		@Override
		public MediaType contentType() {
			return responseBody.contentType();
		}

		@Override
		public long contentLength() {
			return responseBody.contentLength();
		}

		@Override
		public BufferedSource source() {
			if (bufferedSource == null) {
				bufferedSource = Okio.buffer(source(responseBody.source()));
			}
			return bufferedSource;
		}

		private Source source(Source source) {
			return new ForwardingSource(source) {
				long totalBytesRead;

				@Override
				public long read(Buffer sink, long byteCount) throws IOException {
					long bytesRead = super.read(sink, byteCount);
					// read() returns the number of bytes read, or -1 if this source is exhausted.
					totalBytesRead += bytesRead != -1 ? bytesRead : 0;
					progressListener.update(totalBytesRead, responseBody.contentLength());
					return bytesRead;
				}
			};
		}
	}

	private static class ProgressRequestBody extends RequestBody {
		private final RequestBody requestBody;
		private final ProgressListener progressListener;

		ProgressRequestBody(RequestBody requestBody, ProgressListener progressListener) {
			this.requestBody = requestBody;
			this.progressListener = progressListener;
		}

		@Override
		public MediaType contentType() {
			return requestBody.contentType();
		}

		@Override
		public long contentLength() throws IOException {
			return requestBody.contentLength();
		}

		@Override
		public void writeTo(BufferedSink sink) throws IOException {
			Sink countingSink = new ForwardingSink(sink) {
				long bytesWritten;

				@Override
				public void write(Buffer source, long byteCount) throws IOException {
					super.write(source, byteCount);
					bytesWritten += byteCount;
					progressListener.update(bytesWritten, contentLength());
				}
			};
			BufferedSink bufferedSink = Okio.buffer(countingSink);
			requestBody.writeTo(bufferedSink);
			bufferedSink.flush();
		}
	}

}

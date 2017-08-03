package me.app.myapplication;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ProgressBar;

import java.util.concurrent.TimeUnit;

import eu.alessiobianchi.log.Log;

public class MainActivity extends AppCompatActivity {

	private ProgressBar progressBarUp;
	private ProgressBar progressBarDown;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		progressBarUp = (ProgressBar) findViewById(R.id.progressBarUp);
		progressBarDown = (ProgressBar) findViewById(R.id.progressBarDown);
		progressBarUp.setMax(100);
		progressBarDown.setMax(100);

		final int delay = 200;

		Sampler<State> uploadSampler = new Sampler<State>(delay, TimeUnit.MILLISECONDS) {
			@SuppressLint("DefaultLocale")
			@Override
			protected void onSampledEvent(final State state) {
				long bytesRead = state.bytesRead;
				long contentLength = state.contentLength;

				final float perc = (100f * bytesRead) / contentLength;
				Log.e(String.format("%s progress %d/%d - %f%%", toString(), bytesRead, contentLength, perc), this);

				progressBarUp.post(new Runnable() {
					@Override
					public void run() {
						progressBarUp.setProgress(Math.round(perc));
					}
				});
			}
		};

		Sampler<State> downloadSampler = new Sampler<State>(delay, TimeUnit.MILLISECONDS) {
			@SuppressLint("DefaultLocale")
			@Override
			protected void onSampledEvent(final State state) {
				long bytesRead = state.bytesRead;
				long contentLength = state.contentLength;

				final float perc = (100f * bytesRead) / contentLength;
				Log.e(String.format("%s progress %d/%d - %f%%", toString(), bytesRead, contentLength, perc), this);

				progressBarDown.post(new Runnable() {
					@Override
					public void run() {
						progressBarDown.setProgress(Math.round(perc));
					}
				});
			}
		};

		final Progress.ProgressListener uploadListener = new MyProgressListener(uploadSampler);
		final Progress.ProgressListener downloadListener = new MyProgressListener(downloadSampler);

		findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					progressBarUp.setProgress(0);
					progressBarDown.setProgress(0);
					final long SIZE = 20 * 1024 * 1024;
					new Progress().run(SIZE, uploadListener, downloadListener);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private class MyProgressListener implements Progress.ProgressListener {
		private final Sampler<State> sampler;

		MyProgressListener(Sampler<State> sampler) {
			this.sampler = sampler;
		}

		@Override
		public void update(long bytesRead, long contentLength) {
			State event = new State(bytesRead, contentLength);
			if (event.isDone()) {
				sampler.terminate(event);
			} else {
				sampler.submit(event);
			}
		}
	}

	public static abstract class Sampler<T> {
		private final long delay;
		private long lastTstamp;

		public Sampler(long delay, TimeUnit unit) {
			this.delay = TimeUnit.MILLISECONDS.convert(delay, unit);
		}

		public void submit(T event) {
			final long now = SystemClock.elapsedRealtime();
			if (now - lastTstamp > delay) {
				lastTstamp = now;
				onSampledEvent(event);
			}
		}

		public void terminate(T event) {
			onSampledEvent(event);
		}

		protected abstract void onSampledEvent(T event);
	}

	private static class State {
		public final long bytesRead;
		public final long contentLength;

		public State(long bytesRead, long contentLength) {
			this.bytesRead = bytesRead;
			this.contentLength = contentLength;
		}

		boolean isDone() {
			return bytesRead == contentLength;
		}
	}

}

package com.vaguehope.dlnatoad.media;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class StoringMediaIdCallback implements MediaIdCallback {

	private final CountDownLatch latch = new CountDownLatch(1);
	private volatile String mediaId;
	private volatile IOException exception;

	@Override
	public void onResult(final String id) throws IOException {
		this.mediaId = id;
		this.latch.countDown();
	}

	@Override
	public void onError(final IOException e) {
		this.exception = e;
		this.latch.countDown();
	}

	public String getMediaId() throws IOException {
		try {
			this.latch.await();
		} catch (final InterruptedException e) {
			throw new IOException(e);
		}
		if (this.exception != null) {
			// Wrap exception so it includes the async caller.
			throw new IOException(this.exception);
		}
		return this.mediaId;
	}

}

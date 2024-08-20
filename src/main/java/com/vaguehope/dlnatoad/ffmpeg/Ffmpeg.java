package com.vaguehope.dlnatoad.ffmpeg;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.ffmpeg.ProcessHelper.ProcessException;

import io.prometheus.metrics.core.metrics.Counter;

public class Ffmpeg {

	private static final String FFMPEG = "ffmpeg";
	private static final Logger LOG = LoggerFactory.getLogger(Ffprobe.class);

	private static Boolean isAvailable = null;

	private static final Counter INVOCATIONS_METRIC = Counter.builder()
			.name("ffmpeg_invocations")
			.labelNames("exitcode")
			.help("count of ffmpeg executations.")
			.register();

	public static boolean isAvailable () {
		if (isAvailable == null) {
			try {
				isAvailable = ProcessHelper.runAndWait(FFMPEG, "-version").size() > 0;
			}
			catch (final IOException e) {
				isAvailable = false;
				LOG.warn("{} not found, video thumbnails can not be generated.", FFMPEG);
			}
		}
		return isAvailable.booleanValue();
	}

	private static void checkAvailable() throws IOException {
		if (!isAvailable()) throw new IOException(FFMPEG + " not avilable.");
	}

	public static void generateThumbnail (final File videoFile, final int size, final File thumbnailFile) throws IOException {
		checkAvailable();
		// https://ffmpeg.org/ffmpeg-filters.html#select_002c-aselect
		// https://ffmpeg.org/ffmpeg-filters.html#scale-1
		// https://ffmpeg.org/ffmpeg-utils.html

		try {
			ProcessHelper.runAndWait(thumbCmd(videoFile, thumbnailFile, size, "gt(scene\\,0.5)+gt(n\\,300)"));
			if (!thumbnailFile.exists()) {
				ProcessHelper.runAndWait(thumbCmd(videoFile, thumbnailFile, size, "1"));
			}
			INVOCATIONS_METRIC.labelValues("0").inc();
		}
		catch (final ProcessException e) {
			INVOCATIONS_METRIC.labelValues(String.valueOf(e.getExitCode())).inc();
			throw e;
		}
	}

	private static String[] thumbCmd(final File inF, final File outF, final int size, final String select) {
		return new String[] {
				FFMPEG,
				"-hide_banner",
				"-loglevel", "error",
				"-y",
				"-i", inF.getAbsolutePath(),
				"-vf", "select=" + select + ",scale='if(gt(in_w,in_h)," + size + ",-1)':'if(gt(in_h,in_w)," + size + ",-1)'",
				"-frames:v", "1",
				outF.getAbsolutePath()
		};
	}

}

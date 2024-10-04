package com.vaguehope.dlnatoad.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vaguehope.dlnatoad.fs.MediaFile;

public class ImageResizerTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private ImageResizer undertest;

	@Before
	public void before() throws Exception {
		this.undertest = new ImageResizer();
	}

	@Test
	public void itDecodesImage() throws Exception {
		final File in = this.tmp.newFile();
		try (final InputStream is = ImageResizer.class.getResourceAsStream("/icon.png")) {
			FileUtils.copyInputStreamToFile(is, in);
		}
		final File f = this.tmp.newFile();
		this.undertest.scaleImageToFile(MediaFile.forFile(in), 24, 0.8f, f);
		assertTrue(f.exists());
		assertThat(f.length(), greaterThan(1L));
	}

	@Ignore
	@Test
	public void itDecodesLocalFile() throws Exception {
		final File f = this.tmp.newFile();
		this.undertest.scaleImageToFile(MediaFile.forFile(new File(new File(System.getProperty("user.home")), "Art/test.jpg")), 200, 0.8f, f);
		assertTrue(f.exists());
		assertThat(f.length(), greaterThan(1L));
	}

}

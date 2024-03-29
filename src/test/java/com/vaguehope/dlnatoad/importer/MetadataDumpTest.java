package com.vaguehope.dlnatoad.importer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.math.BigInteger;
import java.util.Arrays;

import org.junit.Test;

import com.vaguehope.dlnatoad.importer.HashAndTags.ImportedTag;

public class MetadataDumpTest {

	@Test
	public void itReadsJsonFile() throws Exception {
		@SuppressWarnings("resource")
		MetadataDump actual = MetadataDump.readInputStream(getClass().getResourceAsStream("/test_metadata_dump.json"));
		assertThat(actual.getHashAndTags(), contains(
				HashAndTags.sha1(new BigInteger("d8e244abc5057409cbbfcb8f44c5120ec2e3f64f", 16), Arrays.asList(
						new ImportedTag("foo", null, 1200000100000L, false),
						new ImportedTag("no mod", null, 0L, false),
						new ImportedTag("bat", null, 1200000300000L, true))),
				HashAndTags.sha1(new BigInteger("722f710e0f039262c87c50b6af87a9d9132feac4", 16), Arrays.asList(
						new ImportedTag("red", null, 1300000100000L, false),
						new ImportedTag("green", null, 1300000200000L, true),
						new ImportedTag("blue", "colour", 1300000300000L, false))),
				HashAndTags.md5(new BigInteger("b8ed9178293e9dcde989f8c0ede65052", 16), Arrays.asList(
						new ImportedTag("this", "here", 0L, false),
						new ImportedTag("that", "there", 0L, false)))
				));
	}

}

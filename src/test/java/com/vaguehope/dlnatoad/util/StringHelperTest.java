package com.vaguehope.dlnatoad.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class StringHelperTest {

	@Test
	public void itUnquotesStrings () throws Exception {
		assertEquals("foo\"bar", StringHelper.unquoteQuotes("\"foo\\\"bar\""));
		assertEquals("foo\"bar", StringHelper.unquoteQuotes("foo\\\"bar\""));
		assertEquals("foo\"bar", StringHelper.unquoteQuotes("\"foo\\\"bar"));
		assertEquals("foo\"bar", StringHelper.unquoteQuotes("foo\\\"bar"));
	}

	@Test
	public void itRemovesLeadingString() throws Exception {
		assertEquals("foobar", StringHelper.removePrefix("/foobar", "/"));
		assertEquals("", StringHelper.removePrefix("/", "/"));
		assertEquals("foobar", StringHelper.removePrefix("c/foobar", "c/"));
		assertEquals("/bar", StringHelper.removePrefix("/foo/bar", "/foo"));

		assertEquals("", StringHelper.removePrefix("", "/"));
		assertEquals(null, StringHelper.removePrefix(null, "/"));
	}

	@Test
	public void itRemovesFirstMatchingPrefix() throws Exception {
		assertEquals("foobar", StringHelper.removeFirstMatchingPrefix("/foobar", "/"));
		assertEquals("", StringHelper.removeFirstMatchingPrefix("/", "/"));
		assertEquals("foobar", StringHelper.removeFirstMatchingPrefix("c/foobar", "c/"));
		assertEquals("/bar", StringHelper.removeFirstMatchingPrefix("/foo/bar", "/foo"));

		assertEquals("", StringHelper.removeFirstMatchingPrefix("", "/"));
		assertEquals(null, StringHelper.removeFirstMatchingPrefix(null, "/"));

		assertEquals("/bar", StringHelper.removeFirstMatchingPrefix("/foo/bar", "/foo", "/bat"));
		assertEquals("/bar", StringHelper.removeFirstMatchingPrefix("/bat/bar", "/foo", "/bat"));
	}

	@Test
	public void itRemovesTrailingString() throws Exception {
		assertEquals("foobar", StringHelper.removeSuffix("foobar/", "/"));
		assertEquals("", StringHelper.removeSuffix("/", "/"));
		assertEquals("foobar", StringHelper.removeSuffix("foobar/c", "/c"));

		assertEquals("", StringHelper.removeSuffix("", "/"));
		assertEquals(null, StringHelper.removeSuffix(null, "/"));
	}

}

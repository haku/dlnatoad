package com.vaguehope.dlnatoad.db.search;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.ImmutableSet;

public class DbSearchSyntax {

	public static String makeSingleTagSearch(String tag) {
		final String quote;
		if (StringUtils.containsAny(tag, ' ', '(', ')', '\t', '　')) {
			if (tag.indexOf('"') >= 0) {
				if (tag.indexOf('\'') >= 0) {
					tag = tag.replace("'", "\\'");
				}
				quote = "'";
			}
			else {
				quote = "\"";
			}
		}
		else {
			quote = "";
		}
		final StringBuilder ret = new StringBuilder();
		ret.append("t=");
		ret.append(quote);
		ret.append(tag);
		ret.append(quote);
		return ret.toString();
	}

	public static boolean isFileMatchPartial (final String term) {
		return term.startsWith("f~") || term.startsWith("F~");
	}

	public static boolean isFileNotMatchPartial (final String term) {
		return term.startsWith("-f~") || term.startsWith("-F~");
	}

	public static boolean isTagMatchPartial (final String term) {
		return term.startsWith("t~") || term.startsWith("T~");
	}

	public static boolean isTagNotMatchPartial (final String term) {
		return term.startsWith("-t~") || term.startsWith("-T~");
	}

	public static boolean isTagMatchExact (final String term) {
		return term.startsWith("t=") || term.startsWith("T=");
	}

	public static boolean isTagNotMatchExact (final String term) {
		return term.startsWith("-t=") || term.startsWith("-T=");
	}

	public static String removeMatchOperator (final String term) {
		int x = term.indexOf('=');
		if (x < 0) x = term.indexOf('~');
		if (x < 0) throw new IllegalArgumentException("term does not contain '=' or '~': " + term);
		return term.substring(x + 1);
	}

	public static boolean isTagCountLessThan (final String term) {
		return term.startsWith("t<") || term.startsWith("T<");
	}

	public static boolean isTagCountGreaterThan (final String term) {
		return term.startsWith("t>") || term.startsWith("T>");
	}

	private static final Set<String> NUMBER_OPERATORS = ImmutableSet.of("=", "<", ">", "<=", ">=");

	// Valid:
	// wWhH = < <= > >=
	public static String widthOrHeight (final String term) {
		final String field;
		if (term.startsWith("w") || term.startsWith("W")) {
			field = "width";
		}
		else if (term.startsWith("h") || term.startsWith("H")) {
			field = "height";
		}
		else {
			return null;
		}

		final String op1 = term.length() >= 2 ? term.substring(1, 2) : null;
		final String op2 = term.length() >= 3 ? term.substring(2, 3) : null;

		if (NUMBER_OPERATORS.contains(op1 + op2)) {
			return field + op1 + op2;
		}
		else if (NUMBER_OPERATORS.contains(op1)) {
			return field + op1;
		}

		return null;
	}

	/**
	 * If input is invalid default value is 1.
	 */
	public static int removeCountOperator(final String term) {
		int x = term.indexOf('<');
		if (x < 0) x = term.indexOf('>');
		if (x < 0) x = term.indexOf('=');
		if (x < 0) throw new IllegalArgumentException("term does not contain '<' or '>' or '=': " + term);

		// support <= and >=
		if (x < term.length() - 1 && term.charAt(x + 1) == '=') x += 1;

		final String s = term.substring(x + 1);
		if (s.length() < 1) return 1;

		try {
			return Integer.parseInt(s);
		}
		catch (NumberFormatException e) {
			return 1;
		}
	}

}

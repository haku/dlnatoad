package com.vaguehope.dlnatoad.db;

import java.util.Comparator;
import java.util.Objects;

public class TagFrequency {

	private final String tag;
	private final int count;

	public TagFrequency(String tag, int count) {
		this.tag = tag;
		this.count = count;
	}

	public String getTag() {
		return this.tag;
	}

	public int getCount() {
		return this.count;
	}

	@Override
	public String toString() {
		return String.format("TagFrequency{%s, %s}", this.tag, this.count);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.tag, this.count);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == null) return false;
		if (this == obj) return true;
		if (!(obj instanceof TagFrequency)) return false;
		final TagFrequency that = (TagFrequency) obj;
		return Objects.equals(this.tag, that.tag)
				&& this.count == that.count;
	}

	public enum Order implements Comparator<TagFrequency> {
		COUNT_DESC {
			@Override
			public int compare(final TagFrequency a, final TagFrequency b) {
				final int c = Integer.compare(b.count, a.count);
				if (c != 0) return c;
				return TAG_ASC.compare(a, b);
			}
		},
		TAG_ASC {
			@Override
			public int compare(final TagFrequency a, final TagFrequency b) {
				return a.tag.compareTo(b.tag);
			}
		}
	}

}

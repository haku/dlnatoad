package com.vaguehope.dlnatoad.db;

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
				&& Objects.equals(this.count, that.count);
	}

}
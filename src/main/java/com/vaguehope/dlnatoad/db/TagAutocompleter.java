package com.vaguehope.dlnatoad.db;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/*
 * Binary search and then step backwards/forwards inspired by:
 * https://stackoverflow.com/questions/9543046/implement-binary-search-with-string-prefix
 */
public class TagAutocompleter {

	private static final int MAX_SUGGESTIONS = 20;
	private static final long START_DELAY_SECONDS = TimeUnit.MINUTES.toSeconds(1);  // Add an extra delay as a fudge factor.
	private static final Logger LOG = LoggerFactory.getLogger(TagAutocompleter.class);

	private final MediaDb db;
	private final ScheduledExecutorService schExSvc;

	private FragmentAndTag[] tagsArr;
	private FragmentAndTag[] fragmentsArr;

	public TagAutocompleter(final MediaDb db, final ScheduledExecutorService schExSvc) {
		this.db = db;
		this.schExSvc = schExSvc;
	}

	public void start() {
		this.schExSvc.submit(() -> {
			// TODO change to scheduleWithFixedDelay() and have a way to check if the DB has changed before running.
			this.schExSvc.schedule(new Worker(), START_DELAY_SECONDS, TimeUnit.SECONDS);
		});
	}

	private class Worker implements Runnable {
		@Override
		public void run() {
			try {
				generateIndex();
			}
			catch (final Exception e) {
				LOG.error("Exception while generating autocomplete index.", e);
			}
		}
	}

	public void addOrIncrementTag(final String tag) {
		this.schExSvc.execute(() -> {
			internalAddOrIncrementTag(tag, 1);
		});
	}

	public void decrementTag(final String tag) {
		this.schExSvc.execute(() -> {
			internalAddOrIncrementTag(tag, -1);
		});
	}

	public List<TagFrequency> suggestTags(final String input) {
		return binarySearch(this.tagsArr, input);
	}

	public List<TagFrequency> suggestFragments(final String input) {
		return binarySearch(this.fragmentsArr, input);
	}

	private static List<TagFrequency> binarySearch(final FragmentAndTag[] idx, final String input) {
		if (idx == null) return Collections.emptyList();

		final FragmentPrefixComp comp = new FragmentPrefixComp(input);
		final int randomIndex = Arrays.binarySearch(idx, new FragmentAndTag(input, "unused", -1), comp);
		if (randomIndex < 0) return Collections.emptyList();

		int rangeStarts = randomIndex;
		int rangeEnds = randomIndex;
		while (rangeStarts > -1
				&& idx[rangeStarts].fragment.toLowerCase().startsWith(input.toLowerCase())) {
			rangeStarts--;
		}
		while (rangeEnds < idx.length
				&& idx[rangeEnds].fragment.toLowerCase().startsWith(input.toLowerCase())) {
			rangeEnds++;
		}
		final FragmentAndTag[] matches = Arrays.copyOfRange(idx, rangeStarts + 1, rangeEnds);
		Arrays.sort(matches, FragmentAndTag.Order.COUNT_DESC);

		// set to remove duplicates, eg: searching "2" will match "123923" as both "23923" and "23" fragments.
		final Set<TagFrequency> ret = new LinkedHashSet<>();
		for (int i = 0; i < matches.length && i < MAX_SUGGESTIONS; i++) {
			final FragmentAndTag f = matches[i];
			ret.add(new TagFrequency(f.tag, f.fileCount));
		}
		return new ArrayList<>(ret);
	}

	private static class FragmentPrefixComp implements Comparator<FragmentAndTag> {
		private final String prefix;

		public FragmentPrefixComp(final String prefix) {
			this.prefix = prefix;
		}

		@Override
		public int compare(final FragmentAndTag o1, final FragmentAndTag o2) {
			final String p1;
			if (o1.fragment.length() < this.prefix.length()) {
				p1 = o1.fragment;
			}
			else {
				p1 = o1.fragment.substring(0, this.prefix.length());
			}
			return p1.compareToIgnoreCase(o2.fragment);
		}
	}

	public void generateIndex() throws SQLException {
		final List<TagFrequency> tags = this.db.getAllTagsNotMissingNotDeleted();
		generateTagsIndex(tags);
		generateFragmentsIndex(tags);
	}

	private void generateTagsIndex(final List<TagFrequency> tags) {
		final List<FragmentAndTag> idx = new ArrayList<>(tags.size());
		for (final TagFrequency tf : tags) {
			idx.add(new FragmentAndTag(tf.getTag(), tf.getTag(), tf.getCount()));
		}
		// Ensure sort is consistent with lookup, sqlite can sort slightly differently sometimes.
		idx.sort(FragmentAndTag.Order.FRAGMENT_ASC);
		LOG.info("Tags index: {}", idx.size());
		this.tagsArr = idx.toArray(new FragmentAndTag[idx.size()]);
	}

	private void generateFragmentsIndex(final List<TagFrequency> tags) {
		final List<FragmentAndTag> fragments = makeFragments(tags);
		LOG.info("Fragments index: {}", fragments.size());

		fragments.sort(FragmentAndTag.Order.FRAGMENT_ASC);
		final Multiset<String> fragmentCounts = HashMultiset.create();
		for (final Iterator<FragmentAndTag> i = fragments.iterator(); i.hasNext();) {
			final FragmentAndTag n = i.next();
			if (fragmentCounts.count(n.fragment) < MAX_SUGGESTIONS) {
				fragmentCounts.add(n.fragment);
			}
			else {
				i.remove();
			}
		}

		LOG.info("Top fragments: {}", fragments.size());
		this.fragmentsArr = fragments.toArray(new FragmentAndTag[fragments.size()]);
	}

	private static List<FragmentAndTag> makeFragments(final List<TagFrequency> allTags) {
		final List<FragmentAndTag> allFragments = new ArrayList<>();
		for (final TagFrequency tag : allTags) {
			allFragments.addAll(makeFragments(tag.getTag(), tag.getCount()));
		}
		return allFragments;
	}

	static List<FragmentAndTag> makeFragments(final String tag, final int fileCount) {
		if (tag.length() < 2) return Collections.emptyList();
		final List<FragmentAndTag> ret = new ArrayList<>(); // TODO would simple array be faster?
		for (int i = 1; i < tag.length(); i++) {
			final String frag = tag.substring(i);
			if (Character.isWhitespace(frag.charAt(0))) continue;
			ret.add(new FragmentAndTag(frag, tag, fileCount));
		}
		return ret;
	}

	private void internalAddOrIncrementTag(final String tag, final int delta) {
		addOrIncrementExactTag(tag, delta);
		addOrIncrementFragments(tag, delta);
	}

	// Only synchronized to stop it interleaving with itself.
	private synchronized void addOrIncrementExactTag(final String tag, final int delta) {
		final FragmentPrefixComp comp = new FragmentPrefixComp(tag);
		final int randomIndex = Arrays.binarySearch(this.tagsArr, new FragmentAndTag(tag, "unused", -1), comp);

		boolean found = false;
		if (randomIndex >= 0) {
			final FragmentAndTag match = this.tagsArr[randomIndex];
			if (tag.equals(match.tag)) {
				this.tagsArr[randomIndex] = match.changeCount(delta);
				found = true;
			}
		}

		if (!found && delta > 0) {
			final int newIndex = randomIndex >= 0 ? randomIndex : 0 - randomIndex - 1;
			final FragmentAndTag[] newTagsArr = new FragmentAndTag[this.tagsArr.length + 1];
			System.arraycopy(this.tagsArr, 0, newTagsArr, 0, newIndex);
			System.arraycopy(this.tagsArr, newIndex, newTagsArr, newIndex + 1, this.tagsArr.length - newIndex);
			newTagsArr[newIndex] = new FragmentAndTag(tag, tag, delta);
			this.tagsArr = newTagsArr;
		}
	}

	// Only synchronized to stop it interleaving with itself.
	private synchronized void addOrIncrementFragments(final String tag, final int delta) {
		final List<String> fragments = new ArrayList<>();
		for (int i = 1; i < tag.length(); i++) {
			final String frag = tag.substring(i);
			if (Character.isWhitespace(frag.charAt(0))) continue;
			fragments.add(frag);
		}

		for (final String frag : fragments) {
			final FragmentPrefixComp comp = new FragmentPrefixComp(frag);
			final int randomIndex = Arrays.binarySearch(this.fragmentsArr, new FragmentAndTag(frag, "unused", -1), comp);

			// TODO atm this only increments fragments already in the index.
			// it does not insert new fragments, as that would require re-ranking them etc.
			if (randomIndex < 0) continue;

			int rangeStarts = randomIndex;
			int rangeEnds = randomIndex;
			while (rangeStarts > -1
					&& this.fragmentsArr[rangeStarts].fragment.toLowerCase().startsWith(frag.toLowerCase())) {
				rangeStarts--;
			}
			while (rangeEnds < this.fragmentsArr.length
					&& this.fragmentsArr[rangeEnds].fragment.toLowerCase().startsWith(frag.toLowerCase())) {
				rangeEnds++;
			}
			rangeStarts += 1;

			for (int i = rangeStarts; i < rangeEnds; i++) {
				final FragmentAndTag match = this.fragmentsArr[i];
				if (tag.equals(match.tag) && frag.equals(match.fragment)) {
					this.fragmentsArr[i] = match.changeCount(delta);
				}
			}
		}
	}

	static class FragmentAndTag {
		final String fragment;
		final String tag;
		final int fileCount;

		public FragmentAndTag(final String fragment, final String tag, final int fileCount) {
			if (fragment == null) throw new IllegalArgumentException("fragment is null.");
			if (tag == null) throw new IllegalArgumentException("tag is null.");
			this.fragment = fragment;
			this.tag = tag;
			this.fileCount = fileCount;
		}

		public FragmentAndTag changeCount(final int delta) {
			return new FragmentAndTag(this.fragment, this.tag, Math.max(this.fileCount + delta, 0));
		}

		@Override
		public String toString() {
			return String.format("FragmentAndTag{%s, %s, %s}", this.fragment, this.tag, this.fileCount);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.fragment, this.tag, this.fileCount);
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == null) return false;
			if (this == obj) return true;
			if (!(obj instanceof FragmentAndTag)) return false;
			final FragmentAndTag that = (FragmentAndTag) obj;
			return Objects.equals(this.fragment, that.fragment)
					&& Objects.equals(this.tag, that.tag)
					&& Objects.equals(this.fileCount, that.fileCount);
		}

		public enum Order implements Comparator<FragmentAndTag> {
			FRAGMENT_ASC {
				@Override
				public int compare(final FragmentAndTag a, final FragmentAndTag b) {
					final int c = a.fragment.compareTo(b.fragment);
					if (c != 0) return c;
					return COUNT_DESC.compare(a, b);
				}
			},
			COUNT_DESC {
				@Override
				public int compare(final FragmentAndTag a, final FragmentAndTag b) {
					final int c = Long.compare(b.fileCount, a.fileCount);
					if (c != 0) return c;
					return TAG_ASC.compare(a, b);
				}
			},
			TAG_ASC {
				@Override
				public int compare(final FragmentAndTag a, final FragmentAndTag b) {
					return a.tag.compareTo(b.tag);
				}
			}
		}
	}

}

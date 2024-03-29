package com.vaguehope.dlnatoad.ui;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vaguehope.dlnatoad.auth.ReqAttr;
import com.vaguehope.dlnatoad.db.TagAutocompleter;
import com.vaguehope.dlnatoad.db.TagFrequency;
import com.vaguehope.dlnatoad.db.search.DbSearchSyntax;

public class AutocompleteServlet extends HttpServlet {

	private static final String CONTENT_TYPE_JSON = "text/json;charset=utf-8";
	private static final long serialVersionUID = 7357804711012837077L;

	private final TagAutocompleter tagAutocompleter;
	private final Gson gson;

	public AutocompleteServlet(final TagAutocompleter tagAutocompleter) {
		this.tagAutocompleter = tagAutocompleter;
		this.gson = new GsonBuilder().create();
	}

	@SuppressWarnings("resource")
	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		if (this.tagAutocompleter == null) {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "DB is not enabled.");
			return;
		}

		if (req.getParameter("reload") != null && ReqAttr.USERNAME.get(req) != null) {
			try {
				this.tagAutocompleter.generateIndex();
				resp.getWriter().println("Index generated successfully.");
				return;
			}
			catch (final SQLException e) {
				resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				e.printStackTrace(resp.getWriter());
				return;
			}
		}

		final String mode = ServletCommon.readRequiredParam(req, resp, "mode", 1);
		if (mode == null) return;
		final String fragment = ServletCommon.readRequiredParam(req, resp, "fragment", 1);
		if (fragment == null) return;

		final Collection<TagFrequency> tags;
		if ("addtag".equalsIgnoreCase(mode)) {
			tags = mergedPrefixAndFragmentSuggestions(fragment);
		}
		else if ("search".equalsIgnoreCase(mode)) {
			tags = forSearch(fragment);
		}
		else {
			ServletCommon.returnStatus(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid mode.");
			return;
		}

		resp.setContentType(CONTENT_TYPE_JSON);
		this.gson.toJson(tags, resp.getWriter());
	}

	private Collection<TagFrequency> mergedPrefixAndFragmentSuggestions(final String fragment) {
		final List<TagFrequency> ret = new ArrayList<>();
		ret.addAll(this.tagAutocompleter.suggestTags(fragment));
		ret.addAll(this.tagAutocompleter.suggestFragments(fragment));
		ret.sort(TagFrequency.Order.COUNT_DESC);
		return new LinkedHashSet<>(ret);
	}

	private List<TagFrequency> forSearch(final String fragment) {
		final String toMatch = DbSearchSyntax.removeMatchOperator(fragment);
		if (DbSearchSyntax.isTagMatchExact(fragment)) {
			return addPrefix(this.tagAutocompleter.suggestTags(toMatch), "t=");
		}
		else if (DbSearchSyntax.isTagNotMatchExact(fragment)) {
			return addPrefix(this.tagAutocompleter.suggestTags(toMatch), "-t=");
		}
		else if (DbSearchSyntax.isTagMatchPartial(fragment)) {
			return addPrefix(mergedPrefixAndFragmentSuggestions(toMatch), "t=");
		}
		else if (DbSearchSyntax.isTagNotMatchPartial(fragment)) {
			return addPrefix(mergedPrefixAndFragmentSuggestions(toMatch), "-t=");
		}
		throw new IllegalStateException("Fragment does not start with matches: " + fragment);
	}

	private static List<TagFrequency> addPrefix(final Collection<TagFrequency> tags, final String prefix) {
		final List<TagFrequency> ret = new ArrayList<>(tags.size());
		for (final TagFrequency tf : tags) {
			ret.add(new TagFrequency(prefix + tf.getTag(), tf.getCount()));
		}
		return ret;
	}

}

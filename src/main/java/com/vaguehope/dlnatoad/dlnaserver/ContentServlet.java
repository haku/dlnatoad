package com.vaguehope.dlnatoad.dlnaserver;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.dlnatoad.C;
import com.vaguehope.dlnatoad.util.StringHelper;

public class ContentServlet extends DefaultServlet {

	private static final long serialVersionUID = -4819786280597656455L;
	private static final Logger LOG = LoggerFactory.getLogger(ContentServlet.class);

	private final ContentTree contentTree; // NOSONAR
	private final ContentServingHistory contentServingHistory;
	private final boolean printAccessLog;

	public ContentServlet (final ContentTree contentTree, final ContentServingHistory contentServingHistory, final boolean printAccessLog) {
		this.contentTree = contentTree;
		this.contentServingHistory = contentServingHistory;
		this.printAccessLog = printAccessLog;
	}

	@Override
	protected void doGet (final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String remoteAddr = req.getRemoteAddr();
		final String requestURI = req.getRequestURI();
		try {
			this.contentServingHistory.recordStart(remoteAddr, requestURI);
			super.doGet(req, resp);
		}
		finally {
			this.contentServingHistory.recordEnd(remoteAddr, requestURI);
			if (this.printAccessLog) {
				final String ranges = StringHelper.join(req.getHeaders(HttpHeaders.RANGE), ",");
				if (ranges != null) {
					LOG.info("{} {} {} (r:{}) {}", resp.getStatus(), req.getMethod(), requestURI, ranges, remoteAddr);
				}
				else {
					LOG.info("{} {} {} {}", resp.getStatus(), req.getMethod(), requestURI, remoteAddr);
				}
			}
		}
	}

	@Override
	public Resource getResource (final String pathInContext) {
		if (pathInContext.endsWith(".gz")) return null;

		try {
			String id = URLDecoder.decode(pathInContext, "UTF-8");
			id = contentNodeIdFromPath(id);
			final ContentNode node = this.contentTree.getNode(id);
			if (node != null && node.isItem()) {
				return Resource.newResource(node.getFile());
			}
		}
		catch (final MalformedURLException e) {
			LOG.warn("Failed to map resource '{}': {}", pathInContext, e.getMessage());
		}
		catch (final IOException e) {
			LOG.warn("Failed to serve resource '{}': {}", pathInContext, e.getMessage());
		}
		return null;
	}

	public static String contentNodeIdFromPath(String id) {
		id = StringHelper.removePrefix(id, "/");
		id = StringHelper.removePrefix(id, C.CONTENT_PATH_PREFIX);
		id = StringHelper.removeSuffix(id, "/");
		// Remove everything before the last slash.
		final int lastSlash = id.lastIndexOf("/");
		if (lastSlash >= 0 && lastSlash < id.length() - 1) {
			id = id.substring(lastSlash + 1);
		}
		// Remove everything after first dot.
		final int firstDot = id.indexOf('.');
		if (firstDot > 0) {
			id = id.substring(0, firstDot);
		}
		return id;
	}

}

package com.vaguehope.dlnatoad.db;

import java.io.File;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.Encoding;
import org.sqlite.SQLiteConfig.TransactionMode;

public class MediaDb {

	public static final String COL_FILE = "file";
	public static final String COL_TAG = "tag";

	private final String dbPath;
	private final Connection dbConn;

	public MediaDb (final File dbFile) throws SQLException {
		this("jdbc:sqlite:" + dbFile.getAbsolutePath());
	}

	protected MediaDb(final String dbPath) throws SQLException {
		this.dbPath = dbPath;
		this.dbConn = makeDbConnection(dbPath);
		makeSchema();
	}

	private void makeSchema () throws SQLException {
		if (!tableExists("files")) {
			executeSql("CREATE TABLE files ("
					+ COL_FILE + " STRING NOT NULL PRIMARY KEY, "
					+ "size INT NOT NULL, "
					+ "modified INT NOT NULL, "
					+ "hash STRING NOT NULL, "
					+ "id STRING NOT NULL"
					+ ");");
		}
		Sqlite.addColumnIfMissing(this.dbConn, "files", "auth", "STRING NOT NULL DEFAULT '0'");
		Sqlite.addColumnIfMissing(this.dbConn, "files", "missing", "INT(1) NOT NULL DEFAULT 0");
		Sqlite.addColumnIfMissing(this.dbConn, "files", "md5", "STRING");
		// TODO index on MD5?
		if (!tableExists("tags")) {
			executeSql("CREATE TABLE tags ("
					+ "file_id STRING NOT NULL, "
					+ COL_TAG + " STRING NOT NULL COLLATE NOCASE, "
					+ "cls STRING NOT NULL COLLATE NOCASE DEFAULT '', "
					+ "modified INT NOT NULL, "
					+ "deleted INT(1) NOT NULL DEFAULT 0, "
					+ "UNIQUE(file_id, " + COL_TAG + ", cls)"  // TODO auto backfill adding cls here?
					+ ");");
		}
		Sqlite.addColumnIfMissing(this.dbConn, "tags", "cls", "STRING NOT NULL COLLATE NOCASE DEFAULT ''");
		if (!tableExists("hashes")) {
			executeSql("CREATE TABLE hashes ("
					+ "hash STRING NOT NULL PRIMARY KEY, id STRING NOT NULL);");
			executeSql("CREATE INDEX hashes_idx ON hashes (id);");
		}
		if (!tableExists("infos")) {
			executeSql("CREATE TABLE infos ("
					+ "file_id STRING NOT NULL PRIMARY KEY, "
					+ "size INT NOT NULL, "
					+ "duration INT, "
					+ "width INT, "
					+ "height INT);");
		}
	}

	@SuppressWarnings("resource")
	public WritableMediaDb getWritable() throws SQLException {
		final Connection c = makeDbConnection(this.dbPath);
		c.setAutoCommit(false);
		return new WritableMediaDb(c);
	}

	public PreparedStatement prepare(final String sql) throws SQLException {
		try {
			return this.dbConn.prepareStatement(sql);
		}
		catch (final SQLException e) {
			throw new SQLException("Failed to compile query (sql='" + sql + "').", e);
		}
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
	// Files.

	/**
	 * Only for unit tests.
	 */
	// TODO replace with readFileData() ?
	BigInteger readFileAuth(final File file) throws SQLException {
		try (final PreparedStatement st = this.dbConn.prepareStatement("SELECT auth FROM files WHERE file=?;")) {
			st.setString(1, file.getAbsolutePath());
			st.setMaxRows(2);
			try (final ResultSet rs = st.executeQuery()) {
				if (!rs.next()) return null;
				final BigInteger ret = new BigInteger(rs.getString(1), 16);
				if (rs.next()) throw new SQLException("Query for file '" + file.getAbsolutePath() + "' retured more than one result.");
				return ret;
			}
		}
	}

	public String getFilePathForId(final String id) throws SQLException {
		try (final PreparedStatement st = this.dbConn.prepareStatement("SELECT file FROM files WHERE id=?;")) {
			st.setString(1, id);
			st.setMaxRows(2);
			try (final ResultSet rs = st.executeQuery()) {
				if (!rs.next()) return null;
				final String file = rs.getString(1);
				if (rs.next()) throw new SQLException("Query for file '" + id + "' retured more than one result.");
				return file;
			}
		}
	}

	public FileData getFileData(final File file) throws SQLException {
		return readFileDataFromConn(this.dbConn, file);
	}

	protected static FileData readFileDataFromConn(final Connection conn, final File file) throws SQLException {
		try (final PreparedStatement st = conn.prepareStatement("SELECT size, modified, hash, md5, id, auth, missing FROM files WHERE file=?;")) {
			st.setString(1, file.getAbsolutePath());
			st.setMaxRows(2);
			try (final ResultSet rs = st.executeQuery()) {
				if (!rs.next()) return null;
				final FileData fileData = new FileData(
						rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
						new BigInteger(rs.getString(6), 16), rs.getInt(7) != 0);
				if (rs.next()) throw new SQLException("Query for file '" + file.getAbsolutePath() + "' retured more than one result.");
				return fileData;
			}
		}
	}

	public Collection<String> getAllFilesThatAreNotMarkedAsMissing() throws SQLException {
		try (final PreparedStatement st = this.dbConn.prepareStatement("SELECT file FROM files WHERE missing=0;")) {
			try (final ResultSet rs = st.executeQuery()) {
				final Collection<String> ret = new ArrayList<>();
				while (rs.next()) {
					ret.add(rs.getString(1));
				}
				return ret;
			}
		}
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
	// File Info; duration, width, height.

	protected FileInfo readInfoCheckingFileSize (final String fileId, final long expectedSize) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"SELECT size, duration, width, height FROM infos WHERE file_id=?;");
		try {
			st.setString(1, fileId);
			st.setMaxRows(2);
			final ResultSet rs = st.executeQuery();
			try {
				if (!rs.next()) return null;

				final long storedSize = rs.getLong(1);
				final long duration = rs.getLong(2);
				final int width = rs.getInt(3);
				final int height = rs.getInt(4);

				if (rs.next()) throw new SQLException("Query for file_id '" + fileId + "' retured more than one result.");
				if (expectedSize != storedSize) return null;

				return new FileInfo(duration, width, height);
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
	// Hashes.

	public String canonicalIdForHash (final String hash) throws SQLException {
		return MediaDb.canonicalIdForHashFromConn(this.dbConn, hash);
	}

	/**
	 * hash is lower case hex, from BigInteger.toString(16).
	 */
	protected static String canonicalIdForHashFromConn (final Connection conn, final String hash) throws SQLException {
		final PreparedStatement st = conn.prepareStatement(
				"SELECT id FROM hashes WHERE hash=?;");
		try {
			st.setString(1, hash);
			st.setMaxRows(2);
			final ResultSet rs = st.executeQuery();
			try {
				if (!rs.next()) return null;
				final String id = rs.getString(1);
				if (rs.next()) throw new SQLException("Query for hash '" + hash + "' retured more than one result.");
				return id;
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
	// Tags.

	/**
	 * Does not return deleted tags.
	 */
	public Collection<String> getTagsSimple(final String fileId) throws SQLException {
		try (final PreparedStatement st = this.dbConn.prepareStatement("SELECT DISTINCT tag FROM tags WHERE file_id=? AND deleted=0;")) {
			st.setString(1, fileId);
			try (final ResultSet rs = st.executeQuery()) {
				final Collection<String> ret = new ArrayList<>();
				while (rs.next()) {
					ret.add(rs.getString(1));
				}
				return ret;
			}
		}
	}

	public Collection<Tag> getTags(final String fileId, final boolean includeDeleted) throws SQLException {
		return getTagsFromConn(this.dbConn, fileId, includeDeleted);
	}

	protected static Collection<Tag> getTagFromConn(final Connection conn, final String fileId, final String tag, final String cls) throws SQLException {
		try (final PreparedStatement st = conn.prepareStatement(SELECT_FROM_TAGS + "file_id=? AND tag=? AND cls=?")) {
			st.setString(1, fileId);
			st.setString(2, tag);
			st.setString(3, cls);
			return readTagsResultSet(st);
		}
	}

	protected static Collection<Tag> getTagsFromConn(final Connection conn, final String fileId, final boolean includeDeleted) throws SQLException {
		String query = SELECT_FROM_TAGS + "file_id=?";
		if (!includeDeleted) query += " AND deleted=0";
		try (final PreparedStatement st = conn.prepareStatement(query)) {
			st.setString(1, fileId);
			return readTagsResultSet(st);
		}
	}

	private static final String SELECT_FROM_TAGS = "SELECT tag,cls,modified,deleted FROM tags WHERE ";

	private static Collection<Tag> readTagsResultSet(final PreparedStatement st) throws SQLException {
		try (final ResultSet rs = st.executeQuery()) {
			final Collection<Tag> ret = new ArrayList<>();
			while (rs.next()) {
				ret.add(new Tag(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getInt(4) != 0));
			}
			return ret;
		}
	}

	public List<TagFrequency> getTopTags(final Set<BigInteger> authIds, final String pathPrefix, final int countLimit) throws SQLException {
		final StringBuilder sql = new StringBuilder();
		sql.append("SELECT DISTINCT tag, COUNT(DISTINCT file_id) AS freq FROM files, tags WHERE id=file_id AND deleted=0 AND");
		if (pathPrefix != null) {
			sql.append(" file LIKE ? ESCAPE ? AND");
		}
		SqlFragments.appendWhereAuth(sql, authIds);
		sql.append(" GROUP BY tag ORDER BY freq DESC, tag ASC LIMIT ?;");
		try (final PreparedStatement st = this.dbConn.prepareStatement(sql.toString())) {
			int param = 1;
			if (pathPrefix != null) {
				String pathLike = pathPrefix;
				// TODO what about file systems that use \ ?
				if (!pathLike.endsWith("/")) pathLike += "/";
				st.setString(param++, Sqlite.escapeSearch(pathLike) + "%");
				st.setString(param++, Sqlite.SEARCH_ESC);
			}
			st.setInt(param++, countLimit);
			st.setMaxRows(countLimit);
			return readTagFrequencyResultSet(countLimit, st);
		}
	}

	// FIXME this does not honour auth.
	@Deprecated
	public List<TagFrequency> getAutocompleteSuggestions(final String fragment, final int countLimit, final boolean startsWithOnly) throws SQLException {
		final String sql = "SELECT tag, COUNT(DISTINCT file_id) AS count"
				+ " FROM tags"
				+ " WHERE tag LIKE ? ESCAPE ? AND deleted=0"
				+ " GROUP BY tag"
				+ " ORDER BY count DESC, tag ASC"
				+ " LIMIT ?;";
		try (final PreparedStatement st = this.dbConn.prepareStatement(sql)) {
			String matcher = Sqlite.escapeSearch(fragment) + "%";
			if (!startsWithOnly) matcher = "%" + matcher;
			st.setString(1, matcher);
			st.setString(2, Sqlite.SEARCH_ESC);
			st.setInt(3, countLimit);
			st.setMaxRows(countLimit);
			return readTagFrequencyResultSet(countLimit, st);
		}
	}

	// FIXME this does not honour auth.
	public List<TagFrequency> getAllTagsNotMissingNotDeleted() throws SQLException {
		final String sql = "SELECT DISTINCT tag, COUNT(DISTINCT file_id) AS freq"
				+ " FROM files, tags"
				+ " WHERE id=file_id"
				+ " AND missing=0"
				+ " AND deleted=0"
				+ " GROUP BY tag"
				+ " ORDER BY tag ASC, freq DESC;";  // Sort order depended on by TagAutocompleter.
		try (final PreparedStatement st = this.dbConn.prepareStatement(sql.toString())) {
			return readTagFrequencyResultSet(1000, st);
		}
	}

	private static List<TagFrequency> readTagFrequencyResultSet(final int count, final PreparedStatement st) throws SQLException {
		try (final ResultSet rs = st.executeQuery()) {
			final List<TagFrequency> ret = new ArrayList<>(count);
			while (rs.next()) {
				ret.add(new TagFrequency(rs.getString(1), rs.getInt(2)));
			}
			return ret;
		}
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	private static SQLiteConfig makeDbConfig() throws SQLException {
		final SQLiteConfig c = new SQLiteConfig();
		c.setEncoding(Encoding.UTF8);
		c.setSharedCache(true);
		c.setTransactionMode(TransactionMode.IMMEDIATE);
		c.enforceForeignKeys(true);
		c.setBusyTimeout((int) TimeUnit.SECONDS.toMillis(30));  // Should be longer than MediaMetadataStore.FILE_BATCH_MAX_DURATION.
		return c;
	}

	private static Connection makeDbConnection (final String dbPath) throws SQLException {
		return DriverManager.getConnection(dbPath, makeDbConfig().toProperties());
	}

	private boolean tableExists (final String tableName) throws SQLException {
		final Statement st = this.dbConn.createStatement();
		try {
			final ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "';");
			try {
				return rs.next();
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

	private boolean executeSql (final String sql) throws SQLException {
		final Statement st = this.dbConn.createStatement();
		try {
			return st.executeUpdate(sql) > 0;
		}
		catch (final SQLException e) {
			throw new SQLException(String.format("Failed to execute SQL \"%s\".", sql), e);
		}
		finally {
			st.close();
		}
	}

}

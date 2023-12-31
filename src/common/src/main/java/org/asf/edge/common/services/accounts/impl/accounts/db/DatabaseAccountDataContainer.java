package org.asf.edge.common.services.accounts.impl.accounts.db;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asf.edge.common.services.accounts.AccountDataContainer;
import org.asf.edge.common.services.accounts.AccountObject;
import org.asf.edge.common.services.accounts.AccountSaveContainer;
import org.asf.edge.common.services.accounts.impl.DatabaseAccountManager;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class DatabaseAccountDataContainer extends AccountDataContainer {

	private String id;
	private AccountObject account;
	private DatabaseAccountManager mgr;

	private Logger logger = LogManager.getLogger("AccountManager");

	private HashMap<String, JsonElement> dataCache = new HashMap<String, JsonElement>();

	public DatabaseAccountDataContainer(AccountObject account, String id, DatabaseAccountManager manager) {
		this.account = account;
		this.id = id;
		this.mgr = manager;
	}

	@Override
	protected JsonElement get(String key) throws IOException {
		String keyF = key;

		// Check caching
		if (!key.startsWith("accountdata/")) {
			// Get
			while (true) {
				try {
					if (dataCache.containsKey(key))
						return dataCache.get(key) == null ? null : dataCache.get(key).deepCopy();
					break;
				} catch (ConcurrentModificationException e) {
				}
			}
		}
		// Add if needed
		synchronized (dataCache) {
			if (!key.startsWith("accountdata/")) {
				if (dataCache.containsKey(key))
					return dataCache.get(key) == null ? null : dataCache.get(key).deepCopy();
			}
			try {
				DatabaseRequest req = mgr.createRequest();
				try {
					// Parse key
					String parent = "";
					String parentContainer = "";
					if (key.contains("/")) {
						parent = key.substring(0, key.lastIndexOf("/"));
						key = key.substring(key.lastIndexOf("/") + 1);

						// Check for inner parent
						if (parent.contains("/")) {
							parentContainer = parent.substring(0, parent.lastIndexOf("/"));
							parent = parent.substring(parent.lastIndexOf("/") + 1);
						}
					}

					// Create prepared statement
					var statement = req.prepareStatement(
							"SELECT DATA FROM ACCOUNTWIDEPLAYERDATA_V2 WHERE DATAKEY = ? AND PARENT = ? AND PARENTCONTAINER = ? AND ACCID = ?");
					statement.setString(1, key);
					statement.setString(2, parent);
					statement.setString(3, parentContainer);
					statement.setString(4, id);
					ResultSet res = statement.executeQuery();
					if (!res.next()) {
						res.close();
						statement.close();
						dataCache.put(keyF, null);
						return null;
					}
					String data = res.getString("DATA");
					if (data == null) {
						res.close();
						statement.close();
						dataCache.put(keyF, null);
						return null;
					}
					res.close();
					statement.close();
					JsonElement r = JsonParser.parseString(data);
					if (!keyF.startsWith("accountdata/"))
						dataCache.put(keyF, r);
					return r;
				} finally {
					req.close();
				}
			} catch (SQLException e) {
				logger.error("Failed to execute database query request while trying to retrieve data entry '" + key
						+ "' of ID '" + id + "'", e);
				throw new IOException("SQL error", e);
			}
		}
	}

	@Override
	protected void set(String key, JsonElement value) throws IOException {
		String keyF = key;
		try {
			DatabaseRequest req = mgr.createRequest();
			try {
				// Parse key
				String parent = "";
				String parentContainer = "";
				if (key.contains("/")) {
					parent = key.substring(0, key.lastIndexOf("/"));
					key = key.substring(key.lastIndexOf("/") + 1);

					// Check for inner parent
					if (parent.contains("/")) {
						parentContainer = parent.substring(0, parent.lastIndexOf("/"));
						parent = parent.substring(parent.lastIndexOf("/") + 1);
					}
				}

				// Create prepared statement
				var statement = req.prepareStatement(
						"UPDATE ACCOUNTWIDEPLAYERDATA_V2 SET DATA = ? WHERE DATAKEY = ? AND PARENT = ? AND PARENTCONTAINER = ? AND ACCID = ?");
				req.setDataObject(1, value.toString(), statement);
				statement.setString(2, key);
				statement.setString(3, parent);
				statement.setString(4, parentContainer);
				statement.setString(5, id);
				statement.execute();
				statement.close();
				if (!keyF.startsWith("accountdata/"))
					dataCache.put(keyF, value);
			} finally {
				req.close();
			}
		} catch (SQLException e) {
			logger.error("Failed to execute database query request while trying to update data entry '" + key
					+ "' of ID '" + id + "'", e);
			throw new IOException("SQL error", e);
		}
	}

	@Override
	protected void create(String key, String root, JsonElement value) throws IOException {
		String keyF = key;
		try {
			DatabaseRequest req = mgr.createRequest();
			try {
				// Parse key
				String parent = "";
				String parentContainer = "";
				if (key.contains("/")) {
					parent = key.substring(0, key.lastIndexOf("/"));
					key = key.substring(key.lastIndexOf("/") + 1);

					// Check for inner parent
					if (parent.contains("/")) {
						parentContainer = parent.substring(0, parent.lastIndexOf("/"));
						parent = parent.substring(parent.lastIndexOf("/") + 1);
					}
				}

				// Create prepared statement
				var statement = req.prepareStatement("INSERT INTO ACCOUNTWIDEPLAYERDATA_V2 VALUES(?, ?, ?, ?, ?)");
				statement.setString(1, id);
				statement.setString(2, key);
				statement.setString(3, parent);
				statement.setString(4, parentContainer);
				req.setDataObject(5, value.toString(), statement);
				statement.execute();
				statement.close();
				if (!keyF.startsWith("accountdata/"))
					dataCache.put(keyF, value);
			} finally {
				req.close();
			}
		} catch (SQLException e) {
			logger.error("Failed to execute database query request while trying to create data entry '" + key
					+ "' of ID '" + id + "'", e);
			throw new IOException("SQL error", e);
		}
	}

	@Override
	protected boolean exists(String key) throws IOException {
		String keyF = key;
		if (!key.startsWith("accountdata/")) {
			while (true) {
				try {
					if (dataCache.containsKey(key))
						return dataCache.get(key) != null;
					break;
				} catch (ConcurrentModificationException e) {
				}
			}
		}

		try {
			DatabaseRequest req = mgr.createRequest();
			try {
				// Parse key
				String parent = "";
				String parentContainer = "";
				if (key.contains("/")) {
					parent = key.substring(0, key.lastIndexOf("/"));
					key = key.substring(key.lastIndexOf("/") + 1);

					// Check for inner parent
					if (parent.contains("/")) {
						parentContainer = parent.substring(0, parent.lastIndexOf("/"));
						parent = parent.substring(parent.lastIndexOf("/") + 1);
					}
				}

				// Create prepared statement
				var statement = req.prepareStatement(
						"SELECT DATA FROM ACCOUNTWIDEPLAYERDATA_V2 WHERE DATAKEY = ? AND PARENT = ? AND PARENTCONTAINER = ? AND ACCID = ?");
				statement.setString(1, key);
				statement.setString(2, parent);
				statement.setString(3, parentContainer);
				statement.setString(4, id);
				ResultSet res = statement.executeQuery();
				boolean r = res.next();
				if (!keyF.startsWith("accountdata/")) {
					if (!r)
						dataCache.put(keyF, null);
					else {
						// Check
						if (res.getString("DATA") == null)
							dataCache.put(keyF, null);
					}
				}
				res.close();
				statement.close();
				return r;
			} finally {
				req.close();
			}
		} catch (SQLException e) {
			logger.error("Failed to execute database query request while trying to check data entry '" + key
					+ "' of ID '" + id + "'", e);
			throw new IOException("SQL error", e);
		}
	}

	@Override
	protected void delete(String key) throws IOException {
		String keyF = key;
		try {
			DatabaseRequest req = mgr.createRequest();
			try {
				// Parse key
				String parent = "";
				String parentContainer = "";
				if (key.contains("/")) {
					parent = key.substring(0, key.lastIndexOf("/"));
					key = key.substring(key.lastIndexOf("/") + 1);

					// Check for inner parent
					if (parent.contains("/")) {
						parentContainer = parent.substring(0, parent.lastIndexOf("/"));
						parent = parent.substring(parent.lastIndexOf("/") + 1);
					}
				}

				// Create prepared statement
				var statement = req.prepareStatement(
						"DELETE FROM ACCOUNTWIDEPLAYERDATA_V2 WHERE DATAKEY = ? AND PARENT = ? AND PARENTCONTAINER = ? AND ACCID = ?");
				statement.setString(1, key);
				statement.setString(2, parent);
				statement.setString(3, parentContainer);
				statement.setString(4, id);
				statement.execute();
				statement.close();
				if (!keyF.startsWith("accountdata/"))
					dataCache.remove(keyF);
			} finally {
				req.close();
			}
		} catch (SQLException e) {
			logger.error("Failed to execute database query request while trying to delete data entry '" + key
					+ "' of ID '" + id + "'", e);
			throw new IOException("SQL error", e);
		}
	}

	@Override
	protected String[] getEntryKeys(String key) throws IOException {
		// Parse key
		String parent = key;
		String parentContainer = "";

		// Check for inner parent
		if (parent.contains("/")) {
			parentContainer = parent.substring(0, parent.lastIndexOf("/"));
			parent = parent.substring(parent.lastIndexOf("/") + 1);
		}

		// Prepare
		ArrayList<String> keys = new ArrayList<String>();

		// Find all keys
		try {
			DatabaseRequest req = mgr.createRequest();
			try {
				// Create prepared statement
				var statement = req.prepareStatement(
						"SELECT DATAKEY FROM ACCOUNTWIDEPLAYERDATA_V2 WHERE PARENT = ? AND PARENTCONTAINER = ? AND ACCID = ?");
				statement.setString(1, parent);
				statement.setString(2, parentContainer);
				statement.setString(3, id);
				ResultSet res = statement.executeQuery();

				// Find results
				while (res.next()) {
					// Add container
					String cont = res.getString("DATAKEY");
					if (!cont.isEmpty() && !keys.contains(cont))
						keys.add(cont);
				}
				res.close();
				statement.close();
			} finally {
				req.close();
			}
		} catch (SQLException e) {
			logger.error(
					"Failed to execute database query request while trying to retrieve child containers of data container '"
							+ key + "' of ID '" + id + "'",
					e);
			throw new IOException("SQL error", e);
		}

		return keys.toArray(t -> new String[t]);
	}

	@Override
	protected String[] getChildContainers(String key) throws IOException {
		// Prepare
		ArrayList<String> containers = new ArrayList<String>();

		// Find all containers
		try {
			DatabaseRequest req = mgr.createRequest();
			try {
				// Create prepared statement
				var statement = req.prepareStatement(
						"SELECT PARENT FROM ACCOUNTWIDEPLAYERDATA_V2 WHERE PARENTCONTAINER = ? AND ACCID = ?");
				statement.setString(1, key);
				statement.setString(2, id);
				ResultSet res = statement.executeQuery();

				// Find results
				while (res.next()) {
					// Add container
					String cont = res.getString("PARENT");
					if (!cont.isEmpty() && !containers.contains(cont))
						containers.add(cont);
				}
				res.close();
				statement.close();
			} finally {
				req.close();
			}
		} catch (SQLException e) {
			logger.error(
					"Failed to execute database query request while trying to retrieve child containers of data container '"
							+ key + "' of ID '" + id + "'",
					e);
			throw new IOException("SQL error", e);
		}

		// Return
		return containers.toArray(t -> new String[t]);
	}

	@Override
	protected void deleteContainer(String root) throws IOException {
		// Parse key
		String parent = root;
		String parentContainer = "";

		// Check for inner parent
		if (parent.contains("/")) {
			parentContainer = parent.substring(0, parent.lastIndexOf("/"));
			parent = parent.substring(parent.lastIndexOf("/") + 1);
		}
		try {
			// Delete entries
			for (String ent : getEntryKeys()) {
				String k = (root.isEmpty() ? "" : root + "/") + ent;
				if (dataCache.containsKey(k))
					dataCache.remove(k);
			}

			// Delete child containers
			for (String ch : getChildContainers(root)) {
				deleteContainer((root.isEmpty() ? "" : root + "/") + ch);
			}

			// Delete container
			DatabaseRequest req = mgr.createRequest();
			try {
				// Create prepared statement
				var statement = req.prepareStatement(
						"DELETE FROM ACCOUNTWIDEPLAYERDATA_V2 WHERE PARENT = ? AND PARENTCONTAINER = ? AND ACCID = ?");
				statement.setString(1, parent);
				statement.setString(2, parentContainer);
				statement.setString(3, id);
				statement.execute();
				statement.close();
			} finally {
				req.close();
			}
		} catch (SQLException e) {
			logger.error("Failed to execute database query request while trying to delete container '"
					+ (root.isEmpty() ? "<root>" : root) + "' of ID '" + id + "'", e);
			throw new IOException("SQL error", e);
		}
	}

	@Override
	public AccountObject getAccount() {
		return account;
	}

	@Override
	public AccountSaveContainer getSave() {
		return null;
	}

	@Override
	protected JsonElement find(BiFunction<String, JsonElement, Boolean> function, String root) throws IOException {
		JsonElement resO = null;

		// Parse key
		String parent = root;
		String parentContainer = "";

		// Check for inner parent
		if (parent.contains("/")) {
			parentContainer = parent.substring(0, parent.lastIndexOf("/"));
			parent = parent.substring(parent.lastIndexOf("/") + 1);
		}

		// Prepare
		ArrayList<String> keys = new ArrayList<String>();

		// Find all keys
		try {
			DatabaseRequest req = mgr.createRequest();
			try {
				// Create prepared statement
				var statement = req.prepareStatement(
						"SELECT * FROM ACCOUNTWIDEPLAYERDATA_V2 WHERE PARENT = ? AND PARENTCONTAINER = ? AND ACCID = ?");
				statement.setString(1, parent);
				statement.setString(2, parentContainer);
				statement.setString(3, id);
				ResultSet res = statement.executeQuery();

				// Find results
				while (res.next()) {
					// Add container
					String cont = res.getString("DATAKEY");
					String data = res.getString("DATA");
					if (!cont.isEmpty() && !keys.contains(cont)) {
						keys.add(cont);

						// Run function
						JsonElement d = JsonParser.parseString(data);
						if (function.apply(cont, d)) {
							resO = d;
							break;
						}
					}
				}
				res.close();
				statement.close();
			} finally {
				req.close();
			}
		} catch (SQLException e) {
			logger.error(
					"Failed to execute database query request while trying to retrieve child containers of save data container '"
							+ root + "' of ID '" + id + "'",
					e);
			throw new IOException("SQL error", e);
		}

		// Return
		return resO;
	}

	@Override
	protected void runFor(BiFunction<String, JsonElement, Boolean> function, String root) throws IOException {
		// Parse key
		String parent = root;
		String parentContainer = "";

		// Check for inner parent
		if (parent.contains("/")) {
			parentContainer = parent.substring(0, parent.lastIndexOf("/"));
			parent = parent.substring(parent.lastIndexOf("/") + 1);
		}

		// Prepare
		ArrayList<String> keys = new ArrayList<String>();

		// Find all keys
		try {
			DatabaseRequest req = mgr.createRequest();
			try {
				// Create prepared statement
				var statement = req.prepareStatement(
						"SELECT * FROM ACCOUNTWIDEPLAYERDATA_V2 WHERE PARENT = ? AND PARENTCONTAINER = ? AND ACCID = ?");
				statement.setString(1, parent);
				statement.setString(2, parentContainer);
				statement.setString(3, id);
				ResultSet res = statement.executeQuery();

				// Find results
				while (res.next()) {
					// Add container
					String cont = res.getString("DATAKEY");
					String data = res.getString("DATA");
					if (!cont.isEmpty() && !keys.contains(cont)) {
						keys.add(cont);

						// Run function
						JsonElement d = JsonParser.parseString(data);
						if (!function.apply(cont, d))
							break;
					}
				}
				res.close();
				statement.close();
			} finally {
				req.close();
			}
		} catch (SQLException e) {
			logger.error(
					"Failed to execute database query request while trying to retrieve child containers of save data container '"
							+ root + "' of ID '" + id + "'",
					e);
			throw new IOException("SQL error", e);
		}
	}

	@Override
	protected void runForChildren(Function<String, Boolean> function, String root) throws IOException {
		// Prepare
		ArrayList<String> containers = new ArrayList<String>();

		// Find all containers
		try {
			DatabaseRequest req = mgr.createRequest();
			try {
				// Create prepared statement
				var statement = req.prepareStatement(
						"SELECT PARENT FROM ACCOUNTWIDEPLAYERDATA_V2 WHERE PARENTCONTAINER = ? AND ACCID = ?");
				statement.setString(1, root);
				statement.setString(2, id);
				ResultSet res = statement.executeQuery();

				// Find results
				while (res.next()) {
					// Add container
					String cont = res.getString("PARENT");
					if (!cont.isEmpty() && !containers.contains(cont)) {
						containers.add(cont);

						// Run function
						if (!function.apply(cont))
							break;
					}
				}
				res.close();
				statement.close();
			} finally {
				req.close();
			}
		} catch (SQLException e) {
			logger.error(
					"Failed to execute database query request while trying to retrieve child containers of save data container '"
							+ root + "' of ID '" + id + "'",
					e);
			throw new IOException("SQL error", e);
		}
	}

}

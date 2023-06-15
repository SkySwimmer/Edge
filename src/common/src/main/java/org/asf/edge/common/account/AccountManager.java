package org.asf.edge.common.account;

import org.asf.edge.common.services.AbstractService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asf.edge.common.account.AccountManager;
import org.asf.edge.common.account.impl.DatabaseAccountManager;
import org.asf.edge.common.account.impl.RemoteHttpAccountManager;
import org.asf.edge.common.services.ServiceManager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * 
 * Account manager service
 * 
 * @author Sky Swimmer
 *
 */
public abstract class AccountManager extends AbstractService {
	private static boolean initedServices;

	/**
	 * Retrieves the account manager service
	 * 
	 * @return AccountManager instance
	 */
	public static AccountManager getInstance() {
		return ServiceManager.getService(AccountManager.class);
	}

	/**
	 * Internal
	 */
	public static void initAccountManagerServices(int priorityRemote, int priorityDatabase) {
		if (initedServices)
			return;
		initedServices = true;

		// Write/load config
		Logger logger = LogManager.getLogger("AccountManager");
		File configFile = new File("accountmanager.json");
		JsonObject accountManagerConfig = new JsonObject();
		if (configFile.exists()) {
			try {
				accountManagerConfig = JsonParser.parseString(Files.readString(configFile.toPath())).getAsJsonObject();
			} catch (JsonSyntaxException | IOException e) {
				logger.error("Failed to load account manager configuration!", e);
				return;
			}
		}
		boolean changed = false;
		JsonObject remoteManagerConfig = new JsonObject();
		if (!accountManagerConfig.has("remoteHttpManager")) {
			remoteManagerConfig.addProperty("priority", priorityRemote);
			remoteManagerConfig.addProperty("url", "http://127.0.0.1:5324/accountmanager/");
			accountManagerConfig.add("remoteHttpManager", remoteManagerConfig);
			changed = true;
		} else
			remoteManagerConfig = accountManagerConfig.get("remoteHttpManager").getAsJsonObject();
		JsonObject databaseManagerConfig = new JsonObject();
		if (!accountManagerConfig.has("databaseManager")) {
			databaseManagerConfig.addProperty("priority", priorityDatabase);
			databaseManagerConfig.addProperty("url", "jdbc:derby:account-data");
			JsonObject props = new JsonObject();
			props.addProperty("create", true);
			databaseManagerConfig.add("properties", props);
			accountManagerConfig.add("databaseManager", databaseManagerConfig);
			changed = true;
		} else
			databaseManagerConfig = accountManagerConfig.get("databaseManager").getAsJsonObject();
		if (changed) {
			// Write config
			try {
				Files.writeString(configFile.toPath(),
						new Gson().newBuilder().setPrettyPrinting().create().toJson(accountManagerConfig));
			} catch (IOException e) {
				logger.error("Failed to write the account manager configuration!", e);
				return;
			}
		}

		// Register default account managers
		ServiceManager.registerServiceImplementation(AccountManager.class, new RemoteHttpAccountManager(),
				remoteManagerConfig.get("priority").getAsInt());
		ServiceManager.registerServiceImplementation(AccountManager.class, new DatabaseAccountManager(),
				databaseManagerConfig.get("priority").getAsInt());
	}

	/**
	 * Checks if a username is valid
	 * 
	 * @param username Username to check
	 * @return True if valid, false if it contains invalid characters
	 */
	public abstract boolean isValidUsername(String username);

	/**
	 * Checks if a username is in use
	 * 
	 * @param username Username to check
	 * @return True if in use, false otherwise
	 */
	public abstract boolean isUsernameTaken(String username);

	/**
	 * Retrieves account IDs by username
	 * 
	 * @param username Username to retrieve the account ID for
	 * @return Account ID string or null
	 */
	public abstract String getAccountID(String username);

	/**
	 * Called to initialize the account manager
	 */
	public abstract void loadManager();

}
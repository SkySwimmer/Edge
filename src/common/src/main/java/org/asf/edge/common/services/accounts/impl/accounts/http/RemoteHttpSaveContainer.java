package org.asf.edge.common.services.accounts.impl.accounts.http;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asf.edge.common.events.accounts.saves.AccountSaveDeletedEvent;
import org.asf.edge.common.events.accounts.saves.AccountSaveUsernameUpdateEvent;
import org.asf.edge.common.services.accounts.AccountDataContainer;
import org.asf.edge.common.services.accounts.AccountObject;
import org.asf.edge.common.services.accounts.AccountSaveContainer;
import org.asf.edge.common.services.accounts.impl.RemoteHttpAccountManager;
import org.asf.edge.modules.eventbus.EventBus;

import com.google.gson.JsonObject;

public class RemoteHttpSaveContainer extends AccountSaveContainer {

	private String id;
	private long time;
	private String username;
	private String acc;
	private AccountObject account;

	private RemoteHttpAccountManager mgr;
	private Logger logger = LogManager.getLogger("AccountManager");

	public RemoteHttpSaveContainer(String id, long time, String username, String acc, RemoteHttpAccountManager mgr,
			AccountObject account) {
		this.id = id;
		this.time = time;
		this.username = username;
		this.acc = acc;
		this.account = account;
		this.mgr = mgr;
	}

	@Override
	public long getCreationTime() {
		return time;
	}

	@Override
	public String getSaveID() {
		return id;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean updateUsername(String name) {
		String oldName = username;

		// Request
		try {
			// Build payload
			JsonObject payload = new JsonObject();
			payload.addProperty("id", acc);
			payload.addProperty("save", id);
			payload.addProperty("newName", name);
			JsonObject response = mgr.accountManagerRequest("accounts/updateSaveUsername", payload);
			boolean res = response.get("success").getAsBoolean();
			if (res)
				username = name;
			else
				return false;
		} catch (IOException e) {
			logger.error("Account server query failure occurred in updateSaveUsername!", e);
			return false;
		}

		// Dispatch event
		EventBus.getInstance().dispatchEvent(new AccountSaveUsernameUpdateEvent(oldName, name, account, this, mgr));

		// Return
		return true;
	}

	@Override
	public AccountDataContainer getSaveData() {
		return new RemoteHttpSaveDataContainer(account, this, acc, mgr);
	}

	@Override
	public void deleteSave() {
		// Request
		try {
			// Build payload
			JsonObject payload = new JsonObject();
			payload.addProperty("id", acc);
			payload.addProperty("save", id);
			JsonObject response = mgr.accountManagerRequest("accounts/deleteSave", payload);
			if (!response.get("success").getAsBoolean())
				throw new IOException("Server returned success=false");
		} catch (IOException e) {
			logger.error("Account server query failure occurred in deleteSave!", e);
		}

		// Dispatch event
		EventBus.getInstance().dispatchEvent(new AccountSaveDeletedEvent(account, this, mgr));
	}

	@Override
	public AccountObject getAccount() {
		return account;
	}

}

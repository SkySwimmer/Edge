package org.asf.edge.modules.gridapi.http.services;

import java.io.IOException;
import java.util.stream.Stream;

import org.asf.connective.processors.HttpPushProcessor;
import org.asf.edge.common.http.apihandlerutils.EdgeWebService;
import org.asf.edge.common.http.apihandlerutils.functions.Function;
import org.asf.edge.common.http.apihandlerutils.functions.FunctionInfo;
import org.asf.edge.common.http.apihandlerutils.functions.FunctionResult;
import org.asf.edge.common.services.accounts.AccountDataContainer;
import org.asf.edge.common.services.accounts.AccountManager;
import org.asf.edge.common.services.accounts.AccountObject;
import org.asf.edge.common.services.accounts.AccountSaveContainer;
import org.asf.edge.common.services.textfilter.TextFilterService;
import org.asf.edge.common.services.textfilter.result.FilterResult;
import org.asf.edge.common.services.textfilter.result.WordMatch;
import org.asf.edge.modules.gridapi.EdgeGridApiServer;
import org.asf.edge.modules.gridapi.utils.IdentityUtils;
import org.asf.edge.modules.gridapi.utils.PhoenixToken;
import org.asf.edge.modules.gridapi.utils.TokenUtils;
import org.asf.edge.modules.gridapi.utils.TokenUtils.AccessContext;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public class GridAccountManagerWebService extends EdgeWebService<EdgeGridApiServer> {

	public GridAccountManagerWebService(EdgeGridApiServer server) {
		super(server);
	}

	@Override
	public HttpPushProcessor createNewInstance() {
		return new GridAccountManagerWebService(getServerInstance());
	}

	@Override
	public String path() {
		return "/grid/accounts";
	}

	@Function(allowedMethods = { "POST" })
	public FunctionResult getProfileByID(FunctionInfo func) throws IOException {
		// Load request
		JsonObject request;
		try {
			// Parse and check
			request = JsonParser.parseString(func.getRequest().getRequestBodyAsString()).getAsJsonObject();
			if (!request.has("id"))
				throw new IOException();
		} catch (Exception e) {
			// Bad request
			return response(400, "Bad request", "text/json",
					"{\"error\":\"Invalid request body, expecting a JSON with a 'id' field\"}");
		}

		// Load request into memory
		String id = request.get("id").getAsString();

		// Find account manager
		AccountManager manager = AccountManager.getInstance();

		// Find save
		AccountSaveContainer save = manager.getSaveByID(id);
		if (save == null) {
			// Error
			return response(404, "Not found", "text/json", "{\"error\":\"save_not_found\"}");
		}

		// Build response
		AccountObject acc = save.getAccount();
		JsonObject resp = new JsonObject();
		resp.addProperty("saveId", save.getSaveID());
		resp.addProperty("saveUsername", save.getUsername());
		resp.addProperty("registrationTime", acc.getRegistrationTimestamp());
		resp.addProperty("saveCreationTime", save.getCreationTime());

		// Return
		return ok("text/json", resp.toString());
	}

	@Function(allowedMethods = { "POST" })
	public FunctionResult getProfileByName(FunctionInfo func) throws IOException {
		// Load request
		JsonObject request;
		try {
			// Parse and check
			request = JsonParser.parseString(func.getRequest().getRequestBodyAsString()).getAsJsonObject();
			if (!request.has("name"))
				throw new IOException();
		} catch (Exception e) {
			// Bad request
			return response(400, "Bad request", "text/json",
					"{\"error\":\"Invalid request body, expecting a JSON with a 'name' field\"}");
		}

		// Load request into memory
		String name = request.get("name").getAsString();

		// Find account manager
		AccountManager manager = AccountManager.getInstance();

		// Find save
		String accID = manager.getAccountIdBySaveUsername(name);
		if (accID == null) {
			// Error
			return response(404, "Not found", "text/json", "{\"error\":\"save_not_found\"}");
		}
		AccountObject acc = manager.getAccount(accID);
		if (acc == null) {
			// Error
			return response(404, "Not found", "text/json", "{\"error\":\"save_not_found\"}");
		}
		AccountSaveContainer save = null;
		for (String saveID : acc.getSaveIDs()) {
			AccountSaveContainer sv = acc.getSave(saveID);
			if (sv.getUsername().equalsIgnoreCase(name)) {
				save = sv;
				break;
			}
		}
		if (save == null) {
			// Error
			return response(404, "Not found", "text/json", "{\"error\":\"save_not_found\"}");
		}

		// Build response
		JsonObject resp = new JsonObject();
		resp.addProperty("saveId", save.getSaveID());
		resp.addProperty("saveUsername", save.getUsername());
		resp.addProperty("registrationTime", acc.getRegistrationTimestamp());
		resp.addProperty("saveCreationTime", save.getCreationTime());

		// Return
		return ok("text/json", resp.toString());
	}

	@Function(allowedMethods = { "POST" })
	public FunctionResult registerAccount(FunctionInfo func) throws IOException {
		// Register accounts

		// Check authentication
		if (!func.getRequest().hasHeader("Authorization")
				|| !func.getRequest().getHeader("Authorization").getValue().toLowerCase().startsWith("bearer ")) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Missing authorization token\"}");
		}

		// Read token
		String token = func.getRequest().getHeader("Authorization").getValue().substring("bearer ".length());
		AccessContext ctx = TokenUtils.fromToken(token, "login");
		if (ctx == null || !ctx.isAccount) {
			// Error
			return response(401, "Unauthorized", "text/json",
					"{\"error\":\"Authorization token invalid (requiring LOGIN capability)\"}");
		}

		// Load request
		JsonObject request;
		try {
			// Parse and check
			request = JsonParser.parseString(func.getRequest().getRequestBodyAsString()).getAsJsonObject();
			if (!request.has("username") || !request.has("password") || !request.has("isUnderageUser"))
				throw new IOException();
		} catch (Exception e) {
			// Bad request
			return response(400, "Bad request", "text/json",
					"{\"error\":\"Invalid request body, expecting a JSON with a 'email', 'username', 'password', 'subscribeEmailNotifications' and 'isUnderageUser' field\"}");
		}

		// Load request into memory
		String username = request.get("username").getAsString();
		String password = request.get("password").getAsString();
		boolean isUnderageUser = request.get("isUnderageUser").getAsBoolean();

		// Find account manager
		AccountManager manager = AccountManager.getInstance();

		// Check username validity
		if (!manager.isValidUsername(username)) {
			// Error
			return response(400, "Bad request", "text/json", "{\"error\":\"invalid_username\"}");
		}

		// Check filters
		if (TextFilterService.getInstance().isFiltered(username, true)) {
			// Error
			return response(400, "Bad request", "text/json", "{\"error\":\"imappropriate_username\"}");
		}

		// Check password validity
		if (!manager.isValidPassword(password)) {
			// Error
			return response(400, "Bad request", "text/json", "{\"error\":\"invalid_password\"}");
		}

		// Check if name is taken
		if (manager.isUsernameTaken(username)) {
			// Error
			return response(400, "Bad request", "text/json", "{\"error\":\"username_in_use\"}");
		}

		// Create account
		AccountObject acc = manager.registerAccount(username, null, password.toCharArray());
		if (acc == null) {
			// Error
			return response(500, "Internal server error", "text/json", "{\"error\":\"server_error\"}");
		}

		// Set data
		AccountDataContainer cont = acc.getAccountData().getChildContainer("accountdata");
		cont.setEntry("sendupdates", new JsonPrimitive(false));
		cont.setEntry("isunderage", new JsonPrimitive(isUnderageUser));
		cont.setEntry("last_update", new JsonPrimitive(System.currentTimeMillis()));
		cont.setEntry("significantFieldRandom", new JsonPrimitive(IdentityUtils.rnd.nextInt()));
		cont.setEntry("significantFieldNumber", new JsonPrimitive(System.currentTimeMillis()));
		if (isUnderageUser) {
			acc.setChatEnabled(false);
			acc.setStrictChatFilterEnabled(true);
		}

		// Generate game token
		PhoenixToken refresh = new PhoenixToken();
		refresh.gameID = "nexusgrid";
		refresh.identity = acc.getAccountID();
		refresh.lastUpdate = cont.getEntry("last_update").getAsLong();
		refresh.tokenExpiryTime = (System.currentTimeMillis() / 1000) + (30 * 24 * 60 * 60);
		refresh.tokenGenerationTime = System.currentTimeMillis() / 1000;
		refresh.tokenNotBefore = -1;
		refresh.capabilities = new String[] { "playerrefreshlogin" };

		// Build response
		JsonObject resp = new JsonObject();
		resp.addProperty("refreshToken", refresh.toTokenString());
		resp.addProperty("accountId", acc.getAccountID());
		resp.addProperty("accountUsername", acc.getUsername());
		resp.addProperty("accountEmail", ctx.account.getAccountEmail());
		resp.addProperty("lastLoginTime", acc.getLastLoginTime());
		resp.addProperty("registrationTime", acc.getRegistrationTimestamp());
		resp.addProperty("multiplayerEnabled", acc.isMultiplayerEnabled());
		resp.addProperty("chatEnabled", acc.isChatEnabled());
		resp.addProperty("strictChat", acc.isStrictChatFilterEnabled());

		// Add profiles
		JsonObject profiles = new JsonObject();
		for (String save : acc.getSaveIDs()) {
			AccountSaveContainer sv = acc.getSave(save);
			if (sv != null) {
				JsonObject profile = new JsonObject();
				profile.addProperty("username", sv.getUsername());
				profile.addProperty("creationTime", sv.getCreationTime());
				profiles.add(save, profile);
			}
		}
		resp.add("profiles", profiles);

		// Update login time
		acc.updateLastLoginTime();

		// Return
		return ok("text/json", resp.toString());
	}

	@Function
	public FunctionResult getAccountDetails(FunctionInfo func) throws IOException {
		// Account details

		// Check authentication
		if (!func.getRequest().hasHeader("Authorization")
				|| !func.getRequest().getHeader("Authorization").getValue().toLowerCase().startsWith("bearer ")) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Missing authorization token\"}");
		}

		// Read token
		String token = func.getRequest().getHeader("Authorization").getValue().substring("bearer ".length());
		AccessContext ctx = TokenUtils.fromToken(token);
		if (ctx == null || !ctx.isAccount) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Authorization token invalid\"}");
		}

		// Build response
		JsonObject resp = new JsonObject();
		resp.addProperty("accountId", ctx.account.getAccountID());
		resp.addProperty("accountUsername", ctx.account.getUsername());
		resp.addProperty("accountEmail", ctx.account.getAccountEmail());
		resp.addProperty("lastLoginTime", ctx.account.getLastLoginTime());
		resp.addProperty("registrationTime", ctx.account.getRegistrationTimestamp());
		resp.addProperty("multiplayerEnabled", ctx.account.isMultiplayerEnabled());
		resp.addProperty("chatEnabled", ctx.account.isChatEnabled());
		resp.addProperty("strictChat", ctx.account.isStrictChatFilterEnabled());

		// Add profiles
		JsonObject profiles = new JsonObject();
		for (String save : ctx.account.getSaveIDs()) {
			AccountSaveContainer sv = ctx.account.getSave(save);
			if (sv != null) {
				JsonObject profile = new JsonObject();
				profile.addProperty("username", sv.getUsername());
				profile.addProperty("creationTime", sv.getCreationTime());
				profiles.add(save, profile);
			}
		}
		resp.add("profiles", profiles);

		// Return
		return ok("text/json", resp.toString());
	}

	@Function(allowedMethods = { "POST" })
	public FunctionResult updateAccount(FunctionInfo func) throws IOException {
		// Save details

		// Check authentication
		if (!func.getRequest().hasHeader("Authorization")
				|| !func.getRequest().getHeader("Authorization").getValue().toLowerCase().startsWith("bearer ")) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Missing authorization token\"}");
		}

		// Read token
		String token = func.getRequest().getHeader("Authorization").getValue().substring("bearer ".length());
		AccessContext ctx = TokenUtils.fromToken(token);
		if (ctx == null || !ctx.isAccount) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Authorization token invalid\"}");
		}

		// Load request
		JsonObject request;
		try {
			// Parse and check
			request = JsonParser.parseString(func.getRequest().getRequestBodyAsString()).getAsJsonObject();
		} catch (Exception e) {
			// Bad request
			return response(400, "Bad request", "text/json", "{\"error\":\"Invalid request body\"}");
		}

		// Find account manager
		AccountManager manager = AccountManager.getInstance();

		// Update account
		if (request.has("accountUsername")) {
			String username = request.get("accountUsername").getAsString();

			// Check validity
			if (!manager.isValidUsername(username)) {
				// Invalid name
				return response(400, "Bad request", "text/json", "{\"error\":\"invalid_username\"}");
			}

			// Check filters
			if (TextFilterService.getInstance().isFiltered(username, true)) {
				// Invalid name
				return response(400, "Bad request", "text/json", "{\"error\":\"imappropriate_username\"}");
			}

			// Check if in use
			boolean inUse = false;
			if (!ctx.account.getUsername().equalsIgnoreCase(username) && manager.isUsernameTaken(username)) {
				inUse = true;
			} else {
				// Check if in use by any saves
				AccountObject accF = ctx.account;
				if (Stream.of(ctx.account.getSaveIDs()).map(t -> accF.getSave(t)).anyMatch(t -> {
					return t.getUsername().equalsIgnoreCase(username);
				})) {
					inUse = true;
				}
			}
			if (inUse) {
				return response(400, "Bad request", "text/json", "{\"error\":\"username_in_use\"}");
			}

			// Update
			if (!ctx.account.updateUsername(username))
				return response(500, "Internal server error", "text/json", "{\"error\":\"server_error\"}");
			ctx.account.getAccountData().getChildContainer("accountdata").setEntry("last_update",
					new JsonPrimitive(System.currentTimeMillis()));
		}
		if (request.has("accountEmail")) {
			String email = request.get("accountEmail").getAsString();

			// Verify email
			if (!email.toLowerCase().matches(
					"(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])")) {
				// Error
				return response(400, "Bad request", "text/json", "{\"error\":\"invalid_email\"}");
			}

			// Check if email is taken
			if (manager.getAccountIDByEmail(email) != null) {
				// Error
				return response(400, "Bad request", "text/json", "{\"error\":\"email_in_use\"}");
			}

			// Update
			if (!ctx.account.updateEmail(email))
				return response(500, "Internal server error", "text/json", "{\"error\":\"server_error\"}");
			ctx.account.getAccountData().getChildContainer("accountdata").setEntry("last_update",
					new JsonPrimitive(System.currentTimeMillis()));
		}
		if (request.has("accountPassword")) {
			String password = request.get("accountPassword").getAsString();

			// Check password validity
			if (!manager.isValidPassword(password)) {
				// Error
				return response(400, "Bad request", "text/json", "{\"error\":\"invalid_password\"}");
			}

			// Update
			if (!ctx.account.updatePassword(password.toCharArray()))
				return response(500, "Internal server error", "text/json", "{\"error\":\"server_error\"}");
			ctx.account.getAccountData().getChildContainer("accountdata").setEntry("last_update",
					new JsonPrimitive(System.currentTimeMillis()));
		}
		if (request.has("multiplayerEnabled")) {
			ctx.account.setMultiplayerEnabled(request.get("multiplayerEnabled").getAsBoolean());
		}
		if (request.has("chatEnabled")) {
			ctx.account.setMultiplayerEnabled(request.get("chatEnabled").getAsBoolean());
		}
		if (request.has("strictChat")) {
			ctx.account.setMultiplayerEnabled(request.get("strictChat").getAsBoolean());
		}

		// Build response
		JsonObject resp = new JsonObject();
		resp.addProperty("accountId", ctx.account.getAccountID());
		resp.addProperty("accountUsername", ctx.account.getUsername());
		resp.addProperty("accountEmail", ctx.account.getAccountEmail());
		resp.addProperty("lastLoginTime", ctx.account.getLastLoginTime());
		resp.addProperty("registrationTime", ctx.account.getRegistrationTimestamp());
		resp.addProperty("multiplayerEnabled", ctx.account.isMultiplayerEnabled());
		resp.addProperty("chatEnabled", ctx.account.isChatEnabled());
		resp.addProperty("strictChat", ctx.account.isStrictChatFilterEnabled());

		// Add profiles
		JsonObject profiles = new JsonObject();
		for (String save : ctx.account.getSaveIDs()) {
			AccountSaveContainer sv = ctx.account.getSave(save);
			if (sv != null) {
				JsonObject profile = new JsonObject();
				profile.addProperty("username", sv.getUsername());
				profile.addProperty("creationTime", sv.getCreationTime());
				profiles.add(save, profile);
			}
		}
		resp.add("profiles", profiles);

		// Return
		return ok("text/json", resp.toString());
	}

	@Function
	public FunctionResult invalidateAllSessions(FunctionInfo func) throws IOException {
		// Invalidate sessions

		// Check authentication
		if (!func.getRequest().hasHeader("Authorization")
				|| !func.getRequest().getHeader("Authorization").getValue().toLowerCase().startsWith("bearer ")) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Missing authorization token\"}");
		}

		// Read token
		String token = func.getRequest().getHeader("Authorization").getValue().substring("bearer ".length());
		AccessContext ctx = TokenUtils.fromToken(token);
		if (ctx == null || !ctx.isAccount) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Authorization token invalid\"}");
		}

		// Reset sessions
		ctx.account.getAccountData().getChildContainer("accountdata").setEntry("last_update",
				new JsonPrimitive(System.currentTimeMillis()));

		// Return
		JsonObject resp = new JsonObject();
		resp.addProperty("invalidatedSessions", true);
		return ok("text/json", resp.toString());
	}

	@Function
	public FunctionResult deleteAccount(FunctionInfo func) throws IOException {
		// Save details

		// Check authentication
		if (!func.getRequest().hasHeader("Authorization")
				|| !func.getRequest().getHeader("Authorization").getValue().toLowerCase().startsWith("bearer ")) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Missing authorization token\"}");
		}

		// Read token
		String token = func.getRequest().getHeader("Authorization").getValue().substring("bearer ".length());
		AccessContext ctx = TokenUtils.fromToken(token);
		if (ctx == null || !ctx.isAccount) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Authorization token invalid\"}");
		}

		// Delete
		ctx.account.deleteAccount();

		// Build response
		JsonObject resp = new JsonObject();
		resp.addProperty("deleted", true);

		// Return
		return ok("text/json", resp.toString());
	}

	@Function(allowedMethods = { "POST" })
	public FunctionResult createSave(FunctionInfo func) throws IOException {
		// Save details

		// Check authentication
		if (!func.getRequest().hasHeader("Authorization")
				|| !func.getRequest().getHeader("Authorization").getValue().toLowerCase().startsWith("bearer ")) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Missing authorization token\"}");
		}

		// Read token
		String token = func.getRequest().getHeader("Authorization").getValue().substring("bearer ".length());
		AccessContext ctx = TokenUtils.fromToken(token);
		if (ctx == null || !ctx.isAccount) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Authorization token invalid\"}");
		}

		// Load request
		JsonObject request;
		try {
			// Parse and check
			request = JsonParser.parseString(func.getRequest().getRequestBodyAsString()).getAsJsonObject();
			if (!request.has("saveUsername"))
				throw new IOException();
		} catch (Exception e) {
			// Bad request
			return response(400, "Bad request", "text/json",
					"{\"error\":\"Invalid request body, expecting a JSON with a 'saveUsername' field\"}");
		}

		// Find account manager
		AccountManager manager = AccountManager.getInstance();

		// Pull save username
		String saveUsername = request.get("saveUsername").getAsString();

		// Check validity
		if (!manager.isValidUsername(saveUsername)) {
			// Invalid name
			return response(400, "Bad request", "text/json", "{\"error\":\"invalid_username\"}");
		}

		// Check filters
		if (TextFilterService.getInstance().isFiltered(saveUsername, true)) {
			// Invalid name
			return response(400, "Bad request", "text/json", "{\"error\":\"imappropriate_username\"}");
		}

		// Check if in use
		boolean inUse = false;
		if (!ctx.account.getUsername().equalsIgnoreCase(saveUsername) && manager.isUsernameTaken(saveUsername)) {
			inUse = true;
		} else {
			// Check if in use by any saves
			AccountObject accF = ctx.account;
			if (Stream.of(ctx.account.getSaveIDs()).map(t -> accF.getSave(t)).anyMatch(t -> {
				return t.getUsername().equalsIgnoreCase(saveUsername);
			})) {
				inUse = true;
			}
		}
		if (inUse) {
			// Invalid name
			return response(400, "Bad request", "text/json", "{\"error\":\"username_in_use\"}");
		}

		// Create save
		AccountSaveContainer save = ctx.account.createSave(saveUsername);
		if (save == null) {
			// Error
			return response(500, "Internal server error", "text/json", "{\"error\":\"save_creation_failure\"}");
		}

		// Build response
		JsonObject resp = new JsonObject();
		resp.addProperty("accountId", ctx.account.getAccountID());
		resp.addProperty("accountUsername", ctx.account.getUsername());
		resp.addProperty("saveId", save.getSaveID());
		resp.addProperty("saveUsername", save.getUsername());
		resp.addProperty("lastLoginTime", ctx.account.getLastLoginTime());
		resp.addProperty("registrationTime", ctx.account.getRegistrationTimestamp());
		resp.addProperty("saveCreationTime", save.getCreationTime());
		resp.addProperty("multiplayerEnabled", ctx.account.isMultiplayerEnabled());
		resp.addProperty("chatEnabled", ctx.account.isChatEnabled());
		resp.addProperty("strictChat", ctx.account.isStrictChatFilterEnabled());

		// Return
		return ok("text/json", resp.toString());
	}

	@Function(allowedMethods = { "POST" })
	public FunctionResult updateSave(FunctionInfo func) throws IOException {
		// Save details

		// Check authentication
		if (!func.getRequest().hasHeader("Authorization")
				|| !func.getRequest().getHeader("Authorization").getValue().toLowerCase().startsWith("bearer ")) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Missing authorization token\"}");
		}

		// Read token
		String token = func.getRequest().getHeader("Authorization").getValue().substring("bearer ".length());
		AccessContext ctx = TokenUtils.fromToken(token);
		if (ctx == null || !ctx.isAccount) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Authorization token invalid\"}");
		}

		// Load request
		JsonObject request;
		try {
			// Parse and check
			request = JsonParser.parseString(func.getRequest().getRequestBodyAsString()).getAsJsonObject();
			if (!request.has("saveID"))
				throw new IOException();
		} catch (Exception e) {
			// Bad request
			return response(400, "Bad request", "text/json",
					"{\"error\":\"Invalid request body, expecting a JSON with at least a 'saveID' field\"}");
		}

		// Pull save ID
		String saveID = request.get("saveID").getAsString();

		// Locate save
		AccountSaveContainer save = ctx.account.getSave(saveID);
		if (save == null) {
			// Error
			return response(404, "Not found", "text/json", "{\"error\":\"save_not_found\"}");
		}

		// Find account manager
		AccountManager manager = AccountManager.getInstance();

		// Update save
		if (request.has("saveUsername")) {
			String username = request.get("saveUsername").getAsString();

			// Check validity
			if (!manager.isValidUsername(username)) {
				// Invalid name
				return response(400, "Bad request", "text/json", "{\"error\":\"invalid_username\"}");
			}

			// Check filters
			if (TextFilterService.getInstance().isFiltered(username, true)) {
				// Invalid name
				return response(400, "Bad request", "text/json", "{\"error\":\"imappropriate_username\"}");
			}

			// Check if in use
			boolean inUse = false;
			if (!ctx.account.getUsername().equalsIgnoreCase(username) && manager.isUsernameTaken(username)) {
				inUse = true;
			} else {
				// Check if in use by any saves
				AccountObject accF = ctx.account;
				if (Stream.of(ctx.account.getSaveIDs()).map(t -> accF.getSave(t)).anyMatch(t -> {
					return t.getUsername().equalsIgnoreCase(username);
				})) {
					inUse = true;
				}
			}
			if (inUse) {
				return response(400, "Bad request", "text/json", "{\"error\":\"username_in_use\"}");
			}

			// Update
			if (!save.updateUsername(username))
				return response(500, "Internal server error", "text/json", "{\"error\":\"server_error\"}");
		}

		// Build response
		JsonObject resp = new JsonObject();
		resp.addProperty("accountId", ctx.account.getAccountID());
		resp.addProperty("accountUsername", ctx.account.getUsername());
		resp.addProperty("saveId", save.getSaveID());
		resp.addProperty("saveUsername", save.getUsername());
		resp.addProperty("lastLoginTime", ctx.account.getLastLoginTime());
		resp.addProperty("registrationTime", ctx.account.getRegistrationTimestamp());
		resp.addProperty("saveCreationTime", save.getCreationTime());
		resp.addProperty("multiplayerEnabled", ctx.account.isMultiplayerEnabled());
		resp.addProperty("chatEnabled", ctx.account.isChatEnabled());
		resp.addProperty("strictChat", ctx.account.isStrictChatFilterEnabled());

		// Return
		return ok("text/json", resp.toString());
	}

	@Function(allowedMethods = { "POST" })
	public FunctionResult getSaveDetails(FunctionInfo func) throws IOException {
		// Save details

		// Check authentication
		if (!func.getRequest().hasHeader("Authorization")
				|| !func.getRequest().getHeader("Authorization").getValue().toLowerCase().startsWith("bearer ")) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Missing authorization token\"}");
		}

		// Read token
		String token = func.getRequest().getHeader("Authorization").getValue().substring("bearer ".length());
		AccessContext ctx = TokenUtils.fromToken(token);
		if (ctx == null || !ctx.isAccount) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Authorization token invalid\"}");
		}

		// Load request
		JsonObject request;
		try {
			// Parse and check
			request = JsonParser.parseString(func.getRequest().getRequestBodyAsString()).getAsJsonObject();
			if (!request.has("saveID"))
				throw new IOException();
		} catch (Exception e) {
			// Bad request
			return response(400, "Bad request", "text/json",
					"{\"error\":\"Invalid request body, expecting a JSON with a 'saveID' field\"}");
		}

		// Pull save ID
		String saveID = request.get("saveID").getAsString();

		// Locate save
		AccountSaveContainer save = ctx.account.getSave(saveID);
		if (save == null) {
			// Error
			return response(404, "Not found", "text/json", "{\"error\":\"save_not_found\"}");
		}

		// Build response
		JsonObject resp = new JsonObject();
		resp.addProperty("accountId", ctx.account.getAccountID());
		resp.addProperty("accountUsername", ctx.account.getUsername());
		resp.addProperty("saveId", save.getSaveID());
		resp.addProperty("saveUsername", save.getUsername());
		resp.addProperty("lastLoginTime", ctx.account.getLastLoginTime());
		resp.addProperty("registrationTime", ctx.account.getRegistrationTimestamp());
		resp.addProperty("saveCreationTime", save.getCreationTime());
		resp.addProperty("multiplayerEnabled", ctx.account.isMultiplayerEnabled());
		resp.addProperty("chatEnabled", ctx.account.isChatEnabled());
		resp.addProperty("strictChat", ctx.account.isStrictChatFilterEnabled());

		// Return
		return ok("text/json", resp.toString());
	}

	@Function(allowedMethods = { "POST" })
	public FunctionResult deleteSave(FunctionInfo func) throws IOException {
		// Save details

		// Check authentication
		if (!func.getRequest().hasHeader("Authorization")
				|| !func.getRequest().getHeader("Authorization").getValue().toLowerCase().startsWith("bearer ")) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Missing authorization token\"}");
		}

		// Read token
		String token = func.getRequest().getHeader("Authorization").getValue().substring("bearer ".length());
		AccessContext ctx = TokenUtils.fromToken(token);
		if (ctx == null || !ctx.isAccount) {
			// Error
			return response(401, "Unauthorized", "text/json", "{\"error\":\"Authorization token invalid\"}");
		}

		// Load request
		JsonObject request;
		try {
			// Parse and check
			request = JsonParser.parseString(func.getRequest().getRequestBodyAsString()).getAsJsonObject();
			if (!request.has("saveID"))
				throw new IOException();
		} catch (Exception e) {
			// Bad request
			return response(400, "Bad request", "text/json",
					"{\"error\":\"Invalid request body, expecting a JSON with a 'saveID' field\"}");
		}

		// Pull save ID
		String saveID = request.get("saveID").getAsString();

		// Locate save
		AccountSaveContainer save = ctx.account.getSave(saveID);
		if (save == null) {
			// Error
			return response(404, "Not found", "text/json", "{\"error\":\"save_not_found\"}");
		}

		// Delete
		save.deleteSave();

		// Build response
		JsonObject resp = new JsonObject();
		resp.addProperty("deleted", true);

		// Return
		return ok("text/json", resp.toString());
	}

	@Function(allowedMethods = { "POST" })
	public FunctionResult textFilter(FunctionInfo func) {
		// Edge text filter

		// Load request
		JsonObject request;
		try {
			// Parse and check
			request = JsonParser.parseString(func.getRequest().getRequestBodyAsString()).getAsJsonObject();
			if (!request.has("message") || !request.has("strictMode"))
				throw new IOException();
		} catch (Exception e) {
			// Bad request
			return response(400, "Bad request");
		}

		// Run filter
		String message = request.get("message").getAsString();
		boolean strictMode = request.get("strictMode").getAsBoolean();
		FilterResult result = TextFilterService.getInstance().filter(message, strictMode);

		// Build response match list
		JsonArray matches = new JsonArray();
		for (WordMatch match : result.getMatches()) {
			JsonObject m = new JsonObject();
			m.addProperty("phrase", match.getPhraseFilter().getPhrase());
			m.addProperty("matchedPhrase", match.getMatchedPhrase());
			m.addProperty("reason", match.getReason());
			m.addProperty("severity", match.getSeverity().toString());
			matches.add(m);
		}

		// Build response
		JsonObject res = new JsonObject();
		res.addProperty("isFiltered", result.isMatch());
		res.addProperty("resultMessage", result.getFilterResult());
		res.addProperty("resultSeverity", result.getSeverity().toString());
		res.add("matches", matches);

		// Return result
		return ok("text/json", res.toString());
	}

}

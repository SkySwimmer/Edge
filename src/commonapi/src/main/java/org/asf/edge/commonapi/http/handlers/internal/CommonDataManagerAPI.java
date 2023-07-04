package org.asf.edge.commonapi.http.handlers.internal;

import java.io.IOException;

import org.asf.connective.processors.HttpPushProcessor;
import org.asf.edge.common.http.apihandlerutils.BaseApiHandler;
import org.asf.edge.common.http.apihandlerutils.functions.Function;
import org.asf.edge.common.http.apihandlerutils.functions.FunctionInfo;
import org.asf.edge.common.services.commondata.CommonDataContainer;
import org.asf.edge.common.services.commondata.CommonDataManager;
import org.asf.edge.commonapi.EdgeCommonApiServer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class CommonDataManagerAPI extends BaseApiHandler<EdgeCommonApiServer> {

	private CommonDataManager manager;

	public CommonDataManagerAPI(EdgeCommonApiServer server) {
		super(server);
	}

	@Override
	public HttpPushProcessor createNewInstance() {
		return new CommonDataManagerAPI(getServerInstance());
	}

	@Override
	public String path() {
		return "/commondatamanager";
	}

	@Function(allowedMethods = { "POST" })
	public void setupContainer(FunctionInfo func) throws JsonSyntaxException, IOException {
		// Load manager
		if (manager == null)
			manager = CommonDataManager.getInstance();

		// Read payload
		if (!getRequest().hasRequestBody()) {
			getResponse().setResponseStatus(400, "Bad request");
			return;
		}
		JsonObject payload = JsonParser.parseString(getRequestBodyAsString()).getAsJsonObject();
		if (!payload.has("node")) {
			getResponse().setResponseStatus(400, "Bad request");
			return;
		}

		// Send response
		JsonObject resp = new JsonObject();
		manager.getContainer(payload.get("node").getAsString());
		resp.addProperty("success", true);
		setResponseContent("text/json", resp.toString());
	}

	@Function(allowedMethods = { "POST" })
	public void getDataEntry(FunctionInfo func) throws JsonSyntaxException, IOException {
		// Load manager
		if (manager == null)
			manager = CommonDataManager.getInstance();

		// Read payload
		if (!getRequest().hasRequestBody()) {
			getResponse().setResponseStatus(400, "Bad request");
			return;
		}
		JsonObject payload = JsonParser.parseString(getRequestBodyAsString()).getAsJsonObject();
		if (!payload.has("node") || !payload.has("key")) {
			getResponse().setResponseStatus(400, "Bad request");
			return;
		}

		// Send response
		CommonDataContainer data = manager.getContainer(payload.get("node").getAsString());
		JsonObject resp = new JsonObject();
		if (data != null) {
			JsonElement val = data.unsafeAccessor().get(payload.get("key").getAsString());
			resp.addProperty("success", val != null);
			if (val != null)
				resp.add("entryValue", val);
		} else
			resp.addProperty("success", false);
		setResponseContent("text/json", resp.toString());
	}

	@Function(allowedMethods = { "POST" })
	public void setDataEntry(FunctionInfo func) throws JsonSyntaxException, IOException {
		// Load manager
		if (manager == null)
			manager = CommonDataManager.getInstance();

		// Read payload
		if (!getRequest().hasRequestBody()) {
			getResponse().setResponseStatus(400, "Bad request");
			return;
		}
		JsonObject payload = JsonParser.parseString(getRequestBodyAsString()).getAsJsonObject();
		if (!payload.has("node") || !payload.has("key") || !payload.has("value")) {
			getResponse().setResponseStatus(400, "Bad request");
			return;
		}

		// Send response
		CommonDataContainer data = manager.getContainer(payload.get("node").getAsString());
		JsonObject resp = new JsonObject();
		if (data != null) {
			data.unsafeAccessor().set(payload.get("key").getAsString(), payload.get("value"));
			resp.addProperty("success", true);
		} else
			resp.addProperty("success", false);
		setResponseContent("text/json", resp.toString());
	}

	@Function(allowedMethods = { "POST" })
	public void createDataEntry(FunctionInfo func) throws JsonSyntaxException, IOException {
		// Load manager
		if (manager == null)
			manager = CommonDataManager.getInstance();

		// Read payload
		if (!getRequest().hasRequestBody()) {
			getResponse().setResponseStatus(400, "Bad request");
			return;
		}
		JsonObject payload = JsonParser.parseString(getRequestBodyAsString()).getAsJsonObject();
		if (!payload.has("node") || !payload.has("key") || !payload.has("value")) {
			getResponse().setResponseStatus(400, "Bad request");
			return;
		}

		// Send response
		CommonDataContainer data = manager.getContainer(payload.get("node").getAsString());
		JsonObject resp = new JsonObject();
		if (data != null) {
			data.unsafeAccessor().create(payload.get("key").getAsString(), payload.get("value"));
			resp.addProperty("success", true);
		} else
			resp.addProperty("success", false);
		setResponseContent("text/json", resp.toString());
	}

	@Function(allowedMethods = { "POST" })
	public void dataEntryExists(FunctionInfo func) throws JsonSyntaxException, IOException {
		// Load manager
		if (manager == null)
			manager = CommonDataManager.getInstance();

		// Read payload
		if (!getRequest().hasRequestBody()) {
			getResponse().setResponseStatus(400, "Bad request");
			return;
		}
		JsonObject payload = JsonParser.parseString(getRequestBodyAsString()).getAsJsonObject();
		if (!payload.has("node") || !payload.has("key")) {
			getResponse().setResponseStatus(400, "Bad request");
			return;
		}

		// Send response
		CommonDataContainer data = manager.getContainer(payload.get("node").getAsString());
		JsonObject resp = new JsonObject();
		if (data != null) {
			resp.addProperty("result", data.unsafeAccessor().exists(payload.get("key").getAsString()));
		} else
			resp.addProperty("result", false);
		setResponseContent("text/json", resp.toString());
	}

	@Function(allowedMethods = { "POST" })
	public void deleteDataEntry(FunctionInfo func) throws JsonSyntaxException, IOException {
		// Load manager
		if (manager == null)
			manager = CommonDataManager.getInstance();

		// Read payload
		if (!getRequest().hasRequestBody()) {
			getResponse().setResponseStatus(400, "Bad request");
			return;
		}
		JsonObject payload = JsonParser.parseString(getRequestBodyAsString()).getAsJsonObject();
		if (!payload.has("node") || !payload.has("key")) {
			getResponse().setResponseStatus(400, "Bad request");
			return;
		}

		// Send response
		CommonDataContainer data = manager.getContainer(payload.get("node").getAsString());
		JsonObject resp = new JsonObject();
		if (data != null) {
			data.unsafeAccessor().delete(payload.get("key").getAsString());
			resp.addProperty("success", true);
		} else
			resp.addProperty("success", false);
		setResponseContent("text/json", resp.toString());
	}

}
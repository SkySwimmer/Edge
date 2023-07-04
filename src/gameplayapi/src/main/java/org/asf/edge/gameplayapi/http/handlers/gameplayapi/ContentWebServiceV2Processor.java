package org.asf.edge.gameplayapi.http.handlers.gameplayapi;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.asf.connective.RemoteClient;
import org.asf.connective.processors.HttpPushProcessor;
import org.asf.edge.common.entities.items.ItemInfo;
import org.asf.edge.common.entities.items.PlayerInventory;
import org.asf.edge.common.entities.items.PlayerInventoryContainer;
import org.asf.edge.common.entities.items.PlayerInventoryItem;
import org.asf.edge.common.http.apihandlerutils.BaseApiHandler;
import org.asf.edge.common.http.apihandlerutils.functions.Function;
import org.asf.edge.common.http.apihandlerutils.functions.FunctionInfo;
import org.asf.edge.common.services.accounts.AccountDataContainer;
import org.asf.edge.common.services.accounts.AccountManager;
import org.asf.edge.common.services.accounts.AccountObject;
import org.asf.edge.common.services.accounts.AccountSaveContainer;
import org.asf.edge.common.services.items.ItemManager;
import org.asf.edge.common.tokens.SessionToken;
import org.asf.edge.common.tokens.TokenParseResult;
import org.asf.edge.gameplayapi.EdgeGameplayApiServer;
import org.asf.edge.gameplayapi.entities.quests.UserQuestInfo;
import org.asf.edge.gameplayapi.services.quests.QuestManager;
import org.asf.edge.gameplayapi.xmls.avatars.SetAvatarResultData;
import org.asf.edge.gameplayapi.xmls.dragons.CreatePetResponseData;
import org.asf.edge.gameplayapi.xmls.dragons.DragonListData;
import org.asf.edge.gameplayapi.xmls.dragons.PetCreateRequestData;
import org.asf.edge.gameplayapi.xmls.inventories.CommonInventoryData;
import org.asf.edge.gameplayapi.xmls.inventories.CommonInventoryRequestData;
import org.asf.edge.gameplayapi.xmls.inventories.InventoryUpdateResponseData;
import org.asf.edge.gameplayapi.xmls.inventories.SetCommonInventoryRequestData;
import org.asf.edge.gameplayapi.xmls.inventories.CommonInventoryData.ItemBlock;
import org.asf.edge.gameplayapi.xmls.inventories.InventoryUpdateResponseData.ItemUpdateBlock;
import org.asf.edge.gameplayapi.xmls.names.DisplayNameUniqueResponseData.SuggestionResultBlock;
import org.asf.edge.gameplayapi.xmls.names.NameValidationRequest;
import org.asf.edge.gameplayapi.xmls.names.NameValidationResponseData;
import org.asf.edge.gameplayapi.xmls.quests.MissionData;
import org.asf.edge.gameplayapi.xmls.quests.QuestListResponseData;
import org.asf.edge.gameplayapi.xmls.quests.RequestFilterData;
import org.asf.edge.gameplayapi.xmls.quests.RequestFilterData.MissionPairBlock;
import org.asf.edge.gameplayapi.xmls.quests.SetTaskStateResultData;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class ContentWebServiceV2Processor extends BaseApiHandler<EdgeGameplayApiServer> {

	private static AccountManager manager;
	private static ItemManager itemManager;
	private static QuestManager questManager;
	private static Random rnd = new Random();

	public ContentWebServiceV2Processor(EdgeGameplayApiServer server) {
		super(server);
	}

	@Override
	public HttpPushProcessor createNewInstance() {
		return new ContentWebServiceV2Processor(getServerInstance());
	}

	@Override
	public String path() {
		return "/v2/ContentWebService.asmx";
	}

	@Override
	public void fallbackRequestProcessor(String path, String method, RemoteClient client, String contentType)
			throws IOException {
		// Handle request
		path = path;
		setResponseStatus(404, "Not found");
	}

	@Function(allowedMethods = { "POST" })
	public void getGameData(FunctionInfo func) throws IOException {
		// Handle game data request
		ServiceRequestInfo req = getUtilities().getServiceRequestPayload(getServerInstance().getLogger());
		if (req == null)
			return;
		String apiToken = getUtilities().decodeToken(req.payload.get("apiToken").toUpperCase());

		// Read token
		SessionToken tkn = new SessionToken();
		TokenParseResult res = tkn.parseToken(apiToken);
		AccountObject account = tkn.account;
		if (res != TokenParseResult.SUCCESS || !tkn.hasCapability("gp")) {
			// Error
			setResponseStatus(404, "Not found");
			return;
		}

		// TODO: stub
		setResponseContent("text/xml", "<GetGameDataResponse />");
	}

	@Function(allowedMethods = { "POST" })
	public void setTaskState(FunctionInfo func) throws IOException {
		if (manager == null)
			manager = AccountManager.getInstance();
		if (itemManager == null)
			itemManager = ItemManager.getInstance();
		if (questManager == null)
			questManager = QuestManager.getInstance();

		// Handle quest data request
		ServiceRequestInfo req = getUtilities().getServiceRequestPayload(getServerInstance().getLogger());
		if (req == null)
			return;
		String apiToken = getUtilities().decodeToken(req.payload.get("apiToken").toUpperCase());

		// Read token
		SessionToken tkn = new SessionToken();
		TokenParseResult res = tkn.parseToken(apiToken);
		AccountObject account = tkn.account;
		if (res != TokenParseResult.SUCCESS) {
			// Error
			setResponseStatus(404, "Not found");
			return;
		}

		// Parse request
		String userID = req.payload.get("userId");

		// Retrieve container
		AccountSaveContainer save = account.getSave(userID);
		if (save != null) {
			// Load fields
			int invContainer = Integer.parseInt(req.payload.get("ContainerId"));
			int taskID = Integer.parseInt(req.payload.get("taskId"));
			int missionId = Integer.parseInt(req.payload.get("missionId"));
			boolean completed = req.payload.get("completed").equalsIgnoreCase("true");
			String payload = req.payload.get("xmlPayload");

			// Load inventory requests
			String invReq = req.payload.get("commonInventoryRequestXml");
			SetCommonInventoryRequestData[] requests;
			if (!invReq.trim().equals("<?xml version=\"1.0\" encoding=\"utf-8\"?>"))
				requests = req.parseXmlValue(invReq, SetCommonInventoryRequestData[].class);
			else
				requests = new SetCommonInventoryRequestData[0];

			// Find quest
			UserQuestInfo quest = questManager.getUserQuest(save, missionId);
			if (quest != null) {
				setResponseContent("text/xml", req.generateXmlValue("SetTaskStateResult",
						quest.handleTaskCall(taskID, payload, completed, invContainer, requests)));
			} else {
				// Not found
				SetTaskStateResultData resp = new SetTaskStateResultData();
				resp.success = false;
				resp.status = SetTaskStateResultData.SetTaskStateResultStatuses.MISSION_STATE_NOT_FOUND;
				setResponseContent("text/xml", req.generateXmlValue("SetTaskStateResult", resp));
			}
		} else {
			setResponseStatus(404, "Not found");
		}
	}

	@Function(allowedMethods = { "POST" })
	public void getUserMissionState(FunctionInfo func) throws IOException {
		if (manager == null)
			manager = AccountManager.getInstance();
		if (itemManager == null)
			itemManager = ItemManager.getInstance();
		if (questManager == null)
			questManager = QuestManager.getInstance();

		// Handle quest data request
		ServiceRequestInfo req = getUtilities().getServiceRequestPayload(getServerInstance().getLogger());
		if (req == null)
			return;
		String apiToken = getUtilities().decodeToken(req.payload.get("apiToken").toUpperCase());

		// Read token
		SessionToken tkn = new SessionToken();
		TokenParseResult res = tkn.parseToken(apiToken);
		AccountObject account = tkn.account;
		if (res != TokenParseResult.SUCCESS) {
			// Error
			setResponseStatus(404, "Not found");
			return;
		}

		// Parse request
		String userID = req.payload.get("userId");

		// Retrieve container
		AccountSaveContainer save = account.getSave(userID);
		if (save != null) {
			// Pull quests
			MissionData[] quests = questManager.getAllQuestDefs();

			// Parse filters
			RequestFilterData filter = req.parseXmlValue(req.payload.get("filter"), RequestFilterData.class);

			// Create response
			ArrayList<Integer> addedQuests = new ArrayList<Integer>();
			ArrayList<MissionData> questLst = new ArrayList<MissionData>();
			QuestListResponseData resp = new QuestListResponseData();
			resp.userID = userID;

			// Apply ID filters
			if (filter.missions != null && filter.missions.length != 0) {
				for (MissionPairBlock pair : filter.missions) {
					// Check ID
					if (addedQuests.contains(pair.missionID))
						continue;

					// Find quest
					UserQuestInfo quest = questManager.getUserQuest(save, pair.missionID);
					if (quest != null && (filter.getCompletedMissions || !quest.isCompleted())) {
						// Add if its the right version
						if (pair.versionID == -1 || quest.getDef().version == pair.versionID) {
							questLst.add(quest.getData());
							addedQuests.add(quest.getQuestID());
						}
					}
				}
			}

			// Apply group ID versions
			if (filter.groupIDs != null && filter.groupIDs.length != 0) {
				IntStream strm = IntStream.of(filter.groupIDs);
				for (MissionData data : quests) {
					// Check ID
					if (addedQuests.contains(data.id))
						continue;

					// Check group
					if (strm.anyMatch(t -> t == data.groupID)) {
						// Found a quest
						UserQuestInfo quest = questManager.getUserQuest(save, data.id);
						if (quest != null && (filter.getCompletedMissions || !quest.isCompleted())) {
							// Add
							questLst.add(quest.getData());
							addedQuests.add(quest.getQuestID());
						}
					}
				}
			}

			// Set response
			resp.quests = questLst.toArray(t -> new MissionData[t]);
			setResponseContent("text/xml", req.generateXmlValue("UserMissionStateResult", resp));
		} else {
			setResponseStatus(404, "Not found");
		}
	}

	@Function(allowedMethods = { "POST" })
	public void getUserCompletedMissionState(FunctionInfo func) throws IOException {
		if (manager == null)
			manager = AccountManager.getInstance();
		if (itemManager == null)
			itemManager = ItemManager.getInstance();
		if (questManager == null)
			questManager = QuestManager.getInstance();

		// Handle quest data request
		ServiceRequestInfo req = getUtilities().getServiceRequestPayload(getServerInstance().getLogger());
		if (req == null)
			return;
		String apiToken = getUtilities().decodeToken(req.payload.get("apiToken").toUpperCase());

		// Read token
		SessionToken tkn = new SessionToken();
		TokenParseResult res = tkn.parseToken(apiToken);
		AccountObject account = tkn.account;
		if (res != TokenParseResult.SUCCESS) {
			// Error
			setResponseStatus(404, "Not found");
			return;
		}

		// Parse request
		String userID = req.payload.get("userId");

		// Retrieve container
		AccountSaveContainer save = account.getSave(userID);
		if (save != null) {
			// Pull quests
			UserQuestInfo[] quests = questManager.getCompletedQuests(save);

			// Create response
			QuestListResponseData resp = new QuestListResponseData();
			resp.userID = userID;
			resp.quests = new MissionData[quests.length];

			// Add data
			for (int i = 0; i < resp.quests.length; i++) {
				MissionData data = quests[i].getData();
				resp.quests[i] = data;
			}

			// Set response
			setResponseContent("text/xml", req.generateXmlValue("UserMissionStateResult", resp));
		} else {
			setResponseStatus(404, "Not found");
		}
	}

	@Function(allowedMethods = { "POST" })
	public void getUserActiveMissionState(FunctionInfo func) throws IOException {
		if (manager == null)
			manager = AccountManager.getInstance();
		if (itemManager == null)
			itemManager = ItemManager.getInstance();
		if (questManager == null)
			questManager = QuestManager.getInstance();

		// Handle quest data request
		ServiceRequestInfo req = getUtilities().getServiceRequestPayload(getServerInstance().getLogger());
		if (req == null)
			return;
		String apiToken = getUtilities().decodeToken(req.payload.get("apiToken").toUpperCase());

		// Read token
		SessionToken tkn = new SessionToken();
		TokenParseResult res = tkn.parseToken(apiToken);
		AccountObject account = tkn.account;
		if (res != TokenParseResult.SUCCESS) {
			// Error
			setResponseStatus(404, "Not found");
			return;
		}

		// Parse request
		String userID = req.payload.get("userId");

		// Retrieve container
		AccountSaveContainer save = account.getSave(userID);
		if (save != null) {
			// Pull quests
			UserQuestInfo[] quests = questManager.getActiveQuests(save);

			// Create response
			QuestListResponseData resp = new QuestListResponseData();
			resp.userID = userID;
			resp.quests = new MissionData[quests.length];

			// Add data
			for (int i = 0; i < resp.quests.length; i++) {
				MissionData data = quests[i].getData();
				resp.quests[i] = data;
			}

			// Set response
			setResponseContent("text/xml", req.generateXmlValue("UserMissionStateResult", resp));
		} else {
			setResponseStatus(404, "Not found");
		}
	}

	@Function(allowedMethods = { "POST" })
	public void getUserUpcomingMissionState(FunctionInfo func) throws IOException {
		if (manager == null)
			manager = AccountManager.getInstance();
		if (itemManager == null)
			itemManager = ItemManager.getInstance();
		if (questManager == null)
			questManager = QuestManager.getInstance();

		// Handle quest data request
		ServiceRequestInfo req = getUtilities().getServiceRequestPayload(getServerInstance().getLogger());
		if (req == null)
			return;
		String apiToken = getUtilities().decodeToken(req.payload.get("apiToken").toUpperCase());

		// Read token
		SessionToken tkn = new SessionToken();
		TokenParseResult res = tkn.parseToken(apiToken);
		AccountObject account = tkn.account;
		if (res != TokenParseResult.SUCCESS) {
			// Error
			setResponseStatus(404, "Not found");
			return;
		}

		// Parse request
		String userID = req.payload.get("userId");

		// Retrieve container
		AccountSaveContainer save = account.getSave(userID);
		if (save != null) {
			// Pull quests
			UserQuestInfo[] quests = questManager.getUpcomingQuests(save);

			// Create response
			QuestListResponseData resp = new QuestListResponseData();
			resp.userID = userID;

			// Add data
			ArrayList<MissionData> questLst = new ArrayList<MissionData>();
			for (UserQuestInfo i : quests) {
				MissionData d = i.getDef();
				questLst.add(d);
			}
			resp.quests = questLst.toArray(t -> new MissionData[t]);

			// Set response
			setResponseContent("text/xml", req.generateXmlValue("UserMissionStateResult", resp));
		} else {
			setResponseStatus(404, "Not found");
		}
	}

	@Function(allowedMethods = { "POST" })
	public void setAvatar(FunctionInfo func) throws IOException {
		if (manager == null)
			manager = AccountManager.getInstance();
		if (itemManager == null)
			itemManager = ItemManager.getInstance();

		// Handle avatar change request
		ServiceRequestInfo req = getUtilities().getServiceRequestPayload(getServerInstance().getLogger());
		if (req == null)
			return;
		String apiToken = getUtilities().decodeToken(req.payload.get("apiToken").toUpperCase());

		// Read token
		SessionToken tkn = new SessionToken();
		TokenParseResult res = tkn.parseToken(apiToken);
		AccountObject account = tkn.account;
		if (res != TokenParseResult.SUCCESS || !tkn.hasCapability("gp")) {
			// Error
			setResponseStatus(404, "Not found");
			return;
		}

		// Find save
		AccountSaveContainer save = account.getSave(tkn.saveID);

		// Parse request
		ObjectNode aviData = req.parseXmlValue(req.payload.get("contentXML"), ObjectNode.class);

		// Find name
		String name = aviData.get("DisplayName").asText();

		// Check if not the same
		if (!name.equalsIgnoreCase(save.getUsername())) {
			// Check validity
			if (!manager.isValidUsername(name)) {
				// Invalid name
				SetAvatarResultData resp = new SetAvatarResultData();
				resp.success = false;
				resp.statusCode = 10;
				resp.suggestions = null;
				setResponseContent("text/xml", req.generateXmlValue("SetAvatarResult", resp));
				return;
			}

			// Check filters
			// FIXME: implement this, use the same error response as invalid names for this

			// Check if in use
			boolean inUse = false;
			if (!account.getUsername().equalsIgnoreCase(name) && manager.isUsernameTaken(name)) {
				inUse = true;
			} else {
				// Check if in use by any saves
				if (Stream.of(account.getSaveIDs()).map(t -> account.getSave(t)).anyMatch(t -> {
					try {
						return t.getUsername().equalsIgnoreCase(name) && t.getSaveData().entryExists("avatar");
					} catch (IOException e) {
						return false;
					}
				})) {
					inUse = true;
				}
			}
			if (inUse) {
				// Taken
				SetAvatarResultData resp = new SetAvatarResultData();
				resp.success = false;
				resp.statusCode = 10;
				resp.suggestions = new SuggestionResultBlock();

				// Generate suggestions
				// TODO: better suggestions
				Random rnd = new Random();
				ArrayList<String> suggestions = new ArrayList<String>();
				for (int i = 1000; i < 9999; i++) {
					if (suggestions.size() == 6)
						break;
					if (!manager.isUsernameTaken(name + rnd.nextInt(1000, 9999)))
						suggestions.add(name + rnd.nextInt(1000, 9999));
				}

				// Set response
				resp.suggestions.suggestions = suggestions.toArray(t -> new String[t]);
				setResponseContent("text/xml", req.generateXmlValue("SetAvatarResult", resp));
				return;
			}

			// Set username
			if (!save.updateUsername(name)) {
				// Invalid name
				SetAvatarResultData resp = new SetAvatarResultData();
				resp.success = false;
				resp.statusCode = 10;
				resp.suggestions = null;
				setResponseContent("text/xml", req.generateXmlValue("SetAvatarResult", resp));
				return;
			}
		}

		// Update avatar
		save.getSaveData().setEntry("avatar", new JsonPrimitive(req.payload.get("contentXML")));

		// Add default items if needed
		if (!save.getSaveData().entryExists("received_default_items")
				|| !save.getSaveData().getEntry("received_default_items").getAsBoolean()) {
			// Give default items
			save.getInventory().giveDefaultItems();
			save.getSaveData().setEntry("received_default_items", new JsonPrimitive(true));
		}

		// Send response
		SetAvatarResultData resp = new SetAvatarResultData();
		resp.statusCode = 1;
		resp.success = true;
		resp.displayName = name;
		resp.suggestions = null;
		setResponseContent("text/xml", req.generateXmlValue("SetAvatarResult", resp));
	}

	@Function(allowedMethods = { "POST" })
	public void validateName(FunctionInfo func) throws IOException {
		if (manager == null)
			manager = AccountManager.getInstance();

		// Handle name validation request, this only checks if the name is valid, tho,
		// for groups im not sure-
		//
		// For display names, actual validation happens in SetAvatar
		ServiceRequestInfo req = getUtilities().getServiceRequestPayload(getServerInstance().getLogger());
		if (req == null)
			return;
		String apiToken = getUtilities().decodeToken(req.payload.get("apiToken").toUpperCase());

		// Read token
		SessionToken tkn = new SessionToken();
		TokenParseResult res = tkn.parseToken(apiToken);
		if (res != TokenParseResult.SUCCESS) {
			// Error
			setResponseStatus(404, "Not found");
			return;
		}

		// Parse request
		NameValidationRequest request = req.parseXmlValue(req.payload.get("nameValidationRequest"),
				NameValidationRequest.class);
		NameValidationResponseData resp = new NameValidationResponseData();
		resp.result = 1;

		// Check name
		if (request.category == 4) {
			// Avatar

			// Check validity
			if (!manager.isValidUsername(request.name)) {
				// Invalid
				resp.result = 4;
				resp.errorMessage = "Invalid";
				setResponseContent("text/xml", req.generateXmlValue("NameValidationResponse", resp));
				return;
			}
		} else if (request.category == 3) {
			// Group
			// FIXME: implement this!

			// Check existence etc and filters
		} else if (request.category == 2) {
			// Dragon
			// FIXME: implement this!

			// Check filters etc
		} else if (request.category == 4) {
			// Default
			// FIXME: implement this!

			// Check filters etc
		}

		// Set response
		setResponseContent("text/xml", req.generateXmlValue("NameValidationResponse", resp));
	}

	@Function(allowedMethods = { "POST" })
	public void createPet(FunctionInfo func) throws IOException {
		if (manager == null)
			manager = AccountManager.getInstance();
		if (itemManager == null)
			itemManager = ItemManager.getInstance();

		// Handle dragon request
		ServiceRequestInfo req = getUtilities().getServiceRequestPayload(getServerInstance().getLogger());
		if (req == null)
			return;
		String apiToken = getUtilities().decodeToken(req.payload.get("apiToken").toUpperCase());

		// Read token
		SessionToken tkn = new SessionToken();
		TokenParseResult res = tkn.parseToken(apiToken);
		AccountObject account = tkn.account;
		if (res != TokenParseResult.SUCCESS || !tkn.hasCapability("gp")) {
			// Error
			setResponseStatus(404, "Not found");
			return;
		}

		// Parse request
		PetCreateRequestData request = req.parseXmlValue(req.payload.get("request"), PetCreateRequestData.class);

		// Find save
		AccountSaveContainer save = account.getSave(tkn.saveID);
		AccountDataContainer data = save.getSaveData();

		// Prepare response
		CreatePetResponseData resp = new CreatePetResponseData();

		// Pull dragons
		data = data.getChildContainer("dragons");
		JsonArray dragonIds = new JsonArray();
		if (data.entryExists("dragonlist"))
			dragonIds = data.getEntry("dragonlist").getAsJsonArray();
		else
			data.setEntry("dragonlist", dragonIds);

		// Create dragon ID
		String entID = UUID.randomUUID().toString();
		int id = rnd.nextInt(0, Integer.MAX_VALUE);
		while (data.entryExists("dragon-" + id))
			id = rnd.nextInt(0, Integer.MAX_VALUE);
		while (true) {
			boolean found = false;
			for (JsonElement ele : dragonIds) {
				String did = ele.getAsString();
				ObjectNode d = req.parseXmlValue(data.getEntry("dragon-" + did).getAsString(), ObjectNode.class);
				if (d.get("eid").asText().equals(entID)) {
					entID = UUID.randomUUID().toString();
					found = true;
					break;
				}
			}
			if (!found)
				break;
		}

		// Fill fields
		SimpleDateFormat fmt = new SimpleDateFormat("yyyy'-'MM'-'dd'T'HH':'mm':'ss");
		fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
		ObjectNode dragon = request.dragonData;
		dragon.set("id", new IntNode(id)); // ID
		dragon.set("ip", new IntNode(dragonIds.size())); // Image
		dragon.set("eid", new TextNode(entID)); // Entity ID
		dragon.set("n", new TextNode("Dragon-" + System.currentTimeMillis())); // Name
		dragon.set("cdt", new TextNode(fmt.format(new Date()))); // Creation time
		dragon.set("upd", new TextNode(fmt.format(new Date()))); // Update time

		// Set active if needed
		if (request.setAsSelected) {
			// Deselect old
			for (JsonElement ele : dragonIds) {
				String did = ele.getAsString();
				ObjectNode ddragon = req.parseXmlValue(data.getEntry("dragon-" + did).getAsString(), ObjectNode.class);

				// Check if active
				if (ddragon.get("is").asBoolean()) {
					// deselect
					ddragon.set("is", BooleanNode.FALSE);

					// Save
					data.setEntry("dragon-" + did, new JsonPrimitive(req.generateXmlValue("RaisedPetData", ddragon)));
				}
			}

			// Set current as active
			dragon.set("is", BooleanNode.TRUE);
		}

		// Save to list
		dragonIds = data.getEntry("dragonlist").getAsJsonArray();
		dragonIds.add(id);
		data.setEntry("dragonlist", dragonIds);

		// Save dragon
		data.setEntry("dragon-" + id, new JsonPrimitive(req.generateXmlValue("RaisedPetData", dragon)));

		// Handle inventory
		if (request.commonInventoryRequests != null && request.commonInventoryRequests.length != 0) {
			// Handle inventory request
			resp.inventoryUpdate = processCommonInventorySet(request.commonInventoryRequests, save.getSaveData(),
					request.containerID == -1 ? 1 : request.containerID);
		}

		// Set response
		resp.dragonData = dragon.deepCopy();
		resp.dragonData.set("ispetcreated", BooleanNode.TRUE);
		setResponseContent("text/xml", req.generateXmlValue("CPR", resp));
	}

	@Function(allowedMethods = { "POST" })
	public void getCommonInventory(FunctionInfo func) throws IOException {
		if (manager == null)
			manager = AccountManager.getInstance();
		if (itemManager == null)
			itemManager = ItemManager.getInstance();

		// Handle inventory request
		ServiceRequestInfo req = getUtilities().getServiceRequestPayload(getServerInstance().getLogger());
		if (req == null)
			return;
		String apiToken = getUtilities().decodeToken(req.payload.get("apiToken").toUpperCase());

		// Read token
		SessionToken tkn = new SessionToken();
		TokenParseResult res = tkn.parseToken(apiToken);
		AccountObject account = tkn.account;
		if (res != TokenParseResult.SUCCESS) {
			// Error
			setResponseStatus(404, "Not found");
			return;
		}

		// Parse request
		CommonInventoryRequestData request = req.parseXmlValue(req.payload.get("getCommonInventoryRequestXml"),
				CommonInventoryRequestData.class);

		// Retrieve container info
		AccountDataContainer data = account.getAccountData();
		if (tkn.saveID != null)
			data = account.getSave(tkn.saveID).getSaveData();
		data = data.getChildContainer("commoninventories").getChildContainer(Integer.toString(request.containerID));

		// Retrieve container
		CommonInventoryData resp = new CommonInventoryData();
		resp.userID = account.getAccountID();
		if (tkn.saveID != null)
			resp.userID = tkn.saveID;

		// Find items
		JsonElement e = data.getEntry("itemlist");
		if (e == null) {
			e = new JsonArray();
			data.setEntry("itemlist", e);
		}
		ArrayList<ItemBlock> items = new ArrayList<ItemBlock>();
		for (JsonElement itemDefEle : e.getAsJsonArray()) {
			int uniqueID = itemDefEle.getAsInt();

			// Locate item
			JsonObject itm = data.getEntry("item-" + uniqueID).getAsJsonObject();

			// Add item
			ItemBlock block = new ItemBlock();
			block.itemID = itm.get("id").getAsInt();
			block.quantity = itm.get("quantity").getAsInt();
			block.uses = itm.get("uses").getAsInt();
			block.uniqueItemID = uniqueID;

			// Add data info from item manager
			ItemInfo def = ItemManager.getInstance().getItemDefinition(block.itemID);
			if (def != null)
				block.data = def.getRawObject();
			items.add(block);
		}
		resp.items = items.toArray(t -> new ItemBlock[t]);

		// Set response
		setResponseContent("text/xml", req.generateXmlValue("CI", resp));
	}

	@Function(allowedMethods = { "POST" })
	public void getAllActivePetsByuserId(FunctionInfo func) throws IOException {
		if (manager == null)
			manager = AccountManager.getInstance();
		if (itemManager == null)
			itemManager = ItemManager.getInstance();

		// Handle dragon data request
		ServiceRequestInfo req = getUtilities().getServiceRequestPayload(getServerInstance().getLogger());
		if (req == null)
			return;
		String apiToken = getUtilities().decodeToken(req.payload.get("apiToken").toUpperCase());

		// Read token
		SessionToken tkn = new SessionToken();
		TokenParseResult res = tkn.parseToken(apiToken);
		AccountObject account = tkn.account;
		if (res != TokenParseResult.SUCCESS) {
			// Error
			setResponseStatus(404, "Not found");
			return;
		}

		// Parse request
		String userID = req.payload.get("userId");

		// Retrieve container
		if (userID.equals(account.getAccountID()) || account.getSave(userID) != null) {
			AccountDataContainer data = account.getAccountData();
			if (!userID.equals(account.getAccountID()))
				data = account.getSave(userID).getSaveData();

			// Pull dragons
			data = data.getChildContainer("dragons");
			JsonArray dragonIds = new JsonArray();
			if (data.entryExists("dragonlist"))
				dragonIds = data.getEntry("dragonlist").getAsJsonArray();
			else
				data.setEntry("dragonlist", dragonIds);

			// Prepare response
			ArrayList<ObjectNode> dragons = new ArrayList<ObjectNode>();

			// Populate list
			for (JsonElement ele : dragonIds) {
				String id = ele.getAsString();
				dragons.add(req.parseXmlValue(data.getEntry("dragon-" + id).getAsString(), ObjectNode.class));
			}

			// Set response
			if (dragons.size() != 0) {
				DragonListData ls = new DragonListData();
				ls.dragons = dragons.toArray(t -> new ObjectNode[t]);
				setResponseContent("text/xml", req.generateXmlValue("ArrayOfRaisedPetData", ls));
			} else
				setResponseContent("text/xml", req.generateXmlValue("ArrayOfRaisedPetData", null));
		} else {
			setResponseStatus(403, "Forbidden");
		}
	}

	/**
	 * Processes common inventory requests
	 * 
	 * @param requests  Request list
	 * @param container Container
	 * @return InventoryUpdateResponseData instance
	 */
	public static InventoryUpdateResponseData processCommonInventorySet(SetCommonInventoryRequestData[] requests,
			AccountDataContainer data, int container) {
		if (itemManager == null)
			itemManager = ItemManager.getInstance();

		// Prepare response
		InventoryUpdateResponseData resp = new InventoryUpdateResponseData();
		ArrayList<ItemUpdateBlock> updates = new ArrayList<ItemUpdateBlock>();
		resp.success = true;

		// Handle requests
		if (requests.length == 0) {
			resp.success = false;
		} else {
			PlayerInventory inv = itemManager.getCommonInventory(data);
			PlayerInventoryContainer cont = inv.getContainer(container);
			for (SetCommonInventoryRequestData request : requests) {
				if (itemManager.getItemDefinition(request.itemID) == null) {
					// Invalid
					resp = new InventoryUpdateResponseData();
					resp.success = false;
					return resp;
				}

				// Find inventory
				PlayerInventoryItem itm = null;
				if (request.itemUniqueID != -1)
					itm = cont.getItem(request.itemUniqueID);
				if (itm == null)
					itm = cont.findFirst(request.itemID);
				// TODO: complete implementation

				// Check
				if (itm == null)
					itm = cont.createItem(request.itemID, 0);

				// Update
				itm.setQuantity(itm.getQuantity() + request.quantity);
				if (request.uses != null) {
					int uses = 0;
					if (itm.getUses() != -1)
						uses = itm.getUses();
					itm.setUses(uses + Integer.parseInt(request.uses));
				}

				// Add update
				if (request.quantity > 0) {
					ItemUpdateBlock b = new ItemUpdateBlock();
					b.itemID = itm.getItemDefID();
					b.itemUniqueID = itm.getUniqueID();
					b.addedQuantity = request.quantity;
					updates.add(b);
				}
			}
		}

		// Set response
		resp.updateItems = updates.toArray(t -> new ItemUpdateBlock[t]);
		return resp;
	}
}

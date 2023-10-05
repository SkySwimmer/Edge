package org.asf.edge.gameplayapi.services.rooms.impl;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asf.edge.common.entities.coordinates.Vector3D;
import org.asf.edge.common.services.accounts.AccountDataContainer;
import org.asf.edge.common.services.accounts.AccountSaveContainer;
import org.asf.edge.gameplayapi.entities.rooms.PlayerRoomInfo;
import org.asf.edge.gameplayapi.entities.rooms.RoomItemInfo;
import org.asf.edge.gameplayapi.services.rooms.PlayerRoomManager;
import org.asf.edge.gameplayapi.xmls.data.KeyValuePairSetData;
import org.asf.edge.gameplayapi.xmls.rooms.RoomItemData.ItemStatBlock;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class PlayerRoomManagerImpl extends PlayerRoomManager {

	private Logger logger;

	@Override
	public void initService() {
		logger = LogManager.getLogger("PlayerRoomManager");
		logger.info("Player room manager is ready");
	}

	@Override
	public PlayerRoomInfo[] getRooms(AccountSaveContainer save) {
		ArrayList<PlayerRoomInfo> rooms = new ArrayList<PlayerRoomInfo>();
		try {
			// Load data
			AccountDataContainer data = save.getSaveData();
			data = data.getChildContainer("rooms");

			// Go through rooms
			for (String id : data.getEntryKeys()) {
				if (id.startsWith("room-")) {
					// Add room
					String roomID = id.substring(5);
					rooms.add(new PlayerRoomInfoImpl(roomID, save));
				}
			}
		} catch (IOException e) {
		}
		return rooms.toArray(t -> new PlayerRoomInfo[t]);
	}

	@Override
	protected PlayerRoomInfo createRoom(String roomID, int categoryID, AccountSaveContainer save) {
		try {
			// Load data
			AccountDataContainer data = save.getSaveData();
			data = data.getChildContainer("rooms");

			// Create room
			if (!data.entryExists("room-" + roomID)) {
				JsonObject roomD = new JsonObject();
				roomD.addProperty("roomCategory", categoryID);
				roomD.addProperty("roomName", save.getUsername());
				roomD.addProperty("itemID", -1);
				roomD.addProperty("creativePoints", 0);
				roomD.addProperty("listed", false);
				roomD.add("items", new JsonArray());
				data.setEntry("room-" + roomID, roomD);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return getRoom(roomID, save);
	}

	@Override
	public PlayerRoomInfo getRoom(String roomID, AccountSaveContainer save) {
		try {
			// Load data
			AccountDataContainer data = save.getSaveData();
			data = data.getChildContainer("rooms");

			// Create room
			if (data.entryExists("room-" + roomID)) {
				return new PlayerRoomInfoImpl(roomID, save);
			}
		} catch (IOException e) {
		}
		return null;
	}

	@Override
	public boolean roomExists(String roomID, AccountSaveContainer save) {
		try {
			// Load data
			AccountDataContainer data = save.getSaveData();
			data = data.getChildContainer("rooms");

			// Create room
			return data.entryExists("room-" + roomID);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public class PlayerRoomInfoImpl extends PlayerRoomInfo {

		private String roomID;
		private AccountSaveContainer save;
		private JsonObject roomData;

		public PlayerRoomInfoImpl(String roomID, AccountSaveContainer save) throws IOException {
			this.roomID = roomID;
			this.save = save;

			// Load data
			this.roomData = save.getSaveData().getChildContainer("rooms").getEntry("room-" + roomID).getAsJsonObject();
		}

		@Override
		public String getID() {
			return roomID;
		}

		@Override
		public double getCreativePoints() {
			return roomData.get("creativePoints").getAsDouble();
		}

		@Override
		public int getItemID() {
			return roomData.get("itemID").getAsInt();
		}

		@Override
		public String getName() {
			return roomData.get("roomName").getAsString();
		}

		@Override
		public RoomItemInfo[] getItems() {
			// Load items
			JsonArray itms = roomData.get("items").getAsJsonArray();
			ArrayList<RoomItemInfo> items = new ArrayList<RoomItemInfo>();

			// Load
			try {
				for (JsonElement ele : itms) {
					JsonObject rObj = ele.getAsJsonObject();
					RoomItemInfo item = new RoomItemInfo();
					item.roomItemID = rObj.get("roomItemID").getAsInt();
					item.parentID = rObj.get("parentID").getAsInt();
					item.itemID = rObj.get("itemID").getAsInt();
					item.itemUniqueID = rObj.get("itemUniqueID").getAsInt();
					item.uses = rObj.get("uses").getAsInt();
					item.currentStateID = rObj.get("currentStateID").getAsInt();
					item.lastStateChange = rObj.get("lastStateChange").getAsLong();
					if (rObj.has("inventoryModificationDate"))
						item.inventoryModificationDate = rObj.get("inventoryModificationDate").getAsString();
					if (rObj.has("itemAttributes"))
						item.itemAttributes = new XmlMapper().readValue(rObj.get("itemAttributes").getAsString(),
								KeyValuePairSetData.class);
					if (rObj.has("itemStats"))
						item.itemStats = new XmlMapper().readValue(rObj.get("itemStats").getAsString(),
								ItemStatBlock.class);
					item.position = new Vector3D(rObj.get("position").getAsJsonObject().get("x").getAsDouble(),
							rObj.get("position").getAsJsonObject().get("y").getAsDouble(),
							rObj.get("position").getAsJsonObject().get("z").getAsDouble());
					item.rotation = new Vector3D(rObj.get("rotation").getAsJsonObject().get("x").getAsDouble(),
							rObj.get("rotation").getAsJsonObject().get("y").getAsDouble(),
							rObj.get("rotation").getAsJsonObject().get("z").getAsDouble());
					items.add(item);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			// Return
			return items.toArray(t -> new RoomItemInfo[t]);
		}

		@Override
		public void setItems(RoomItemInfo[] items) {
			// Create item list
			JsonArray arr = new JsonArray();

			// Create entry
			XmlMapper mapper = new XmlMapper();
			mapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
			for (RoomItemInfo itm : items) {
				try {
					// Assign
					JsonObject rI = new JsonObject();
					rI.addProperty("roomItemID", itm.roomItemID);
					rI.addProperty("parentID", itm.parentID);
					rI.addProperty("itemID", itm.itemID);
					rI.addProperty("itemUniqueID", itm.itemUniqueID);
					rI.addProperty("uses", itm.uses);
					rI.addProperty("currentStateID", itm.currentStateID);
					rI.addProperty("lastStateChange", itm.lastStateChange);
					if (itm.inventoryModificationDate != null)
						rI.addProperty("inventoryModificationDate", itm.inventoryModificationDate);
					if (itm.itemAttributes != null)
						rI.addProperty("itemAttributes",
								mapper.writer().withFeatures(ToXmlGenerator.Feature.WRITE_NULLS_AS_XSI_NIL)
										.withRootName("ItemAttributeData").writeValueAsString(itm.itemAttributes));
					if (itm.itemStats != null)
						rI.addProperty("itemStats",
								mapper.writer().withFeatures(ToXmlGenerator.Feature.WRITE_NULLS_AS_XSI_NIL)
										.withRootName("ItemStatData").writeValueAsString(itm.itemStats));
					JsonObject pos = new JsonObject();
					pos.addProperty("x", itm.position.x);
					pos.addProperty("y", itm.position.y);
					pos.addProperty("z", itm.position.z);
					rI.add("position", pos);
					JsonObject rot = new JsonObject();
					rot.addProperty("x", itm.rotation.x);
					rot.addProperty("y", itm.rotation.y);
					rot.addProperty("z", itm.rotation.z);
					rI.add("rotation", rot);
					arr.add(rI);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

			// Set item ID
			roomData.add("items", arr);

			try {
				// Save
				save.getSaveData().getChildContainer("rooms").setEntry("room-" + roomID, roomData);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void setName(String newName) {
			// Set name
			roomData.addProperty("roomName", newName);

			try {
				// Save
				save.getSaveData().getChildContainer("rooms").setEntry("room-" + roomID, roomData);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void setItemID(int newID) {
			// Set item ID
			roomData.addProperty("itemID", newID);

			try {
				// Save
				save.getSaveData().getChildContainer("rooms").setEntry("room-" + roomID, roomData);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void setCreativePoints(double newPoints) {
			// Set item ID
			roomData.addProperty("creativePoints", newPoints);

			try {
				// Save
				save.getSaveData().getChildContainer("rooms").setEntry("room-" + roomID, roomData);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public int getCategoryID() {
			return roomData.get("roomCategory").getAsInt();
		}

	}
}

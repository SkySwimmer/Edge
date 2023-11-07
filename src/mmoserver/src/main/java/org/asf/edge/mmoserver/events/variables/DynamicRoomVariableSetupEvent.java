package org.asf.edge.mmoserver.events.variables;

import org.asf.edge.mmoserver.entities.smartfox.GameZone;
import org.asf.edge.mmoserver.entities.smartfox.RoomGroup;
import org.asf.edge.mmoserver.entities.smartfox.RoomInfo;
import org.asf.edge.mmoserver.entities.smartfox.RoomVariable;
import org.asf.edge.mmoserver.services.ZoneManager;
import org.asf.nexus.events.EventObject;
import org.asf.nexus.events.EventPath;

/**
 * 
 * Room variable setup event - called when dynamic variables need to be
 * populated
 * 
 * @author Sky Swimmer
 *
 */
@EventPath("rooms.dynamicvariables.setup")
public class DynamicRoomVariableSetupEvent extends EventObject {

	private GameZone zone;
	private ZoneManager zoneManager;
	private RoomGroup group;
	private RoomInfo room;

	private String dynVarKey;
	private RoomVariable variable;

	@Override
	public String eventPath() {
		return "rooms.dynamicvariables.setup";
	}

	public DynamicRoomVariableSetupEvent(ZoneManager zoneManager, GameZone zone, RoomGroup group, RoomInfo room,
			String dynVarKey, RoomVariable variable) {
		this.zoneManager = zoneManager;
		this.room = room;
		this.zone = zone;
		this.group = group;
		this.dynVarKey = dynVarKey;
		this.variable = variable;
	}

	/**
	 * Retrieves the dynamic assignment key
	 * 
	 * @return Variable dynamic assignment key
	 */
	public String getDynamicAssignmentKey() {
		return dynVarKey;
	}

	/**
	 * Retrieves the variable instance
	 * 
	 * @return RoomVariable instance
	 */
	public RoomVariable getVariable() {
		return variable;
	}

	/**
	 * Retrieves the room
	 * 
	 * @return RoomInfo instance
	 */
	public RoomInfo getRoom() {
		return room;
	}

	/**
	 * Retrieves the room group
	 * 
	 * @return RoomGroup instance
	 */
	public RoomGroup getGroup() {
		return group;
	}

	/**
	 * Retrieves the game zone
	 * 
	 * @return GameZone instance
	 */
	public GameZone getZone() {
		return zone;
	}

	/**
	 * Retrieves the zone manager
	 * 
	 * @return ZoneManager instance
	 */
	public ZoneManager getZoneManager() {
		return zoneManager;
	}

}

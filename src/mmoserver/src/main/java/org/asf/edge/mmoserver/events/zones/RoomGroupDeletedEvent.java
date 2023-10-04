package org.asf.edge.mmoserver.events.zones;

import org.asf.edge.mmoserver.entities.smartfox.GameZone;
import org.asf.edge.mmoserver.entities.smartfox.RoomGroup;
import org.asf.edge.mmoserver.services.ZoneManager;
import org.asf.edge.modules.eventbus.EventObject;
import org.asf.edge.modules.eventbus.EventPath;

/**
 * 
 * Room group deletion event - called when room groups are deleted
 * 
 * @author Sky Swimmer
 *
 */
@EventPath("rooms.groups.delete")
public class RoomGroupDeletedEvent extends EventObject {

	private GameZone zone;
	private ZoneManager zoneManager;
	private RoomGroup group;

	@Override
	public String eventPath() {
		return "rooms.groups.delete";
	}

	public RoomGroupDeletedEvent(ZoneManager zoneManager, GameZone zone, RoomGroup group) {
		this.zoneManager = zoneManager;
		this.zone = zone;
		this.group = group;
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

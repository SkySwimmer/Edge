package org.asf.edge.mmoserver.events.players;

import org.asf.edge.mmoserver.entities.player.PlayerInfo;
import org.asf.edge.mmoserver.entities.smartfox.RoomInfo;
import org.asf.edge.modules.eventbus.EventObject;
import org.asf.edge.modules.eventbus.EventPath;

/**
 * 
 * Player room spectator join event, called when a player joins a room as
 * spectator
 * 
 * @author Sky Swimmer
 * 
 */
@EventPath("players.rooms.join.spectator")
public class PlayerRoomJoinSpectatorEvent extends EventObject {

	private PlayerInfo player;
	private RoomInfo room;

	public PlayerRoomJoinSpectatorEvent(PlayerInfo player, RoomInfo room) {
		this.player = player;
		this.room = room;
	}

	@Override
	public String eventPath() {
		return "players.rooms.join.spectator";
	}

	/**
	 * Retrieves the room instance
	 * 
	 * @return RoomInfo instance
	 */
	public RoomInfo getRoom() {
		return room;
	}

	/**
	 * Retrieves the player object
	 * 
	 * @return PlayerInfo instance
	 */
	public PlayerInfo getPlayer() {
		return player;
	}

}

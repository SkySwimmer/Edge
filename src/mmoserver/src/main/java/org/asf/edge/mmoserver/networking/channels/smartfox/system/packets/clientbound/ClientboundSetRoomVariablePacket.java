package org.asf.edge.mmoserver.networking.channels.smartfox.system.packets.clientbound;

import java.util.HashMap;
import java.util.Map;

import org.asf.edge.mmoserver.entities.smartfox.RoomVariable;
import org.asf.edge.mmoserver.networking.packets.ISmartfoxPacket;
import org.asf.edge.mmoserver.networking.sfs.SmartfoxPacketData;

public class ClientboundSetRoomVariablePacket implements ISmartfoxPacket {

	public int roomID;
	public Map<String, RoomVariable> variables = new HashMap<String, RoomVariable>();

	@Override
	public ISmartfoxPacket createInstance() {
		return new ClientboundSetRoomVariablePacket();
	}

	@Override
	public short packetID() {
		return 11;
	}

	@Override
	public void parse(SmartfoxPacketData packet) {
		roomID = packet.payload.getInt("r");

		// Parse variables
		variables.clear();
		Object[] vars = packet.payload.getObjectArray("vl");
		for (Object v : vars) {
			Object[] varData = (Object[]) v;
			RoomVariable varD = RoomVariable.parseObjectArray(varData);
			variables.put(varD.getName(), varD);
		}
	}

	@Override
	public void build(SmartfoxPacketData packet) {
		packet.payload.setInt("r", roomID);

		// Build variables
		int i = 0;
		Object[] vars = new Object[variables.size()];
		for (RoomVariable var : variables.values()) {
			// Build variable
			vars[i++] = var.toObjectArray();
		}
		packet.payload.setObjectArray("vl", vars);
	}

}

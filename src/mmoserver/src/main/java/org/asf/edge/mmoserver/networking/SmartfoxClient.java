package org.asf.edge.mmoserver.networking;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asf.edge.common.EdgeServerEnvironment;
import org.asf.edge.common.services.accounts.AccountManager;
import org.asf.edge.mmoserver.events.clients.ClientConnectedEvent;
import org.asf.edge.mmoserver.events.clients.ClientDisconnectedEvent;
import org.asf.edge.mmoserver.networking.sfs.SmartfoxPayload;
import org.asf.edge.mmoserver.networking.channels.ExtensionChannel;
import org.asf.edge.mmoserver.networking.channels.SystemChannel;
import org.asf.edge.mmoserver.networking.channels.system.packets.clientbound.ClientboundHandshakeStartPacket;
import org.asf.edge.mmoserver.networking.channels.system.packets.serverbound.ServerboundHandshakeStartPacket;
import org.asf.edge.mmoserver.networking.packets.AbstractPacketChannel;
import org.asf.edge.mmoserver.networking.packets.ISmartfoxPacket;
import org.asf.edge.mmoserver.networking.sfs.SmartfoxPacketData;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;

/**
 * 
 * Smartfox client abstract
 * 
 * @author Sky Swimmer
 *
 */
public abstract class SmartfoxClient {

	private class ChannelDat {
		public AbstractPacketChannel channel;
		public CorePacketHandler handler;
	}

	private static Random rnd = new Random();

	private HashMap<String, Object> memory = new HashMap<String, Object>();
	private ArrayList<ChannelDat> registry = new ArrayList<ChannelDat>();
	private Logger logger = LogManager.getLogger("smartfox-client");
	private String sessionID;
	private int userID;

	/**
	 * Retrieves the session numeric ID
	 * 
	 * @return User ID integer
	 */
	public int getSessionNumericID() {
		return userID;
	}

	/**
	 * Retrieves the session ID
	 * 
	 * @return Session ID string
	 */
	public String getSessionID() {
		return sessionID;
	}

	/**
	 * Retrieves channels by type
	 * 
	 * @param <T>          Channel type
	 * @param channelClass Channel class
	 * @return AbstractPacketChannel instance or null
	 */
	@SuppressWarnings("unchecked")
	public <T extends AbstractPacketChannel> T getChannel(Class<T> channelClass) {
		for (ChannelDat ch : registry) {
			if (channelClass.isAssignableFrom(ch.channel.getClass()))
				return (T) ch.channel;
		}
		return null;
	}

	/**
	 * Retrieves the client logger
	 * 
	 * @return Logger instance
	 */
	protected Logger getLogger() {
		return logger;
	}

	/**
	 * Retrieves session memory objects
	 * 
	 * @param <T>  Object type
	 * @param type Object class
	 * @return Object instance or null
	 */
	@SuppressWarnings("unchecked")
	public <T> T getObject(Class<T> type) {
		return (T) memory.get(type.getTypeName());
	}

	/**
	 * Stores session memory objects
	 * 
	 * @param <T>    Object type
	 * @param type   Object class
	 * @param object Object instance
	 */
	public <T> void setObject(Class<T> type, T object) {
		memory.put(type.getTypeName(), object);
	}

	/**
	 * Removes session memory objects
	 * 
	 * @param <T>  Object type
	 * @param type Object class
	 */
	public <T> void removeObject(Class<T> type) {
		memory.remove(type.getTypeName());
	}

	/**
	 * Disconnects the client
	 */
	public void disconnect() {
		if (!isConnected())
			return;
		disconnectClient();
		callDisconnectEvents();
	}

	/**
	 * Calls disconnect events
	 */
	protected void callDisconnectEvents() {
		// Call disconnect
		getServer().getEventBus().dispatchEvent(new ClientDisconnectedEvent(getServer(), this));
	}

	/**
	 * Retrieves the server this client is associated with
	 * 
	 * @return SmartfoxServer instance
	 */
	public abstract SmartfoxServer getServer();

	/**
	 * Retrieves the remote socket address
	 * 
	 * @return Remote socket address string
	 */
	public abstract String getRemoteAddress();

	/**
	 * Checks if the client is still connected
	 * 
	 * @return True if connected, false otherwise
	 */
	public abstract boolean isConnected();

	/**
	 * Called to disconnect the client
	 */
	protected abstract void disconnectClient();

	/**
	 * Called to read single packets
	 * 
	 * @return Packet bytes
	 * @throws IOException If reading fails
	 */
	protected abstract byte[] readSingleRawPacket() throws IOException;

	/**
	 * Called to write packets
	 * 
	 * @param packet Packet payload
	 * @throws IOException If writing fails
	 */
	protected abstract void writeSingleRawPacket(byte[] packet) throws IOException;

	void startClient() {
		// Handshake
		try {
			// Read handshake
			SystemChannel channel = getChannel(SystemChannel.class);
			ServerboundHandshakeStartPacket handshakePk = readPacket(new ServerboundHandshakeStartPacket(),
					channel.channelID());
			logger.debug("Client " + getRemoteAddress() + " connected with a " + handshakePk.clientType
					+ " client, API " + handshakePk.apiVersion);

			// Prepare response
			ClientboundHandshakeStartPacket resp = new ClientboundHandshakeStartPacket();
			resp.compressionThreshold = 2048;
			resp.maxMessageSize = Integer.MAX_VALUE;

			// Generate session token
			String id = UUID.randomUUID().toString();
			while (true) {
				String idF = id;
				if (Stream.of(getServer().getClients()).anyMatch(t -> t.getSessionID().equals(idF)))
					id = UUID.randomUUID().toString();
				else
					break;
			}
			sessionID = id;
			int uId = rnd.nextInt();
			while (true) {
				int idF = uId;
				if (Stream.of(getServer().getClients()).anyMatch(t -> t.getSessionNumericID() == idF))
					uId = rnd.nextInt();
				else
					break;
			}
			userID = uId;

			// Build header
			JsonObject headers = new JsonObject();
			headers.addProperty("alg", "RS256");
			headers.addProperty("typ", "JWT");
			String headerD = Base64.getUrlEncoder().withoutPadding()
					.encodeToString(headers.toString().getBytes("UTF-8"));

			// Build payload
			JsonObject payload = new JsonObject();
			payload.addProperty("iat", System.currentTimeMillis() / 1000);
			payload.addProperty("jti", UUID.randomUUID().toString());
			payload.addProperty("iss", "EDGE");
			payload.addProperty("sub", "EDGE_MMO");
			payload.addProperty("uuid", sessionID);

			// Build
			String payloadD = Base64.getUrlEncoder().withoutPadding()
					.encodeToString(payload.toString().getBytes("UTF-8"));

			// Sign
			String token = headerD + "." + payloadD;
			String sig = Base64.getUrlEncoder().withoutPadding()
					.encodeToString(AccountManager.getInstance().signToken(token));

			// Send response
			resp.sessionToken = token + "." + sig;
			channel.sendPacket(resp);
		} catch (IOException e) {
			getLogger().error("Failed to handshake client " + getRemoteAddress(), e);
			disconnect();
			return;
		}

		// Dispatch connected event
		getServer().getEventBus().dispatchEvent(new ClientConnectedEvent(getServer(), this));

		// Start packet handler
		while (true) {
			// Read packet
			byte[] packet;
			try {
				packet = readSingleRawPacket();
			} catch (IOException e) {
				disconnect();
				return;
			}

			// Decode
			SmartfoxPacketData pkt;
			try {
				SmartfoxPayload pl = SmartfoxPayload.parseSfsObject(packet);

				// Check debug mode
				if (EdgeServerEnvironment.isInDebugMode()) {
					// Log
					logger.debug("C->S: " + new ObjectMapper().writeValueAsString(pl.toSfsObject()));
				}

				// Decode
				pkt = SmartfoxPacketData.fromSfsObject(pl);
			} catch (Exception e) {
				logger.error("Error occured while decoding packet: " + bytesToHex(packet) + " from client "
						+ getRemoteAddress() + ", terminating connection!", e);
				disconnect();
				return;
			}

			// Handle
			try {
				boolean handled = false;

				// Find channel
				for (ChannelDat ch : registry) {
					if (ch.channel.channelID() == pkt.channelID) {
						// Found channel
						ch.handler.handle(pkt.channelID, pkt.packetId, pkt);
						handled = true;
						break;
					}
				}

				if (!handled) {
					// Unhandled
					try {
						logger.warn("Unhandled packet: " + pkt.channelID + ":" + pkt.packetId + ": ["
								+ new ObjectMapper().writeValueAsString(pkt.payload.toSfsObject()) + "], client: "
								+ getRemoteAddress());
					} catch (JsonProcessingException e1) {
					}
				}
			} catch (Exception e) {
				try {
					logger.error("Error occured while handling packet: " + pkt.channelID + ":" + pkt.packetId + ": ["
							+ new ObjectMapper().writeValueAsString(pkt.payload.toSfsObject()) + "], client "
							+ getRemoteAddress(), e);
				} catch (JsonProcessingException e1) {
				}
				disconnect();
				return;
			}
		}
	}

	@SuppressWarnings("unchecked")
	private <T extends ISmartfoxPacket> T readPacket(T pkt, int channel) throws IOException {
		// Read packet
		SmartfoxPayload pl = SmartfoxPayload.parseSfsObject(readSingleRawPacket());

		// Check debug mode
		if (EdgeServerEnvironment.isInDebugMode()) {
			// Log
			logger.debug("C->S: " + new ObjectMapper().writeValueAsString(pl.toSfsObject()));
		}

		// Parse packet
		SmartfoxPacketData pk = SmartfoxPacketData.fromSfsObject(pl);
		if (pk.channelID != channel || pk.packetId != pkt.packetID() || !pkt.matches(pk))
			throw new IOException("Unexpected packet: " + pk.channelID + ":" + pk.packetId);
		pkt = (T) pkt.createInstance();
		pkt.parse(pk);
		return pkt;
	}

	void initRegistry(AbstractPacketChannel[] channels) {
		registry.clear();

		// Default channels
		registry.add(channelEntry(new SystemChannel()));
		registry.add(channelEntry(new ExtensionChannel()));

		// Add channels
		for (AbstractPacketChannel channel : channels) {
			// Init
			channel = channel.createInstance();
			CorePacketHandler handler = channel.init(this, (ch, packet) -> {
				// Build pacet
				SmartfoxPacketData data = new SmartfoxPacketData();
				data.channelID = ch.channelID();
				data.packetId = packet.packetID();
				data.payload = SmartfoxPayload.create();
				packet.build(data);

				// Check debug mode
				if (EdgeServerEnvironment.isInDebugMode()) {
					// Log
					logger.debug("S->C: " + new ObjectMapper().writeValueAsString(data.toSfsObject().toSfsObject()));
				}

				// Write
				writeSingleRawPacket(data.toSfsObject().encodeToSfsObject());
			});

			// Add
			ChannelDat d = new ChannelDat();
			d.channel = channel;
			d.handler = handler;
			registry.add(d);
		}
	}

	private ChannelDat channelEntry(AbstractPacketChannel ch) {
		return new ChannelDat() {
			{
				channel = ch;

				handler = channel.init(SmartfoxClient.this, (ch, packet) -> {
					// Build pacet
					SmartfoxPacketData data = new SmartfoxPacketData();
					data.channelID = ch.channelID();
					data.packetId = packet.packetID();
					data.payload = SmartfoxPayload.create();
					packet.build(data);

					// Check debug mode
					if (EdgeServerEnvironment.isInDebugMode()) {
						// Log
						logger.debug(
								"S->C: " + new ObjectMapper().writeValueAsString(data.toSfsObject().toSfsObject()));
					}

					// Write
					writeSingleRawPacket(data.toSfsObject().encodeToSfsObject());
				});
			}
		};
	}

	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars);
	}

}

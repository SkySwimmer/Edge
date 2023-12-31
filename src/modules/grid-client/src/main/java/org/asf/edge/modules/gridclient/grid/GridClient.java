package org.asf.edge.modules.gridclient.grid;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asf.connective.tasks.AsyncTaskManager;
import org.asf.edge.modules.gridclient.grid.components.auth.AuthenticatorComponent;
import org.asf.edge.modules.gridclient.grid.components.auth.GridLoginComponent;
import org.asf.edge.modules.gridclient.grid.components.core.ConnectFailureHandlerComponent;
import org.asf.edge.modules.gridclient.grid.components.core.DisconnectHandlerComponent;
import org.asf.edge.modules.gridclient.grid.events.GridClientConnectFailedEvent;
import org.asf.edge.modules.gridclient.grid.events.GridClientConnectedEvent;
import org.asf.edge.modules.gridclient.grid.events.GridClientDisconnectedEvent;
import org.asf.edge.modules.gridclient.grid.events.GridClientSetupEvent;
import org.asf.edge.modules.gridclient.phoenix.DisconnectReason;
import org.asf.edge.modules.gridclient.phoenix.PhoenixClient;
import org.asf.edge.modules.gridclient.phoenix.PhoenixEnvironment;
import org.asf.edge.modules.gridclient.phoenix.auth.LoginManager;
import org.asf.edge.modules.gridclient.phoenix.certificate.PhoenixCertificate;
import org.asf.edge.modules.gridclient.phoenix.events.ClientConnectedEvent;
import org.asf.edge.modules.gridclient.phoenix.events.ClientDisconnectedEvent;
import org.asf.edge.modules.gridclient.phoenix.events.ClientProgramHandshakeEvent;
import org.asf.edge.modules.gridclient.phoenix.events.ClientProgramLateHandshakeEvent;
import org.asf.edge.modules.gridclient.phoenix.exceptions.PhoenixConnectException;
import org.asf.edge.modules.gridclient.phoenix.serverlist.ServerInstance;
import org.asf.edge.modules.gridclient.phoenix.serverlist.ServerListFilter;
import org.asf.edge.modules.gridclient.phoenix.serverlist.ServerListScanner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 
 * Grid client class (<b>DO NOT USE DIRECTLY WHEN WORKING FROM WITHIN
 * COMPONENTS, THIS CLASS LOCKS UNTIL THE SERVER CONNECTION IS ESTABLISHED</b>)
 * 
 * @author Sky Swimmer
 *
 */
public class GridClient {

	public static final int GRID_PROTOCOL_VERSION = 1;
	public static final String GRID_SOFTWARE_VERSION = "1.0.0.A1";

	static PhoenixClient client;
	static LinkedHashMap<String, GridClientComponent> components = new LinkedHashMap<String, GridClientComponent>();
	private static Logger logger = LogManager.getLogger("grid-client");

	private static boolean isDisconnect;
	private static boolean disconnected;

	private static Object serverSwitchLock = new Object();
	private static Object serverConnectLock = new Object();

	private static PhoenixCertificate cert;
	private static String host;
	private static int port;

	private static LoginManager loginManager;

	private static void addComponentsToPhoenixClient(PhoenixClient client) {
		logger.info("Loading components...");

		// Add components
		registerComponent(new DisconnectHandlerComponent());
		registerComponent(new ConnectFailureHandlerComponent());
		registerComponent(new AuthenticatorComponent(loginManager));
		registerComponent(new GridLoginComponent(loginManager));

		// Dispatch event
		client.getEventBus().dispatchEvent(new GridClientSetupEvent(client));
	}

	/**
	 * Retrieves the login manager
	 * 
	 * @return LoginManager instance
	 */
	public static LoginManager getLoginManager() {
		return loginManager;
	}

	/**
	 * Scans the server list and finds the best server instance
	 * 
	 * @return ServerInstance object or null
	 */
	public static ServerInstance findBestServer() {
		logger.info("Searching for Phoenix servers...");
		ServerListScanner sc = new ServerListScanner("nexusgrid", GRID_PROTOCOL_VERSION);
		ServerInstance[] servers = sc.scanPublicServerList(new ServerListFilter("players", "notfull"));

		// Log
		logger.info("Found " + servers.length + " server(s), attempting server selection...");

		//
		// FIXME: THIS HAS TO BE DONE BETTER
		//
		// TODO: we also need support for a friend system, we should prioritize servers
		// with friends
		//

		// Go through servers, server with lowest ping and 65 or more players first
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 65 && online < 80 && srv.getPing() < 25)
				return srv;
		}

		// Go through servers, server with lowest ping and 50 or more players first
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 50 && online < 80 && srv.getPing() < 25)
				return srv;
		}

		// Go through servers, server with low ping and 65 or more players first
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 65 && online < 80 && srv.getPing() < 50)
				return srv;
		}

		// Go through servers, server with low ping and 50 or more players first
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 50 && online < 80 && srv.getPing() < 50)
				return srv;
		}

		// Go through servers, server with lowest ping and 25 or more players first
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 25 && online < 80 && srv.getPing() < 25)
				return srv;
		}

		// Go through servers, server with low ping and 50 or more players first
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 25 && online < 80 && srv.getPing() < 50)
				return srv;
		}

		// Go through servers, server with 65 or more players first, higher ping
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 65 && online < 80 && srv.getPing() < 100)
				return srv;
		}

		// Go through servers, server with 50 or more players first, higher ping
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 50 && online < 80 && srv.getPing() < 100)
				return srv;
		}

		// Go through servers, server with 25 or more players first, higher ping
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 25 && online < 80 && srv.getPing() < 100)
				return srv;
		}

		// Go through servers, server with lowest ping and 50 or more players first, no
		// max, give up trying to find decent load, we need a server, preferably with
		// at least some players and low ping
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 50 && srv.getPing() < 25)
				return srv;
		}

		// Go through servers, server with low ping and 50 or more players first, no
		// max, give up trying to find decent load, we need a server, preferably with
		// at least some players and low ping
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 50 && srv.getPing() < 50)
				return srv;
		}

		// Give up, find server with some smaller number players at all and lowest ping
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 20 && srv.getPing() < 25)
				return srv;
		}
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 20 && srv.getPing() < 50)
				return srv;
		}
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 20 && srv.getPing() < 100)
				return srv;
		}
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 20)
				return srv;
		}
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 10 && srv.getPing() < 25)
				return srv;
		}
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 10 && srv.getPing() < 50)
				return srv;
		}
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 10 && srv.getPing() < 100)
				return srv;
		}
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			int online = Integer.parseInt(srv.details.get("players.current"));
			if (online >= 10)
				return srv;
		}

		// Okay, just return the one with lowest ping
		for (ServerInstance srv : Stream.of(servers).filter(t -> t.details.containsKey("players.current"))
				.sorted((t1, t2) -> Integer.compare(t1.getPing(), t2.getPing())).toArray(t -> new ServerInstance[t])) {
			// Check
			return srv;
		}

		// None found
		return null;
	}

	/**
	 * Retrieves all client component instances
	 * 
	 * @return Array of GridClientComponent instances
	 */
	public static GridClientComponent[] getAllComponents() {
		synchronized (serverSwitchLock) {
			synchronized (serverConnectLock) {
				return components.values().toArray(t -> new GridClientComponent[t]);
			}
		}
	}

	/**
	 * Retrieves components by ID
	 * 
	 * @param componentID Component ID string
	 * @return GridClientComponent instance or null
	 */
	public static GridClientComponent getComponent(String componentID) {
		synchronized (serverSwitchLock) {
			synchronized (serverConnectLock) {
				for (GridClientComponent comp : getAllComponents())
					if (comp.componentID().equals(componentID))
						return comp;
				return null;
			}
		}
	}

	/**
	 * Checks if components are present
	 * 
	 * @param componentID Component ID string
	 * @return True if present, false otherwise
	 */
	public static boolean hasComponent(String componentID) {
		synchronized (serverSwitchLock) {
			synchronized (serverConnectLock) {
				return components.containsKey(componentID);
			}
		}
	}

	/**
	 * Retrieves components
	 * 
	 * @param <T>          Component type
	 * @param componentCls Component class
	 * @return GridClientComponent instance or null
	 */
	@SuppressWarnings("unchecked")
	public static <T extends GridClientComponent> T getComponent(Class<T> componentCls) {
		synchronized (serverSwitchLock) {
			synchronized (serverConnectLock) {
				for (GridClientComponent comp : getAllComponents())
					if (componentCls.isAssignableFrom(comp.getClass()))
						return (T) comp;
				return null;
			}
		}
	}

	/**
	 * Registers client components
	 * 
	 * @param component GridClientComponent instance to register
	 */
	public static void registerComponent(GridClientComponent component) {
		if (components.containsKey(component.componentID()))
			throw new IllegalArgumentException("Component " + component.componentID() + " is already registered!");

		// Add
		logger.info("Loading component: " + component.componentID());
		components.put(component.componentID(), component);
		component.addToClient(client);

		// Register events
		client.getEventBus().addAllEventsFromReceiver(component);
	}

	/**
	 * Retrieves the Phoenix client instance
	 * 
	 * @return PhoenixClient instance
	 */
	public static PhoenixClient getClient() {
		synchronized (serverSwitchLock) {
			synchronized (serverConnectLock) {
				return client;
			}
		}
	}

	/**
	 * Checks if the client is connected
	 * 
	 * @return True if connected, false otherwise
	 */
	public static boolean isConnected() {
		return getClient().isConnected();
	}

	/**
	 * Switches Phoenix servers
	 * 
	 * @param host Server host
	 * @param port Server port
	 * @param cert Server certificate
	 */
	public static void switchServer(String host, int port, PhoenixCertificate cert) {
		synchronized (serverSwitchLock) {
			// Disconnect
			disconnect();

			// Assign
			GridClient.host = host;
			GridClient.port = port;
			GridClient.cert = cert;

			// Reconnect
			connectToServer();
		}
	}

	/**
	 * Switches Phoenix servers
	 * 
	 * @param server Server information object
	 * @throws IOException If downloading the certificate fails
	 */
	public static void switchServer(ServerInstance server) throws IOException {
		synchronized (serverSwitchLock) {
			// Disconnect
			disconnect();

			// Assign
			// Create client
			GridClient.host = server.getBestAdress();
			GridClient.port = server.port;
			GridClient.cert = (server.secureMode ? PhoenixCertificate.downloadFromAPI("nexusgrid", server.serverID)
					: null);

			// Reconnect
			connectToServer();
		}
	}

	/**
	 * Disconnects from the servers
	 */
	public static void disconnect() {
		if (client == null)
			return;

		// Disconnect
		logger.info("Disconnecting from servers...");
		client.getEventBus().addEventHandler(ClientDisconnectedEvent.class, event -> {
			disconnected = true;
		});
		isDisconnect = true;
		if (client.isConnected())
			client.disconnect();

		// Wait for disconnect
		while (!disconnected)
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}

		// Unload components
		logger.info("Unloading components...");
		for (GridClientComponent comp : components.values()) {
			logger.info("Unoading component: " + comp.componentID());
			comp.deInit();
		}

		// Disconnected
		disconnected = false;
		isDisconnect = false;
		logger.info("Disconnected from Phoenix servers.");
		client = null;
	}

	private static boolean inited;

	/**
	 * Initializes the Grid client
	 * 
	 * @param host         Server host
	 * @param port         Server port
	 * @param cert         Server certificate
	 * @param loginManager Login manager
	 */
	public static void initGridPhoenixClient(String host, int port, PhoenixCertificate cert,
			LoginManager loginManager) {
		if (inited)
			return;
		inited = true;

		// Create client
		GridClient.host = host;
		GridClient.port = port;
		GridClient.cert = cert;
		GridClient.loginManager = loginManager;
		connectToServer();
	}

	/**
	 * Initializes the Grid client
	 * 
	 * @param server       Server information object
	 * @param loginManager Login manager
	 * @throws IOException If downloading the certificate fails
	 */
	public static void initGridPhoenixClient(ServerInstance server, LoginManager loginManager) throws IOException {
		if (inited)
			return;
		inited = true;

		// Create client
		GridClient.host = server.getBestAdress();
		GridClient.port = server.port;
		GridClient.cert = (server.secureMode ? PhoenixCertificate.downloadFromAPI("nexusgrid", server.serverID) : null);
		GridClient.loginManager = loginManager;
		connectToServer();
	}

	/**
	 * Connects to the Phoenix servers
	 */
	public static void connectToServer() {
		synchronized (serverConnectLock) {
			if (client != null)
				throw new IllegalStateException("Already connected");

			// Phoenix handshake
			client = new PhoenixClient();
			logger.info("Attempting to connect to Grid Phoenix server " + host + ":" + port + "...");
			String gameID = "nexusgrid";
			String gameVersion = GRID_SOFTWARE_VERSION;
			int gameProtocol = GRID_PROTOCOL_VERSION;
			client.getEventBus().addEventHandler(ClientProgramHandshakeEvent.class, event -> {
				try {
					PhoenixClient cl = event.getClient();

					// Write game ID
					cl.getLogger().debug("Performing Phoenix Game Handshake on connection " + event.getClient());
					cl.getLogger().trace("Sending game ID: " + gameID + ", protocol version " + gameProtocol
							+ ", game version " + gameVersion + " to " + cl);
					event.getWriter().writeString(gameID);

					// Send protocol
					event.getWriter().writeInt(gameProtocol);
					event.getWriter().writeString(gameVersion);

					// Send IP and port
					event.getWriter().writeString(host);
					event.getWriter().writeInt(port);

					// Read ID and protocol
					String rGID = event.getReader().readString();
					int rProtocol = event.getReader().readInt();
					String rVer = event.getReader().readString();
					cl.getLogger().debug(
							"Received game ID: " + rGID + ", protocol version " + rProtocol + ", game version " + rVer);
					cl.getLogger().debug("Verifying handshake...");
					if (!rGID.equals(gameID)) {
						// Fail
						cl.getLogger().error("Handshake failure! Game ID mismatch!");
						cl.disconnect("connect.error.connectfailure.gamemismatch", new String[] { rGID, gameID });
						event.failHandshake();
						return;
					} else if (gameProtocol != rProtocol) {
						// Fail
						cl.getLogger().error("Handshake failure! Game version mismatch!");
						cl.disconnect("connect.error.connectfailure.versionmismatch",
								new String[] { rVer, gameVersion });
						event.failHandshake();
						return;
					}
					cl.getLogger().debug("Handshake success!");

					// Run for all components
					for (GridClientComponent comp : components.values())
						if (!event.isHandled())
							comp.programHandshake(event);
				} catch (Exception e) {
					event.failHandshake();
				}
			});

			// Client late handshake
			client.getEventBus().addEventHandler(ClientProgramLateHandshakeEvent.class, event -> {
				// Run for all components
				for (GridClientComponent comp : components.values())
					if (client.isConnected())
						comp.programLateHandshake(event);
			});

			// Connection success
			client.getEventBus().addEventHandler(ClientConnectedEvent.class, event -> {
				try {
					PhoenixClient cl = event.getClient();
					cl.getLogger().info("Grid server connection established!");
					connectionSuccess(cl);
				} catch (Exception e) {
				}
			});

			try {
				// Add components
				components.clear();
				addComponentsToPhoenixClient(client);

				// Check session
				if (!loginManager.isLoggedIn())
					throw new PhoenixConnectException(new DisconnectReason("connect.error.nosessiontoken"));

				// Start client
				client.connect(host, port, cert);
				startSuccess(host, port, cert, client);
			} catch (IOException | PhoenixConnectException e) {
				// Check
				if (isDisconnect) {
					disconnected = true;
					return;
				}

				// Dispatch event
				GridClientConnectFailedEvent eve = new GridClientConnectFailedEvent(client,
						(e instanceof PhoenixConnectException ? ((PhoenixConnectException) e).getDisconnectReason()
								: new DisconnectReason("connection.failed", e.getMessage())));
				client.getEventBus().dispatchEvent(eve);

				// Run for all components
				for (GridClientComponent comp : components.values())
					if (!eve.isHandled())
						comp.connectFailed(eve);

				// Reconnect if needed
				if (!eve.shouldAttemptReconnect()) {
					// Error
					if (e instanceof PhoenixConnectException)
						logger.error("Failed to connect to the Phoenix Grid server! ("
								+ ((PhoenixConnectException) e).getDisconnectReason().getReason() + ")");
					else
						logger.error("Failed to connect to the Phoenix Grid server!", e);
				} else {
					// Error
					if (e instanceof PhoenixConnectException)
						logger.error("Failed to connect to the Phoenix Grid server! Automatic retry scheduled... ("
								+ ((PhoenixConnectException) e).getDisconnectReason().getReason() + ")");
					else
						logger.error("Failed to connect to the Phoenix Grid server! Automatic retry scheduled...", e);
					AsyncTaskManager.runAsync(() -> {
						while (true) {
							// Attempt connection
							try {
								// Check session
								if (!loginManager.isLoggedIn())
									throw new PhoenixConnectException(
											new DisconnectReason("connect.error.nosessiontoken"));

								// Login
								ServerInstance inst = findBestServer();
								if (inst == null)
									throw new PhoenixConnectException(
											new DisconnectReason("connect.error.connectfailure.noservers"));
								else
									client.connect(inst.getBestAdress(), inst.port,
											PhoenixCertificate.downloadFromAPI("nexusgrid", inst.serverID));
								startSuccess(host, port, cert, client);
								break;
							} catch (IOException | PhoenixConnectException e2) {
								// Check
								if (isDisconnect) {
									disconnected = true;
									break;
								}

								// Dispatch event
								GridClientConnectFailedEvent ev = new GridClientConnectFailedEvent(client,
										(e instanceof PhoenixConnectException
												? ((PhoenixConnectException) e).getDisconnectReason()
												: new DisconnectReason("connection.failed", e.getMessage())));
								client.getEventBus().dispatchEvent(ev);

								// Run for all components
								for (GridClientComponent comp : components.values())
									if (!ev.isHandled())
										comp.connectFailed(ev);

								// Dont reconnect if cancelled
								if (!ev.shouldAttemptReconnect())
									break;
							}
							try {
								Thread.sleep(10000);
							} catch (InterruptedException e1) {
								break;
							}
						}
					});
				}
			}
		}
	}

	private static void connectionSuccess(PhoenixClient client) {
		// Run for all components
		for (GridClientComponent comp : components.values())
			comp.connected(client);

		// Dispatch event
		client.getEventBus().dispatchEvent(new GridClientConnectedEvent(client));
	}

	private static void startSuccess(String host, int port, PhoenixCertificate cert, PhoenixClient client) {
		// Add handler
		client.getEventBus().addEventHandler(ClientDisconnectedEvent.class, event -> {
			try {
				// Check
				if (isDisconnect) {
					disconnected = true;
					return;
				}

				// Dispatch event
				GridClientDisconnectedEvent ev = new GridClientDisconnectedEvent(client, event.getDisconnectReason());
				client.getEventBus().dispatchEvent(ev);

				// Run for all components
				for (GridClientComponent comp : components.values())
					if (!ev.isHandled())
						comp.disconnect(ev);

				// Dont reconnect if cancelled
				if (!ev.shouldAttemptReconnect()) {
					// Log
					logger.error("Lost Grid server connection, cannot reconnect! ("
							+ event.getDisconnectReason().getReason() + ")");
					return;
				}

				// Log
				logger.error("Lost Grid server connection, attempting to reconnect... ("
						+ event.getDisconnectReason().getReason() + ")");

				// Reconnect
				AsyncTaskManager.runAsync(() -> {
					while (true) {
						// Attempt connection
						try {
							// Check session
							if (!loginManager.isLoggedIn())
								throw new PhoenixConnectException(new DisconnectReason("connect.error.nosessiontoken"));

							// Login
							ServerInstance inst = findBestServer();
							if (inst == null)
								throw new PhoenixConnectException(
										new DisconnectReason("connect.error.connectfailure.noservers"));
							else
								client.connect(inst.getBestAdress(), inst.port,
										PhoenixCertificate.downloadFromAPI("nexusgrid", inst.serverID));
							break;
						} catch (IOException | PhoenixConnectException e) {
							// Check
							if (isDisconnect) {
								disconnected = true;
								break;
							}

							// Dispatch event
							GridClientConnectFailedEvent ev2 = new GridClientConnectFailedEvent(client,
									(e instanceof PhoenixConnectException
											? ((PhoenixConnectException) e).getDisconnectReason()
											: new DisconnectReason("connection.failed", e.getMessage())));
							client.getEventBus().dispatchEvent(ev2);

							// Run for all components
							for (GridClientComponent comp : components.values())
								if (!ev.isHandled())
									comp.connectFailed(ev2);

							// Dont reconnect if cancelled
							if (!ev2.shouldAttemptReconnect())
								break;
						}
						try {
							Thread.sleep(10000);
						} catch (InterruptedException e1) {
							break;
						}
					}
				});
			} catch (Exception e) {
			}
		});
	}

	/**
	 * Authenticates the Grid game session
	 * 
	 * @param gridApiVersion Expected Grid API version
	 * @param gridSoftwareID Expected Grid API software ID
	 * @throws IOException If the game cannot be authenticated
	 */
	public static void authenticateGame(String gridApiVersion, String gridSoftwareID) throws IOException {
		// Build URL
		String url = PhoenixEnvironment.defaultAPIServer;
		if (!url.endsWith("/"))
			url += "/";
		url += "grid/gameservice/startgame";

		// Build request
		JsonObject payload = new JsonObject();
		payload.addProperty("clientApiVersion", gridApiVersion);
		payload.addProperty("clientSoftwareID", gridSoftwareID);

		// Contact Phoenix
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.getOutputStream().write(payload.toString().getBytes("UTF-8"));

		// Read response
		if (conn.getResponseCode() >= 400) {
			// Error
			try {
				JsonObject response = JsonParser.parseString(new String(conn.getErrorStream().readAllBytes(), "UTF-8"))
						.getAsJsonObject();
				if (response.has("error")) {
					String error = response.get("error").getAsString();
					if (error.equals("api_version_mismatch"))
						throw new IOException("API version mismatch while authenticating the game");
					else if (error.equals("software_id_mismatch"))
						throw new IOException("Software ID mismatch while authenticating the game");
					else
						throw new IOException("Server returned: " + error);
				}
			} catch (IOException e) {
				throw e;
			}
		}
		JsonObject response = JsonParser.parseString(new String(conn.getInputStream().readAllBytes(), "UTF-8"))
				.getAsJsonObject();
		PhoenixEnvironment.defaultLoginToken = response.get("token").getAsString();
	}

	/**
	 * Creates Grid API requests
	 * 
	 * @param function            API function
	 * @param token               API token
	 * @param payload             Payload json
	 * @param returnErrorMessages True to return error messages, false to throw
	 *                            exceptions instead
	 * @return Response object
	 * @throws IOException If contacting the server fails
	 */
	public static JsonObject sendGridApiRequest(String function, String token, JsonObject payload,
			boolean returnErrorMessages) throws IOException {
		// Build URL
		String url = PhoenixEnvironment.defaultAPIServer;
		if (!url.endsWith("/"))
			url += "/";
		if (function.startsWith("/"))
			function = function.substring(1);
		url += function;

		// Open connection
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.addRequestProperty("Authorization", "Bearer " + token);
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);

		// Write request
		conn.getOutputStream().write(payload.toString().getBytes("UTF-8"));

		// Check response
		if (conn.getResponseCode() >= 400) {
			if (!returnErrorMessages)
				throw new IOException(
						"Server returned HTTP " + conn.getResponseCode() + " " + conn.getResponseMessage());

			// Read error
			try {
				JsonObject errorMessage = JsonParser
						.parseString(new String(conn.getErrorStream().readAllBytes(), "UTF-8")).getAsJsonObject();
				if (!errorMessage.has("error")) {
					errorMessage.addProperty("error", "http_status_" + conn.getResponseCode());
					errorMessage.addProperty("errorMessage", conn.getResponseMessage());
				}
				return errorMessage;
			} catch (IOException e) {
				throw new IOException(
						"Server returned HTTP " + conn.getResponseCode() + " " + conn.getResponseMessage());
			}
		}

		// Read response
		try {
			return JsonParser.parseString(new String(conn.getInputStream().readAllBytes(), "UTF-8")).getAsJsonObject();
		} catch (Exception e) {
			throw new IOException("Server returned a non-json response");
		}
	}

	/**
	 * Verifies the session lock status
	 * 
	 * @return SessionLockStatus value
	 */
	public static SessionLockStatus sessionLockStatus() {
		try {
			// Test connection
			String url = PhoenixEnvironment.defaultAPIServer;
			if (!url.endsWith("/"))
				url += "/";
			url += "grid/utilities/testsessionlock";
			HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
			conn.addRequestProperty("Authorization", "Bearer " + loginManager.getSession().getGameSessionToken());
			if (conn.getResponseCode() >= 400) {
				return SessionLockStatus.valueOf(new String(conn.getErrorStream().readAllBytes(), "UTF-8").trim());
			} else {
				return SessionLockStatus.valueOf(new String(conn.getInputStream().readAllBytes(), "UTF-8").trim());
			}
		} catch (Exception e) {
			return SessionLockStatus.CONNECTION_FAILURE;
		}
	}

	/**
	 * Verifies the grid server connection
	 * 
	 * @return True if connected, false otherwise
	 */
	public static boolean verifyGridConnection() {
		try {
			// Verify our own connection to the Grid
			String url = PhoenixEnvironment.defaultAPIServer;
			if (!url.endsWith("/"))
				url += "/";
			url += "grid/utilities/testconnection";
			new URL(url).openStream().close();

			// Connected!
			return true;
		} catch (IOException e) {
		}
		return false;
	}

}

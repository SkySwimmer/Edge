package org.asf.edge.gameplayapi.events.server;

import org.asf.edge.gameplayapi.EdgeGameplayApiServer;
import org.asf.edge.gameplayapi.config.GameplayApiServerConfig;
import org.asf.edge.gameplayapi.events.GameplayApiServerEvent;
import org.asf.edge.modules.eventbus.EventPath;

/**
 * 
 * Gameplay API server setup event - called before the server starts
 * 
 * @author Sky Swimmer
 *
 */
@EventPath("gameplayapi.server.setup")
public class GameplayApiServerSetupEvent extends GameplayApiServerEvent {
	private GameplayApiServerConfig config;
	private EdgeGameplayApiServer server;

	public GameplayApiServerSetupEvent(GameplayApiServerConfig config, EdgeGameplayApiServer server) {
		this.config = config;
		this.server = server;
	}

	/**
	 * Retrieves the server instance
	 * 
	 * @return EdgeGameplayApiServer instance
	 */
	public EdgeGameplayApiServer getServer() {
		return server;
	}

	/**
	 * Retrieves the configuration object
	 * 
	 * @return GameplayApiServerConfig instance
	 */
	public GameplayApiServerConfig getConfig() {
		return config;
	}

	@Override
	public String eventPath() {
		return "gameplayapi.server.setup";
	}

}

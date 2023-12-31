package org.asf.edge.contentserver.events.server;

import org.asf.edge.contentserver.EdgeContentServer;
import org.asf.edge.contentserver.config.ContentServerConfig;
import org.asf.edge.contentserver.events.ContentServerEvent;
import org.asf.edge.modules.eventbus.EventPath;

/**
 * 
 * Content server setup event - called before the server starts
 * 
 * @author Sky Swimmer
 *
 */
@EventPath("contentserver.server.setup")
public class ContentServerSetupEvent extends ContentServerEvent {
	private ContentServerConfig config;
	private EdgeContentServer server;

	public ContentServerSetupEvent(ContentServerConfig config, EdgeContentServer server) {
		this.config = config;
		this.server = server;
	}

	/**
	 * Retrieves the server instance
	 * 
	 * @return EdgeContentServer instance
	 */
	public EdgeContentServer getServer() {
		return server;
	}

	/**
	 * Retrieves the configuration object
	 * 
	 * @return ContentServerConfig instance
	 */
	public ContentServerConfig getConfig() {
		return config;
	}

	@Override
	public String eventPath() {
		return "contentserver.server.setup";
	}

}

package org.asf.edge.commonapi.events.server;

import org.asf.edge.commonapi.EdgeCommonApiServer;
import org.asf.edge.commonapi.config.CommonApiServerConfig;
import org.asf.edge.commonapi.events.CommonApiServerEvent;
import org.asf.edge.modules.eventbus.EventPath;

/**
 * 
 * Common API server setup event - called before the server starts
 * 
 * @author Sky Swimmer
 *
 */
@EventPath("commonapi.server.setup")
public class CommonApiServerSetupEvent extends CommonApiServerEvent {
	private CommonApiServerConfig config;
	private EdgeCommonApiServer server;

	public CommonApiServerSetupEvent(CommonApiServerConfig config, EdgeCommonApiServer server) {
		this.config = config;
		this.server = server;
	}

	/**
	 * Retrieves the server instance
	 * 
	 * @return EdgeCommonApiServer instance
	 */
	public EdgeCommonApiServer getServer() {
		return server;
	}

	/**
	 * Retrieves the configuration object
	 * 
	 * @return CommonApiServerConfig instance
	 */
	public CommonApiServerConfig getConfig() {
		return config;
	}

	@Override
	public String eventPath() {
		return "commonapi.server.setup";
	}

}

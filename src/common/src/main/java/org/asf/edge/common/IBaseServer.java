package org.asf.edge.common;

import org.apache.logging.log4j.Logger;

public interface IBaseServer {

	/**
	 * Retrieves the server logger
	 * 
	 * @return Logger instance
	 */
	public Logger getLogger();

	/**
	 * Retrieves the server version
	 * 
	 * @return Server version string
	 */
	public String getVersion();

}

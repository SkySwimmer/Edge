package org.asf.edge.gameplayapi.http.handlers.gameplayapi;

import java.io.IOException;
import org.asf.connective.RemoteClient;
import org.asf.connective.processors.HttpPushProcessor;
import org.asf.edge.common.http.apihandlerutils.EdgeWebService;
import org.asf.edge.gameplayapi.EdgeGameplayApiServer;

public class RatingWebServiceV1Processor extends EdgeWebService<EdgeGameplayApiServer> {

	public RatingWebServiceV1Processor(EdgeGameplayApiServer server) {
		super(server);
	}

	@Override
	public HttpPushProcessor createNewInstance() {
		return new RatingWebServiceV1Processor(getServerInstance());
	}

	@Override
	public String path() {
		return "/RatingWebService.asmx";
	}

	@Override
	public void fallbackRequestProcessor(String path, String method, RemoteClient client, String contentType)
			throws IOException {
		// Handle request
		path = path;
		setResponseStatus(404, "Not found");
	}

}

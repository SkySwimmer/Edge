package gameplayapi;

import org.asf.edge.modules.IEdgeModule;
import org.asf.edge.modules.eventbus.EventListener;

public class TestModule implements IEdgeModule {

	@Override
	public String moduleID() {
		return "test";
	}

	@Override
	public String version() {
		return "test";
	}

	@Override
	public void init() {
	}
}

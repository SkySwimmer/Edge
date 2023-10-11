package org.asf.edge.mmoserver.events.variables;

import org.asf.edge.mmoserver.entities.smartfox.SfsUser;
import org.asf.edge.mmoserver.entities.smartfox.UserVariable;
import org.asf.edge.modules.eventbus.EventObject;
import org.asf.edge.modules.eventbus.EventPath;

/**
 * 
 * User variable removed event - called when variables are removed from users
 * 
 * @author Sky Swimmer
 *
 */
@EventPath("users.variables.removed")
public class UserVariableRemovedEvent extends EventObject {

	private SfsUser user;
	private UserVariable variable;

	@Override
	public String eventPath() {
		return "users.variables.removed";
	}

	public UserVariableRemovedEvent(SfsUser user, UserVariable variable) {
		this.user = user;
		this.variable = variable;
	}

	/**
	 * Retrieves the variable instance
	 * 
	 * @return UserVariable instance
	 */
	public UserVariable getVariable() {
		return variable;
	}

	/**
	 * Retrieves the smartfox user object
	 * 
	 * @return SfsUser instance
	 */
	public SfsUser getUser() {
		return user;
	}

}

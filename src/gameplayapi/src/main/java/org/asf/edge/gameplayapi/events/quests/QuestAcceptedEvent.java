package org.asf.edge.gameplayapi.events.quests;

import org.asf.edge.common.services.accounts.AccountObject;
import org.asf.edge.common.services.accounts.AccountSaveContainer;
import org.asf.edge.gameplayapi.entities.quests.UserQuestInfo;
import org.asf.edge.gameplayapi.events.GameplayApiServerEvent;
import org.asf.edge.gameplayapi.services.quests.QuestManager;
import org.asf.edge.modules.eventbus.EventPath;

/**
 * 
 * Quest accepted event - called when quests are accepted from their quest giver
 * NPC
 * 
 * @author Sky Swimmer
 *
 */
@EventPath("quests.questmanager.quest.accepted")
public class QuestAcceptedEvent extends GameplayApiServerEvent {

	private UserQuestInfo quest;
	private AccountSaveContainer save;
	private QuestManager questManager;

	public QuestAcceptedEvent(UserQuestInfo quest, AccountSaveContainer save, QuestManager questManager) {
		this.quest = quest;
		this.save = save;
		this.questManager = questManager;
	}

	@Override
	public String eventPath() {
		return "quests.questmanager.quest.accepted";
	}

	/**
	 * Retrieves the quest manager instance
	 * 
	 * @return QuestManager instance
	 */
	public QuestManager getQuestManager() {
		return questManager;
	}

	/**
	 * Retrieves the quest instance
	 * 
	 * @return UserQuestInfo instance
	 */
	public UserQuestInfo getQuest() {
		return quest;
	}

	/**
	 * Retrieves the account save container
	 * 
	 * @return AccountSaveContainer instance
	 */
	public AccountSaveContainer getAccountSave() {
		return save;
	}

	/**
	 * Retrieves the account object
	 * 
	 * @return AccountObject instance
	 */
	public AccountObject getAccount() {
		return save.getAccount();
	}

}

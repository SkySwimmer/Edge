package org.asf.edge.gameplayapi.services.quests.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asf.connective.tasks.AsyncTaskManager;
import org.asf.edge.common.entities.achivements.RankTypeID;
import org.asf.edge.common.entities.items.PlayerInventoryItem;
import org.asf.edge.common.events.achievements.RankChangedEvent;
import org.asf.edge.common.events.items.InventoryItemCreateEvent;
import org.asf.edge.common.events.items.InventoryItemDeleteEvent;
import org.asf.edge.common.events.items.InventoryItemQuantityUpdateEvent;
import org.asf.edge.common.experiments.EdgeDefaultExperiments;
import org.asf.edge.common.experiments.ExperimentManager;
import org.asf.edge.common.services.accounts.AccountDataContainer;
import org.asf.edge.common.services.accounts.AccountObject;
import org.asf.edge.common.services.accounts.AccountSaveContainer;
import org.asf.edge.common.services.achievements.AchievementManager;
import org.asf.edge.common.services.commondata.CommonDataContainer;
import org.asf.edge.common.services.commondata.CommonDataManager;
import org.asf.edge.common.services.config.ConfigProviderService;
import org.asf.edge.gameplayapi.entities.quests.UserQuestInfo;
import org.asf.edge.gameplayapi.events.quests.QuestAcceptedEvent;
import org.asf.edge.gameplayapi.events.quests.QuestCompletedEvent;
import org.asf.edge.gameplayapi.events.quests.QuestManagerLoadEvent;
import org.asf.edge.gameplayapi.events.quests.QuestResetEvent;
import org.asf.edge.gameplayapi.events.quests.QuestStartedEvent;
import org.asf.edge.gameplayapi.events.quests.QuestTaskCompletionEvent;
import org.asf.edge.gameplayapi.events.quests.QuestTaskProgressionEvent;
import org.asf.edge.gameplayapi.events.quests.QuestUnlockEvent;
import org.asf.edge.gameplayapi.services.quests.QuestManager;
import org.asf.edge.common.util.InventoryUtil;
import org.asf.edge.common.util.RewardUtil;
import org.asf.edge.common.xmls.achievements.AchievementRewardData;
import org.asf.edge.common.xmls.dragons.DragonData;
import org.asf.edge.common.xmls.inventories.SetCommonInventoryRequestData;
import org.asf.edge.gameplayapi.xmls.quests.MissionData;
import org.asf.edge.gameplayapi.xmls.quests.MissionData.MissionRulesBlock;
import org.asf.edge.gameplayapi.xmls.quests.MissionData.TaskBlock;
import org.asf.edge.gameplayapi.xmls.quests.MissionData.MissionRulesBlock.CriteriaBlock.RuleInfoBlock.RuleInfoTypes;
import org.asf.edge.gameplayapi.xmls.quests.MissionData.MissionRulesBlock.PrerequisiteInfoBlock;
import org.asf.edge.gameplayapi.xmls.quests.SetTaskStateResultData;
import org.asf.edge.gameplayapi.xmls.quests.SetTaskStateResultData.CompletedMissionInfoBlock;
import org.asf.edge.gameplayapi.xmls.quests.edgespecific.QuestRegistryManifest;
import org.asf.edge.modules.IEdgeModule;
import org.asf.edge.modules.ModuleManager;
import org.asf.edge.modules.eventbus.EventBus;
import org.asf.edge.modules.eventbus.EventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonArray;

public class QuestManagerImpl extends QuestManager {

	private Logger logger;
	private HashMap<Integer, MissionData> quests = new HashMap<Integer, MissionData>();
	private HashMap<Integer, MissionData> allQuests = new HashMap<Integer, MissionData>();

	private long lastReloadTime;
	private long lastQuestUpdateTime;
	private String questDataVersion;
	private String lastQuestUpdateVersion;

	@EventListener
	public void rankXpChanged(RankChangedEvent event) throws IOException {
		// Check if any prerequisite is present for this item
		boolean updated = false;
		for (MissionData def : this.getAllQuestDefs()) {
			// Check prerequisites
			if (def.missionRules != null) {
				if (def.missionRules.prerequisites != null) {
					for (PrerequisiteInfoBlock req : def.missionRules.prerequisites) {
						if (!req.clientRule) {
							// Check type
							switch (req.type) {

							// Rank rule
							case MissionRulesBlock.PrerequisiteInfoBlock.PrerequisiteRuleTypes.RANK: {
								// Check rank
								String[] idsStrs = req.value.split(",");
								int typeID = 0;
								if (idsStrs.length >= 1)
									typeID = Integer.parseInt(idsStrs[0]);

								// Check achievement rank
								if (typeID == event.getEntityRank().getTypeID().getPointTypeID())
									updated = true;
								break;
							}

							}
						}
						if (updated)
							break;
					}
				}
			}
			if (updated)
				break;
		}

		// Check
		if (updated) {
			// Recompute quests
			AsyncTaskManager.runAsync(() -> {
				recomputeQuests(event.getSave());
			});
		}
	}

	@EventListener
	public void itemCreated(InventoryItemCreateEvent event) {
		itemChanged(event.getAccount(), event.getSaveData(), event.getItem());
	}

	@EventListener
	public void itemDeleted(InventoryItemDeleteEvent event) {
		itemChanged(event.getAccount(), event.getSaveData(), event.getItem());
	}

	@EventListener
	public void itemQuantityChanged(InventoryItemQuantityUpdateEvent event) {
		itemChanged(event.getAccount(), event.getSaveData(), event.getItem());
	}

	private void itemChanged(AccountObject account, AccountDataContainer saveData, PlayerInventoryItem item) {
		if (saveData.getSave() == null)
			return;

		// Check if any prerequisite is present for this item
		boolean hasPrerequisite = false;
		for (MissionData def : this.getAllQuestDefs()) {
			// Check prerequisites
			if (def.missionRules != null) {
				if (def.missionRules.prerequisites != null) {
					for (PrerequisiteInfoBlock req : def.missionRules.prerequisites) {
						if (!req.clientRule) {
							// Check type
							switch (req.type) {

							// Item rule
							case MissionRulesBlock.PrerequisiteInfoBlock.PrerequisiteRuleTypes.ITEM: {
								// Check item
								int itmId = Integer.parseInt(req.value);
								if (item.getItemDefID() == itmId) {
									hasPrerequisite = true;
								}
								break;
							}

							}
						}
						if (hasPrerequisite)
							break;
					}
				}
			}
			if (hasPrerequisite)
				break;
		}

		// Check
		if (hasPrerequisite) {
			// Recompute quests
			AsyncTaskManager.runAsync(() -> {
				recomputeQuests(saveData.getSave());
			});
		}
	}

	@Override
	public void initService() {
		logger = LogManager.getLogger("QuestManager");

		// Load
		loadQuests();

		// Load update time
		try {
			if (!ConfigProviderService.getInstance().configExists("server", "questversion")) {
				try {
					JsonObject conf = new JsonObject();
					conf.addProperty("__COMMENT1__",
							"this file controls the quest version, each time quest data is updated this file should also be updated to hold a new version ID");
					conf.addProperty("__COMMENT2__",
							"you MUST update this file manually otherwise quests wont be recomputed after user content updates");
					conf.addProperty("version", System.currentTimeMillis());
					ConfigProviderService.getInstance().saveConfig("server", "questversion", conf);
				} catch (IOException e) {
				}
			}
			try {
				lastQuestUpdateVersion = ConfigProviderService.getInstance().loadConfig("server", "questversion")
						.get("version").getAsString();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} catch (Exception e) {
			logger.error("Failed to load update time", e);
		}

		// Start reload watchdog
		try {
			CommonDataContainer cont = CommonDataManager.getInstance().getContainer("QUESTMANAGER");
			try {
				if (!cont.entryExists("lastreload")) {
					lastReloadTime = System.currentTimeMillis();
					cont.setEntry("lastreload", new JsonPrimitive(lastReloadTime));
				} else
					lastReloadTime = cont.getEntry("lastreload").getAsLong();
			} catch (IOException e) {
			}
			AsyncTaskManager.runAsync(() -> {
				while (true) {
					// Check reload
					try {
						long reload = cont.getEntry("lastreload").getAsLong();
						if (reload > lastReloadTime) {
							// Trigger reload
							lastReloadTime = reload;
							loadQuests();
						}
					} catch (IOException e) {
					}
					try {
						Thread.sleep(30000);
					} catch (InterruptedException e) {
					}
				}
			});

			// Start quest date check watchdog
			try {
				if (!cont.entryExists("lastupdate")) {
					lastQuestUpdateTime = System.currentTimeMillis();
					cont.setEntry("lastupdate", new JsonPrimitive(lastQuestUpdateTime));
				} else
					lastQuestUpdateTime = cont.getEntry("lastupdate").getAsLong();
			} catch (IOException e) {
			}
			AsyncTaskManager.runAsync(() -> {
				while (true) {
					// Check quest defs
					try {
						// Check all quest defs
						JsonArray allDateActiveDefsLast = new JsonArray();
						if (cont.entryExists("activedefs"))
							allDateActiveDefsLast = cont.getEntry("activedefs").getAsJsonArray();
						ArrayList<String> defsActiveLast = new ArrayList<String>();
						ArrayList<String> defsActiveCurrent = new ArrayList<String>();
						for (JsonElement el : allDateActiveDefsLast)
							defsActiveLast.add(el.getAsString());

						// Load current defs
						for (MissionData def : this.getAllQuestDefs()) {
							boolean active = true;

							// Check prerequisites
							if (def.missionRules != null) {
								if (def.missionRules.prerequisites != null) {
									for (PrerequisiteInfoBlock req : def.missionRules.prerequisites) {
										if (!req.clientRule) {
											// Check type
											switch (req.type) {

											// Date range rule
											case MissionRulesBlock.PrerequisiteInfoBlock.PrerequisiteRuleTypes.DATERANGE: {
												// Parse
												String[] dStrs = req.value.split(",");
												if (dStrs.length == 2) {
													String startDate = dStrs[0];
													String endDate = dStrs[1];

													try {
														// Parse dates
														SimpleDateFormat fmt = new SimpleDateFormat(
																"MM'-'dd'-'yyyy HH':'mm':'ss");
														Date start = fmt.parse(startDate);
														Date end = fmt.parse(endDate);

														// Check
														Date now = new Date(System.currentTimeMillis());
														if (start.before(now) || end.after(now)) {
															active = false;
														}
													} catch (ParseException e) {
														try {
															// Parse dates
															SimpleDateFormat fmt = new SimpleDateFormat(
																	"MM'-'dd'-'yyyy");
															Date start = fmt.parse(startDate);
															Date end = fmt.parse(endDate);

															// Check
															Date now = new Date(System.currentTimeMillis());
															if (start.before(now) || end.after(now)) {
																active = false;
															}
														} catch (ParseException e2) {
															try {
																// Parse dates
																SimpleDateFormat fmt = new SimpleDateFormat(
																		"dd'/'MM'/'yyyy");
																Date start = fmt.parse(startDate);
																Date end = fmt.parse(endDate);

																// Check
																Date now = new Date(System.currentTimeMillis());
																if (start.before(now) || end.after(now)) {
																	active = false;
																}
															} catch (ParseException e3) {
																throw new RuntimeException(e);
															}
														}
													}
												}

												break;
											}

											}
										}
									}
								}
							}

							// Check
							if (active)
								defsActiveCurrent.add(def.id + "-" + def.version);
						}

						// Check def lists
						boolean changed = false;
						if (defsActiveCurrent.size() != defsActiveLast.size()) {
							changed = true;
						} else {
							// Go through the lists
							for (String d : defsActiveCurrent) {
								if (!defsActiveLast.contains(d)) {
									changed = true;
									break;
								}
							}
							for (String d : defsActiveLast) {
								if (!defsActiveCurrent.contains(d)) {
									changed = true;
									break;
								}
							}
						}

						// If changed, update
						if (changed) {
							// Create new def list
							JsonArray newDefs = new JsonArray();
							for (String d : defsActiveCurrent)
								newDefs.add(d);
							cont.setEntry("activedefs", newDefs);

							// Update time
							lastQuestUpdateTime = System.currentTimeMillis();
							cont.setEntry("lastupdate", new JsonPrimitive(lastQuestUpdateTime));
						}
					} catch (IOException e) {
					}
					try {
						Thread.sleep(30000);
					} catch (InterruptedException e) {
					}
				}
			});
		} catch (Exception e) {
			logger.error("Failed to start watchdogs!", e);
		}
	}

	private void loadQuests() {
		// Load quests
		logger.info("Loading quest data...");
		try {
			// Load XML
			InputStream strm = getClass().getClassLoader().getResourceAsStream("questdata.xml");
			String data = new String(strm.readAllBytes(), "UTF-8");
			strm.close();

			// Load into map
			XmlMapper mapper = new XmlMapper();
			QuestRegistryManifest questReg = mapper.readValue(data, QuestRegistryManifest.class);
			questDataVersion = questReg.questDataVersion;

			// Load quests
			logger.info("Loading quest definitions...");
			HashMap<Integer, MissionData> quests = new HashMap<Integer, MissionData>();
			for (MissionData quest : questReg.defaultQuestDefs.questDefs) {
				// Load quest
				quests.put(quest.id, quest);
				scanQuest(quest);
			}

			// Load quest transformers
			logger.info("Loading quest transformers...");
			loadTransformers(getClass(), quests);

			// Load module transformers
			for (IEdgeModule module : ModuleManager.getLoadedModules()) {
				loadTransformers(module.getClass(), quests);
			}

			// Load all transformers from disk
			File transformersQuests = new File("questtransformers");
			if (transformersQuests.exists()) {
				for (File transformer : transformersQuests
						.listFiles(t -> t.getName().endsWith(".xml") || t.isDirectory())) {
					loadTransformer(transformer, quests);
				}
			}

			// Apply
			this.quests = quests;

			// Dispatch event
			logger.info("Dispatching load event...");
			EventBus.getInstance().dispatchEvent(new QuestManagerLoadEvent(this));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void applyTransformer(MissionData def, HashMap<Integer, MissionData> quests) {
		// Find def, if not present, define a new one
		MissionData old = quests.get(def.id);
		if (old == null) {
			// Define new
			quests.put(def.id, def);
			scanQuest(def);
		} else {
			// Transform
			if (def.staticData != null)
				old.staticData = def.staticData;
			if (def.acceptanceAchievementID != 0)
				old.acceptanceAchievementID = def.acceptanceAchievementID;
			if (def.acceptanceRewards != null && def.acceptanceRewards.length != 0)
				old.acceptanceRewards = def.acceptanceRewards;
			if (def.achievementID != 0)
				old.achievementID = def.achievementID;
			if (def.childMissions != null && def.childMissions.length != 0)
				old.childMissions = def.childMissions;
			if (def.groupID != 0)
				old.groupID = def.groupID;
			if (def.missionRules != null)
				old.missionRules = def.missionRules;
			if (def.name != null)
				old.name = def.name;
			if (def.name != null)
				old.name = def.name;
			if (def.repeatable != null)
				old.repeatable = def.repeatable;
			if (def.tasks != null && def.tasks.length != 0)
				old.tasks = def.tasks;
			if (def.rewards != null && def.rewards.length != 0)
				old.rewards = def.rewards;
			if (def.version != 0)
				old.version = def.version;
			updateQuest(old);
		}
	}

	private void loadTransformer(File transformer, HashMap<Integer, MissionData> quests) {
		if (transformer.isFile()) {
			logger.debug("Loading transformer: '" + transformer.getPath() + "'...");
			try {
				// Find the transformer
				InputStream strm = new FileInputStream(transformer);

				// Load transformer
				XmlMapper mapper = new XmlMapper();
				MissionData def = mapper.reader().readValue(new String(strm.readAllBytes(), "UTF-8"),
						MissionData.class);
				strm.close();
				applyTransformer(def, quests);
			} catch (Exception e) {
				logger.error("Transformer failed to load: " + transformer.getPath(), e);
			}
		} else {
			logger.debug("Loading transformers from " + transformer.getPath() + "...");
			for (File tr : transformer.listFiles(t -> t.getName().endsWith(".xml") || t.isDirectory())) {
				loadTransformer(tr, quests);
			}
		}
	}

	private void loadTransformers(Class<?> cls, HashMap<Integer, MissionData> quests) {
		URL source = cls.getProtectionDomain().getCodeSource().getLocation();

		// Generate a base URL
		String baseURL = "";
		String fileName = "";
		try {
			File sourceFile = new File(source.toURI());
			fileName = sourceFile.getName();
			if (sourceFile.isDirectory()) {
				baseURL = source + (source.toString().endsWith("/") ? "" : "/");
			} else {
				baseURL = "jar:" + source + "!/";
			}
		} catch (Exception e) {
			return;
		}

		try {
			// Find the transformer document
			logger.debug("Loading transformers from " + fileName + "...");
			InputStream strm = new URL(baseURL + "questtransformers/index.json").openStream();
			JsonArray index = JsonParser.parseString(new String(strm.readAllBytes(), "UTF-8")).getAsJsonArray();
			strm.close();

			// Load all transformers
			for (JsonElement ele : index) {
				logger.debug("Loading transformer: 'questtransformers/" + ele.getAsString() + ".xml'...");
				try {
					// Find the transformer
					strm = new URL(baseURL + "questtransformers/" + ele.getAsString() + ".xml").openStream();

					// Load transformer
					XmlMapper mapper = new XmlMapper();
					MissionData def = mapper.reader().readValue(new String(strm.readAllBytes(), "UTF-8"),
							MissionData.class);
					strm.close();
					applyTransformer(def, quests);
				} catch (Exception e) {
					logger.error("Transformer failed to load: " + ele.getAsString() + " (" + fileName + ")", e);
				}
			}
		} catch (Exception e) {
			if (e instanceof FileNotFoundException)
				return;
			throw new RuntimeException(e);
		}
	}

	private void scanQuest(MissionData quest) {
		allQuests.put(quest.id, quest);
		logger.debug("Registered quest definition: " + quest.id + ": " + quest.name);
		if (quest.childMissions != null) {
			for (MissionData cQuest : quest.childMissions) {
				cQuest.parentQuestID = quest.id;
				scanQuest(cQuest);
			}
		}
	}

	private void updateQuest(MissionData quest) {
		allQuests.put(quest.id, quest);
		logger.debug("Updated quest definition: " + quest.id + ": " + quest.name);
		if (quest.childMissions != null) {
			for (MissionData cQuest : quest.childMissions) {
				cQuest.parentQuestID = quest.id;
				updateQuest(cQuest);
			}
		}
	}

	@Override
	public int[] getQuestIDs() {
		int[] ids = new int[quests.size()];
		int i = 0;
		for (int id : quests.keySet())
			ids[i++] = id;
		return ids;
	}

	@Override
	public int[] getAllQuestIDs() {
		int[] ids = new int[allQuests.size()];
		int i = 0;
		for (int id : allQuests.keySet())
			ids[i++] = id;
		return ids;
	}

	@Override
	public MissionData getQuestDef(int id) {
		return allQuests.get(id);
	}

	@Override
	public MissionData[] getQuestDefs() {
		return quests.values().toArray(t -> new MissionData[t]);
	}

	@Override
	public MissionData[] getAllQuestDefs() {
		return allQuests.values().toArray(t -> new MissionData[t]);
	}

	@Override
	public UserQuestInfo getUserQuest(AccountSaveContainer save, int id) {
		// Find def
		MissionData def = getQuestDef(id);
		if (def == null)
			return null;
		return new UserQuestInfoImpl(def, save);
	}

	@Override
	public UserQuestInfo[] getCompletedQuests(AccountSaveContainer save) {
		try {
			// Load completed quests
			AccountDataContainer data = save.getSaveData().getChildContainer("quests");
			if (!data.entryExists("completedquests"))
				return new UserQuestInfo[0];

			// Create list
			ArrayList<UserQuestInfo> quests = new ArrayList<UserQuestInfo>();
			JsonArray completedQuests = data.getEntry("completedquests").getAsJsonArray();
			for (JsonElement ele : completedQuests) {
				// Find quest
				quests.add(getUserQuest(save, ele.getAsInt()));
			}

			// Return
			return quests.toArray(t -> new UserQuestInfo[t]);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public UserQuestInfo[] getActiveQuests(AccountSaveContainer save) {
		try {
			// Load active quests
			AccountDataContainer data = save.getSaveData().getChildContainer("quests");
			if (!data.entryExists("activequests") || !data.entryExists("lastupdate")
					|| data.getEntry("lastupdate").getAsLong() != lastQuestUpdateTime
					|| !data.entryExists("lastupdate_serverdata")
					|| !data.getEntry("lastupdate_serverdata").getAsString().equals(lastQuestUpdateVersion)
					|| !data.entryExists("lastupdate_serverver")
					|| !data.getEntry("lastupdate_serverver").getAsString().equals(questDataVersion)) {
				recomputeActiveQuests(save);
				return getActiveQuests(save);
			}

			// Create list
			ArrayList<UserQuestInfo> quests = new ArrayList<UserQuestInfo>();
			JsonArray activeQuests = data.getEntry("activequests").getAsJsonArray();
			for (JsonElement ele : activeQuests) {
				// Find quest
				quests.add(getUserQuest(save, ele.getAsInt()));
			}

			// Return
			return quests.toArray(t -> new UserQuestInfo[t]);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public UserQuestInfo[] getUpcomingQuests(AccountSaveContainer save) {
		try {
			// Load active quests
			AccountDataContainer data = save.getSaveData().getChildContainer("quests");
			if (!data.entryExists("activequests") || !data.entryExists("lastupdate")
					|| data.getEntry("lastupdate").getAsLong() != lastQuestUpdateTime
					|| !data.entryExists("lastupdate_serverdata")
					|| !data.getEntry("lastupdate_serverdata").getAsString().equals(lastQuestUpdateVersion)
					|| !data.entryExists("lastupdate_serverver")
					|| !data.getEntry("lastupdate_serverver").getAsString().equals(questDataVersion)) {
				recomputeUpcomingQuests(save);
				return getUpcomingQuests(save);
			}

			// Create list
			ArrayList<UserQuestInfo> quests = new ArrayList<UserQuestInfo>();
			JsonArray upcomingQuests = data.getEntry("upcomingquests").getAsJsonArray();
			for (JsonElement ele : upcomingQuests) {
				// Find quest
				quests.add(getUserQuest(save, ele.getAsInt()));
			}

			// Return
			return quests.toArray(t -> new UserQuestInfo[t]);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void recomputeActiveQuests(AccountSaveContainer save) {
		try {
			// Load data container and prepare lists
			AccountDataContainer data = save.getSaveData().getChildContainer("quests");
			JsonArray active = new JsonArray();
			JsonArray activeOld = data.entryExists("activequests") ? data.getEntry("activequests").getAsJsonArray()
					: new JsonArray();
			ArrayList<Integer> activeQuests = new ArrayList<Integer>();
			ArrayList<Integer> prevActiveQuests = new ArrayList<Integer>();
			for (JsonElement ele : activeOld)
				prevActiveQuests.add(ele.getAsInt());

			// Find active quests
			for (MissionData mission : this.quests.values()) {
				UserQuestInfo q = getUserQuest(save, mission.id);
				if (((mission.repeatable != null && mission.repeatable.equalsIgnoreCase("true")) || !q.isCompleted())) {
					if (q.isActive()) {
						active.add(q.getQuestID());
						activeQuests.add(q.getQuestID());
						if (!prevActiveQuests.contains(q.getQuestID())) {
							// Dispatch event
							EventBus.getInstance().dispatchEvent(new QuestUnlockEvent(q, save, this));
						}
					}
				}
			}

			// Save
			data.setEntry("activequests", active);
			data.setEntry("lastupdate", new JsonPrimitive(lastQuestUpdateTime));
			data.setEntry("lastupdate_serverdata", new JsonPrimitive(lastQuestUpdateVersion));
			data.setEntry("lastupdate_serverver", new JsonPrimitive(questDataVersion));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void recomputeUpcomingQuests(AccountSaveContainer save) {
		try {
			// Load data container and prepare lists
			AccountDataContainer data = save.getSaveData().getChildContainer("quests");
			JsonArray upcoming = new JsonArray();

			// Find upcoming quests
			for (MissionData mission : this.quests.values()) {
				UserQuestInfo q = getUserQuest(save, mission.id);
				if (!q.isCompleted() && !q.isActive()) {
					upcoming.add(q.getQuestID());
				}
			}

			// Save
			data.setEntry("upcomingquests", upcoming);
			data.setEntry("lastupdate", new JsonPrimitive(lastQuestUpdateTime));
			data.setEntry("lastupdate_serverdata", new JsonPrimitive(lastQuestUpdateVersion));
			data.setEntry("lastupdate_serverver", new JsonPrimitive(questDataVersion));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void recomputeQuests(AccountSaveContainer save) {
		try {
			// You may wonder:
			// Why arent i calling the above computation methods here?
			//
			// Well, optimization, the above ones are designed to work without one another,
			// the one here uses data gathered from computing active quests to speed up
			// computing upcoming quests instead of having to go through the database for
			// each quest again after that

			// Load data container and prepare lists
			AccountDataContainer data = save.getSaveData().getChildContainer("quests");
			JsonArray active = new JsonArray();
			JsonArray upcoming = new JsonArray();
			JsonArray activeOld = data.entryExists("activequests") ? data.getEntry("activequests").getAsJsonArray()
					: new JsonArray();
			ArrayList<Integer> activeQuests = new ArrayList<Integer>();
			ArrayList<Integer> prevActiveQuests = new ArrayList<Integer>();
			for (JsonElement ele : activeOld)
				prevActiveQuests.add(ele.getAsInt());

			// Find active quests
			for (MissionData mission : this.quests.values()) {
				UserQuestInfo q = getUserQuest(save, mission.id);
				if ((mission.repeatable != null && mission.repeatable.equalsIgnoreCase("true")) || !q.isCompleted()) {
					if (q.isActive()) {
						active.add(q.getQuestID());
						activeQuests.add(q.getQuestID());
						if (!prevActiveQuests.contains(q.getQuestID())) {
							// Dispatch event
							EventBus.getInstance().dispatchEvent(new QuestUnlockEvent(q, save, this));
						}
					}
				}
			}

			// Find upcoming quests
			for (MissionData mission : this.quests.values()) {
				UserQuestInfo q = getUserQuest(save, mission.id);
				if (!activeQuests.contains(q.getQuestID()) && !q.isCompleted()) {
					upcoming.add(q.getQuestID());
				}
			}

			// Save
			data.setEntry("activequests", active);
			data.setEntry("upcomingquests", upcoming);
			data.setEntry("lastupdate", new JsonPrimitive(lastQuestUpdateTime));
			data.setEntry("lastupdate_serverdata", new JsonPrimitive(lastQuestUpdateVersion));
			data.setEntry("lastupdate_serverver", new JsonPrimitive(questDataVersion));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private class UserQuestInfoImpl extends UserQuestInfo {

		private MissionData def;
		private AccountDataContainer data;
		private AccountSaveContainer save;
		private JsonObject questInfoData;

		private Object questInfoLock = new Object();

		private void populateQuestInfoIfNeeded() {
			if (questInfoData != null)
				return;

			// Lock
			synchronized (questInfoLock) {
				if (questInfoData != null)
					return; // Another thread did it before us

				// Populate data
				try {
					data = save.getSaveData().getChildContainer("quests");
					if (data.entryExists("quest-" + def.id)) {
						// Load quest
						questInfoData = data.getEntry("quest-" + def.id).getAsJsonObject();
						if (!questInfoData.has("accepted"))
							questInfoData.addProperty("accepted", false);
						if (!questInfoData.has("started"))
							questInfoData.addProperty("started", false);
						if (!questInfoData.has("completed"))
							questInfoData.addProperty("completed", false);
					} else {
						// Not found
						questInfoData = new JsonObject();
						questInfoData.addProperty("completed", false);
						questInfoData.addProperty("accepted", false);
						questInfoData.addProperty("started", false);
						questInfoData.add("payload", new JsonObject());
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

		public UserQuestInfoImpl(MissionData def, AccountSaveContainer save) {
			this.def = def;
			this.save = save;

			// Load object
			try {
				data = save.getSaveData().getChildContainer("quests");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public int getQuestID() {
			return def.id;
		}

		@Override
		public MissionData getDef() {
			return def;
		}

		@Override
		public MissionData getData() {
			populateQuestInfoIfNeeded();

			// Retrieve data
			MissionData data = def.copy();

			// Get child missions
			if (data.childMissions != null) {
				for (int i = 0; i < data.childMissions.length; i++) {
					// Make sure the children are updated too
					data.childMissions[i] = getUserQuest(save, data.childMissions[i].id).getData();
				}
			}

			// Apply modifications
			JsonObject payload = questInfoData.get("payload").getAsJsonObject();
			if (questInfoData.has("accepted"))
				data.accepted = questInfoData.get("accepted").getAsBoolean();
			if (questInfoData.has("completed"))
				data.completed = questInfoData.get("completed").getAsBoolean() ? 1 : 0;
			if (payload.has("tasks") && data.tasks != null) {
				// Apply task modifications
				JsonObject tasksBase = payload.get("tasks").getAsJsonObject();
				for (MissionData.TaskBlock task : data.tasks) {
					if (tasksBase.has(Integer.toString(task.id))) {
						// Apply modification
						JsonObject taskPayload = tasksBase.get(Integer.toString(task.id)).getAsJsonObject();
						if (taskPayload.has("completed"))
							task.completed = taskPayload.get("completed").getAsBoolean() ? 1 : 0;
						if (taskPayload.has("payload"))
							task.payload = taskPayload.get("payload").getAsString();
					}
				}
			}

			// Set criteria
			if (data.missionRules != null && data.missionRules.criteria != null
					&& data.missionRules.criteria.rules != null) {
				for (MissionData.MissionRulesBlock.CriteriaBlock.RuleInfoBlock rule : data.missionRules.criteria.rules) {
					if (rule.type == RuleInfoTypes.MISSION) {
						// Mission completion
						int missionID = rule.id;
						if (missionID == data.id) {
							// Set completion state
							rule.complete = data.completed;
						} else {
							// Find mission
							UserQuestInfo i = getUserQuest(save, missionID);
							if (i != null)
								rule.complete = i.isCompleted() ? 1 : 0;
						}
					} else {
						// Task completion
						UserQuestInfo i = this;
						int missionID = rule.missionID;
						if (missionID != data.id)
							i = getUserQuest(save, missionID);
						if (i != null) {
							// Check mission
							MissionData d = data;
							if (missionID != data.id)
								d = i.getData();

							// Find task
							Optional<TaskBlock> opt = Stream.of(d.tasks).filter(t -> t.id == rule.id).findFirst();
							if (opt.isPresent()) {
								rule.complete = opt.get().completed;
							}
						}
					}
				}
			}

			// Return
			return data;
		}

		@Override
		public void acceptQuest() {
			// Update info
			populateQuestInfoIfNeeded();

			// Check
			if (questInfoData.get("started").getAsBoolean())
				return; // Already started but lets not cause sync errors

			// Start quest
			startQuest();

			// Mark accepted
			questInfoData.addProperty("accepted", true);

			// Save
			try {
				data.setEntry("quest-" + def.id, questInfoData);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			// Compute quest name
			String questName = def.name;
			if (def.staticData != null) {
				try {
					XmlMapper mapper = new XmlMapper();
					ObjectNode nd = mapper.readValue(def.staticData, ObjectNode.class);
					if (nd.has("Title")) {
						questName = nd.get("Title").get("Text").asText();
					}
				} catch (Exception e) {
				}
			}

			// Quest accept
			logger.info("Player " + save.getUsername() + " (ID " + save.getSaveID() + ") accepted quest " + questName
					+ " (ID " + def.id + ")");

			// Acceptance rewards
			if (!ExperimentManager.getInstance().isExperimentEnabled(EdgeDefaultExperiments.ACHIEVEMENTSV1_SUPPORT)) {
				try {
					RewardUtil.addRewards(save, def.acceptanceRewards, isCompleted(), 1);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			} else {
				if (def.acceptanceAchievementID != 0)
					AchievementManager.getInstance().unlockAchievement(save, def.acceptanceAchievementID);
			}

			// Dispatch event
			EventBus.getInstance().dispatchEvent(new QuestAcceptedEvent(this, save, QuestManagerImpl.this));

			// Recompute active quests
			AsyncTaskManager.runAsync(() -> {
				recomputeQuests(save);
			});
		}

		@Override
		public SetTaskStateResultData handleTaskCall(int taskID, String payloadStr, boolean completed, int invContainer,
				SetCommonInventoryRequestData[] requests) throws IOException {
			populateQuestInfoIfNeeded();

			// Find task
			MissionData mission = getData();
			Optional<MissionData.TaskBlock> opt = Stream.of(mission.tasks).filter(t -> t.id == taskID).findFirst();
			MissionData.TaskBlock task = opt.isPresent() ? opt.get() : null;

			// Check task
			if (task == null) {
				// Invalid request
				SetTaskStateResultData resp = new SetTaskStateResultData();
				resp.success = false;
				resp.status = SetTaskStateResultData.SetTaskStateResultStatuses.MISSION_STATE_NOT_FOUND;
				return resp;
			}

			// Load payload
			JsonObject payload = questInfoData.get("payload").getAsJsonObject();
			JsonObject tasksBase;
			if (payload.has("tasks"))
				tasksBase = payload.get("tasks").getAsJsonObject();
			else {
				tasksBase = new JsonObject();
				payload.add("tasks", tasksBase);
			}

			// Security checks
			if (isCompleted() && (mission.repeatable == null || mission.repeatable.equalsIgnoreCase("false"))
					&& !tasksBase.has(Integer.toString(taskID))) {
				// Mission already completed, and not trying to update
				SetTaskStateResultData resp = new SetTaskStateResultData();
				resp.success = false;
				resp.status = SetTaskStateResultData.SetTaskStateResultStatuses.NON_REPEATABLE_MISSION;
				return resp;
			}
			// FIXME: implement more checks

			// Check if active
			if (!questInfoData.get("started").getAsBoolean()) {
				// Start quest
				startQuest();
			}

			// Load task payload
			JsonObject taskPayload;
			if (tasksBase.has(Integer.toString(taskID)))
				taskPayload = tasksBase.get(Integer.toString(taskID)).getAsJsonObject();
			else {
				taskPayload = new JsonObject();
				tasksBase.add(Integer.toString(taskID), taskPayload);
			}

			// Update task
			taskPayload.addProperty("payload", payloadStr);
			if (completed) {
				taskPayload.addProperty("completed", true);
				task.completed = 1;
			} else
				task.completed = taskPayload.has("completed") && taskPayload.get("completed").getAsBoolean() ? 1 : 0;
			task.payload = payloadStr;

			// Save
			try {
				data.setEntry("quest-" + def.id, questInfoData);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			// Prepare response
			SetTaskStateResultData resp = new SetTaskStateResultData();
			resp.status = SetTaskStateResultData.SetTaskStateResultStatuses.TASK_CAN_BE_DONE;
			resp.success = true;

			// Update inventories
			if (requests != null && requests.length != 0) {
				resp.inventoryUpdate = InventoryUtil.processCommonInventorySet(requests, save.getSaveData(),
						invContainer);
			}
			if (invContainer == -1)
				invContainer = 1;

			// Dispatch event
			EventBus.getInstance()
					.dispatchEvent(new QuestTaskProgressionEvent(this, task, payloadStr, save, QuestManagerImpl.this));

			// Compute task name
			String name = task.name;
			if (task.staticData != null) {
				try {
					XmlMapper mapper = new XmlMapper();
					ObjectNode nd = mapper.readValue(task.staticData, ObjectNode.class);
					if (nd.has("Title")) {
						name = nd.get("Title").get("Text").asText();
					}
				} catch (Exception e) {
				}
			}

			// Compute quest name
			String questName = mission.name;
			if (mission.staticData != null) {
				try {
					XmlMapper mapper = new XmlMapper();
					ObjectNode nd = mapper.readValue(mission.staticData, ObjectNode.class);
					if (nd.has("Title")) {
						questName = nd.get("Title").get("Text").asText();
					}
				} catch (Exception e) {
				}
			}

			// Compute progress
			int questProgPercentage = 0;
			if (mission.missionRules != null && mission.missionRules.criteria != null
					&& mission.missionRules.criteria.rules != null && mission.missionRules.criteria.rules.length != 0) {
				// Read minimal rules
				int minCompletedRules = mission.missionRules.criteria.min;
				if (mission.missionRules.criteria.type.equals("all"))
					minCompletedRules = mission.missionRules.criteria.rules.length;

				// Go through rules
				int completedRules = 0;
				for (MissionData.MissionRulesBlock.CriteriaBlock.RuleInfoBlock rule : mission.missionRules.criteria.rules) {
					// Handle type
					switch (rule.type) {

					case 1: {
						// Task

						// Find mission
						MissionData questD = mission;
						if (rule.missionID != mission.id) {
							// Find quest
							UserQuestInfo uQuest = QuestManagerImpl.this.getUserQuest(save, rule.missionID);
							if (uQuest == null)
								break;
							questD = uQuest.getData();
						}

						// Find task
						if (questD.tasks != null) {
							Optional<MissionData.TaskBlock> tsk = Stream.of(questD.tasks).filter(t -> t.id == rule.id)
									.findFirst();
							if (tsk.isPresent()) {
								// Check
								if (tsk.get().completed > 0)
									completedRules++;
							}
						}

						break;
					}

					case 2: {
						// Mission

						// Find mission
						MissionData tMission = QuestManagerImpl.this.getQuestDef(rule.id);
						if (tMission != null) {
							// Check completion
							if (QuestManagerImpl.this.getUserQuest(save, rule.id).isCompleted())
								completedRules++;
						}

						break;
					}

					default: {
						// Unknown
						completedRules++;
						break;
					}

					}
				}

				// Compute percentage
				questProgPercentage = (int) ((100d / (double) minCompletedRules) * completedRules);
			} else {
				questProgPercentage = 100;
			}

			// Update quests if needed
			if (completed) {
				// Task completion
				logger.info("Player " + save.getUsername() + " (ID " + save.getSaveID() + ") completed task " + name
						+ " (ID " + task.id + ")" + " of quest " + questName + " (ID " + mission.id
						+ "), quest progress: " + questProgPercentage + "%");

				// Dispatch event
				EventBus.getInstance().dispatchEvent(
						new QuestTaskCompletionEvent(this, task, payloadStr, save, QuestManagerImpl.this));

				// Check tasks
				boolean missionCompleted = isCompletedMission(mission);
				if (missionCompleted) {
					// Handle completion
					ArrayList<CompletedMissionInfoBlock> completedMissions = new ArrayList<CompletedMissionInfoBlock>();

					// Add rewards
					// Prepare object
					CompletedMissionInfoBlock i = new CompletedMissionInfoBlock();
					boolean wasCompleted = isCompleted();

					// Complete quest
					completeQuest();
					i.missionID = getQuestID();
					MissionData missionD = getDef();

					if (!ExperimentManager.getInstance()
							.isExperimentEnabled(EdgeDefaultExperiments.ACHIEVEMENTSV1_SUPPORT)) {
						// Give rewards
						i.rewards = RewardUtil.giveRewardsTo(save, missionD.rewards, wasCompleted, invContainer);
					} else {
						// Unlock achievement and give rewards
						if (missionD.achievementID != 0)
							i.rewards = AchievementManager.getInstance().unlockAchievement(save, missionD.achievementID,
									wasCompleted);
						else
							i.rewards = new AchievementRewardData[0];
					}

					// Add object
					completedMissions.add(i);

					// Set response
					resp.completedMissions = completedMissions.toArray(t -> new CompletedMissionInfoBlock[t]);

					// Recompute active quests
					AsyncTaskManager.runAsync(() -> {
						recomputeQuests(save);
					});
				}
			} else {
				// Task progression
				logger.info("Player " + save.getUsername() + " (ID " + save.getSaveID() + ") progressed task " + name
						+ " (ID " + task.id + ")" + " of quest " + questName + " (ID " + mission.id
						+ "), quest progress: " + questProgPercentage + "%");
			}

			return resp;
		}

		private boolean isCompletedMission(MissionData mission) {
			// Check criteria
			if (mission.missionRules != null && mission.missionRules.criteria != null
					&& mission.missionRules.criteria.rules != null && mission.missionRules.criteria.rules.length != 0) {
				// Read minimal rules
				int minCompletedRules = mission.missionRules.criteria.min;
				if (mission.missionRules.criteria.type.equals("all"))
					minCompletedRules = mission.missionRules.criteria.rules.length;

				// Go through rules
				int completedRules = 0;
				for (MissionData.MissionRulesBlock.CriteriaBlock.RuleInfoBlock rule : mission.missionRules.criteria.rules) {
					// Handle type
					switch (rule.type) {

					case 1: {
						// Task

						// Find mission
						MissionData questD = mission;
						if (rule.missionID != mission.id) {
							// Find quest
							UserQuestInfo uQuest = QuestManagerImpl.this.getUserQuest(save, rule.missionID);
							if (uQuest == null)
								break;
							questD = uQuest.getData();
						}

						// Find task
						if (questD.tasks != null) {
							Optional<MissionData.TaskBlock> task = Stream.of(questD.tasks).filter(t -> t.id == rule.id)
									.findFirst();
							if (task.isPresent()) {
								// Check
								if (task.get().completed > 0)
									completedRules++;
							}
						}

						break;
					}

					case 2: {
						// Mission

						// Find mission
						MissionData tMission = QuestManagerImpl.this.getQuestDef(rule.id);
						if (tMission != null) {
							// Check completion
							if (QuestManagerImpl.this.getUserQuest(save, rule.id).isCompleted())
								completedRules++;
						}

						break;
					}

					default: {
						// Unknown
						completedRules++;
						break;
					}

					}
				}

				// Check
				if (completedRules < minCompletedRules)
					return false;
			}

			// Complete
			return true;
		}

		@Override
		public void startQuest() {
			populateQuestInfoIfNeeded();

			// Check status
			if (questInfoData.get("started").getAsBoolean())
				throw new IllegalArgumentException("Quest is already started");

			// Update status
			questInfoData.addProperty("started", true);

			// Compute quest name
			String questName = def.name;
			if (def.staticData != null) {
				try {
					XmlMapper mapper = new XmlMapper();
					ObjectNode nd = mapper.readValue(def.staticData, ObjectNode.class);
					if (nd.has("Title")) {
						questName = nd.get("Title").get("Text").asText();
					}
				} catch (Exception e) {
				}
			}

			// Quest start
			logger.info("Player " + save.getUsername() + " (ID " + save.getSaveID() + ") started quest " + questName
					+ " (ID " + def.id + ")");

			// Dispatch event
			EventBus.getInstance().dispatchEvent(new QuestStartedEvent(this, save, QuestManagerImpl.this));

			try {
				// Save
				data.setEntry("quest-" + def.id, questInfoData);

				// Check
				if (def.parentQuestID > 0)
					return;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			// Recompute active quests
			AsyncTaskManager.runAsync(() -> {
				recomputeQuests(save);
			});
		}

		@Override
		public void completeQuest() {
			populateQuestInfoIfNeeded();

			// Update status
			questInfoData.addProperty("completed", true);
			questInfoData.addProperty("accepted", false);
			questInfoData.addProperty("started", false);

			// Compute quest name
			String questName = def.name;
			if (def.staticData != null) {
				try {
					XmlMapper mapper = new XmlMapper();
					ObjectNode nd = mapper.readValue(def.staticData, ObjectNode.class);
					if (nd.has("Title")) {
						questName = nd.get("Title").get("Text").asText();
					}
				} catch (Exception e) {
				}
			}

			// Quest completed
			logger.info("Player " + save.getUsername() + " (ID " + save.getSaveID() + ") completed quest " + questName
					+ " (ID " + def.id + ")");

			// Dispatch event
			EventBus.getInstance().dispatchEvent(new QuestCompletedEvent(this, save, QuestManagerImpl.this));

			try {
				// Save
				data.setEntry("quest-" + def.id, questInfoData);

				// Check
				if (def.parentQuestID > 0)
					return;

				// Update completed quests
				JsonArray completedQuests;
				if (!data.entryExists("completedquests")) {
					completedQuests = new JsonArray();
				} else
					completedQuests = data.getEntry("completedquests").getAsJsonArray();
				boolean found = false;
				for (JsonElement ele : completedQuests) {
					if (ele.getAsInt() == def.id) {
						found = true;
						break;
					}
				}
				if (!found) {
					completedQuests.add(def.id);
					data.setEntry("completedquests", completedQuests);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			// Recompute active quests
			AsyncTaskManager.runAsync(() -> {
				recomputeQuests(save);
			});
		}

		@Override
		public boolean isActive() {
			// Check parent
			if (def.parentQuestID > 0) {
				UserQuestInfo quest = getUserQuest(save, def.parentQuestID);
				if (quest != null)
					if (!quest.isActive())
						return false;
			}

			// Populate
			populateQuestInfoIfNeeded();

			// Check prerequisites
			if (def.missionRules != null) {
				if (def.missionRules.prerequisites != null) {
					for (PrerequisiteInfoBlock req : def.missionRules.prerequisites) {
						if (!req.clientRule) {
							// Check type
							switch (req.type) {

							// Accept rule
							case MissionRulesBlock.PrerequisiteInfoBlock.PrerequisiteRuleTypes.ACCEPT: {
								// Check if the mission must be accepted
								if (req.value.equalsIgnoreCase("true")) {
									if (!questInfoData.get("accepted").getAsBoolean())
										return false;
								}

								break;
							}

							// Mission rule
							case MissionRulesBlock.PrerequisiteInfoBlock.PrerequisiteRuleTypes.MISSION: {
								// Check if the mission is complete
								int missionID = Integer.parseInt(req.value);
								UserQuestInfo quest = getUserQuest(save, missionID);
								if (quest == null)
									return false;
								else if (!quest.isCompleted())
									return false;

								break;
							}

							// Date range rule
							case MissionRulesBlock.PrerequisiteInfoBlock.PrerequisiteRuleTypes.DATERANGE: {
								// Parse
								String[] dStrs = req.value.split(",");
								if (dStrs.length == 2) {
									String startDate = dStrs[0];
									String endDate = dStrs[1];

									try {
										// Parse dates
										SimpleDateFormat fmt = new SimpleDateFormat("MM'-'dd'-'yyyy HH':'mm':'ss");
										Date start = fmt.parse(startDate);
										Date end = fmt.parse(endDate);

										// Check
										Date now = new Date(System.currentTimeMillis());
										if (start.before(now) || end.after(now)) {
											return false;
										}
									} catch (ParseException e) {
										try {
											// Parse dates
											SimpleDateFormat fmt = new SimpleDateFormat("MM'-'dd'-'yyyy");
											Date start = fmt.parse(startDate);
											Date end = fmt.parse(endDate);

											// Check
											Date now = new Date(System.currentTimeMillis());
											if (start.before(now) || end.after(now)) {
												return false;
											}
										} catch (ParseException e2) {
											try {
												// Parse dates
												SimpleDateFormat fmt = new SimpleDateFormat("dd'/'MM'/'yyyy");
												Date start = fmt.parse(startDate);
												Date end = fmt.parse(endDate);

												// Check
												Date now = new Date(System.currentTimeMillis());
												if (start.before(now) || end.after(now)) {
													return false;
												}
											} catch (ParseException e3) {
												throw new RuntimeException(e);
											}
										}
									}
								}

								break;
							}

							// Item rule
							case MissionRulesBlock.PrerequisiteInfoBlock.PrerequisiteRuleTypes.ITEM: {
								// Check item
								int itmId = Integer.parseInt(req.value);
								Optional<PlayerInventoryItem> opt = Stream.of(save.getInventory().getContainers())
										.map(t -> t.findFirst(itmId)).filter(t -> t != null).findFirst();
								if (opt.isPresent()) {
									// Check quantity
									if (req.quantity > opt.get().getQuantity())
										return false;
								} else
									return false;

								break;
							}

							// Member rule
							case MissionRulesBlock.PrerequisiteInfoBlock.PrerequisiteRuleTypes.MEMBER: {
								// FIXME: quests need recomputation if this changes

								// Check member only
								if (req.value.equalsIgnoreCase("true")) {
									// TODO: membership check
									return false;
								}

								break;
							}

							// Rank rule
							case MissionRulesBlock.PrerequisiteInfoBlock.PrerequisiteRuleTypes.RANK: {
								// Check rank
								String[] idsStrs = req.value.split(",");
								int typeID = 0;
								int minimal = 0;
								int maximum = 0;
								if (idsStrs.length >= 1)
									typeID = Integer.parseInt(idsStrs[0]);
								if (idsStrs.length >= 2)
									minimal = Integer.parseInt(idsStrs[1]);
								if (idsStrs.length >= 3)
									maximum = Integer.parseInt(idsStrs[2]);

								// Check achievement rank
								RankTypeID id = RankTypeID.getByTypeID(typeID);
								try {
									String userID = rankUserID(save, id);
									int value = AchievementManager.getInstance().getRankIndex(
											AchievementManager.getInstance().getRank(save, userID, id).getRank()) + 1;
									if (value >= minimal && value < maximum)
										break;
								} catch (IOException e) {
								}
								return false;
							}

							}
						}
					}
				}
			}
			return true;
		}

		private static String rankUserID(AccountSaveContainer save, RankTypeID id)
				throws JsonMappingException, JsonProcessingException, IOException {
			String userID = save.getSaveID();
			if (id == RankTypeID.CLAN) {
				// TODO: clan XP
				return null;
			} else if (id == RankTypeID.DRAGON) {
				// Find active dragon
				AccountDataContainer data = save.getSaveData();

				// Pull dragons
				data = data.getChildContainer("dragons");
				JsonArray dragonIds = new JsonArray();
				if (data.entryExists("dragonlist"))
					dragonIds = data.getEntry("dragonlist").getAsJsonArray();
				else
					data.setEntry("dragonlist", dragonIds);

				// Find dragon
				for (JsonElement ele : dragonIds) {
					String did = ele.getAsString();
					DragonData dragon = new XmlMapper().readValue(data.getEntry("dragon-" + did).getAsString(),
							DragonData.class);

					// Check if active
					if (dragon.isSelected) {
						// Found dragon
						userID = dragon.entityID;
						break;
					}
				}
			}
			return userID;
		}

		@Override
		public boolean isCompleted() {
			populateQuestInfoIfNeeded();
			return questInfoData.get("completed").getAsBoolean();
		}

		@Override
		public boolean isStarted() {
			populateQuestInfoIfNeeded();
			return questInfoData.get("started").getAsBoolean();
		}

		@Override
		public void resetQuest() {
			// Reset
			questReset(this, QuestManagerImpl.this);

			// Recompute
			AsyncTaskManager.runAsync(() -> {
				recomputeQuests(save);
			});
		}

		private static void questReset(UserQuestInfoImpl quest, QuestManager manager) {
			// Reset
			quest.questInfoData = new JsonObject();
			quest.questInfoData.addProperty("completed", false);
			quest.questInfoData.addProperty("accepted", false);
			quest.questInfoData.addProperty("started", false);
			quest.questInfoData.add("payload", new JsonObject());

			try {
				// Remove completed
				JsonArray completedQuests;
				if (!quest.data.entryExists("completedquests")) {
					completedQuests = new JsonArray();
				} else
					completedQuests = quest.data.getEntry("completedquests").getAsJsonArray();
				boolean found = false;
				for (JsonElement ele : completedQuests) {
					if (ele.getAsInt() == quest.def.id) {
						found = true;
						completedQuests.remove(ele);
						break;
					}
				}
				if (found) {
					completedQuests.add(quest.def.id);
					quest.data.setEntry("completedquests", completedQuests);
				}

				// Save
				quest.data.setEntry("quest-" + quest.def.id, quest.questInfoData);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			// Dispatch event
			EventBus.getInstance().dispatchEvent(new QuestResetEvent(quest, quest.save, manager));

			// Reset child quests
			if (quest.def.childMissions != null) {
				for (MissionData ch : quest.def.childMissions) {
					// Reset
					UserQuestInfoImpl q = (UserQuestInfoImpl) manager.getUserQuest(quest.save, ch.id);
					questReset(q, manager);
				}
			}

		}

	}

	@Override
	public void reload() {
		// Trigger a reload on all servers
		lastReloadTime = System.currentTimeMillis();
		try {
			CommonDataManager.getInstance().getContainer("QUESTMANAGER").setEntry("lastreload",
					new JsonPrimitive(lastReloadTime));
		} catch (IOException e) {
		}
		logger.info("Reloading quest manager...");
		loadQuests();
	}

}

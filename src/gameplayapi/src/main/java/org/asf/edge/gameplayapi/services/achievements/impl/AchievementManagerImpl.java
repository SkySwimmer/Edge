package org.asf.edge.gameplayapi.services.achievements.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asf.connective.tasks.AsyncTaskManager;
import org.asf.edge.common.services.commondata.CommonDataContainer;
import org.asf.edge.common.services.commondata.CommonDataManager;
import org.asf.edge.gameplayapi.entities.achivements.RankInfo;
import org.asf.edge.gameplayapi.events.achievements.AchievementManagerLoadEvent;
import org.asf.edge.gameplayapi.services.achievements.AchievementManager;
import org.asf.edge.gameplayapi.xmls.achievements.UserRankData;
import org.asf.edge.gameplayapi.xmls.achievements.UserRankList;
import org.asf.edge.modules.IEdgeModule;
import org.asf.edge.modules.ModuleManager;
import org.asf.edge.modules.eventbus.EventBus;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public class AchievementManagerImpl extends AchievementManager {

	private Logger logger;
	private HashMap<Integer, RankInfo> ranks = new HashMap<Integer, RankInfo>();

	private long lastReloadTime;

	@Override
	public void initService() {
		logger = LogManager.getLogger("AchievementManager");

		// Start reload watchdog
		CommonDataContainer cont = CommonDataManager.getInstance().getContainer("ACHIEVEMENTMANAGER");
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
						loadData();
					}
				} catch (IOException e) {
				}
				try {
					Thread.sleep(30000);
				} catch (InterruptedException e) {
				}
			}
		});

		// Load data
		loadData();
	}

	private void loadData() {
		// Prepare
		HashMap<Integer, RankInfo> ranks = new HashMap<Integer, RankInfo>();

		// Load ranks
		logger.info("Loading rank definitions...");

		try {
			// Load XML
			InputStream strm = getClass().getClassLoader().getResourceAsStream("ranks.xml");
			String data = new String(strm.readAllBytes(), "UTF-8");
			strm.close();

			// Load into map
			XmlMapper mapper = new XmlMapper();
			UserRankList rs = mapper.reader().readValue(data, UserRankList.class);

			// Load ranks
			for (UserRankData.UserRankDataWrapper rank : rs.ranks) {
				ranks.put(rank.rankID.value, new RankInfo(rank.getUnwrapped()));
				logger.debug("Registered rank: " + rank.getUnwrapped().rankID + ": " + rank.getUnwrapped().rankName);

			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// Load rank transformers
		logger.info("Loading rank transformers...");
		loadTransformers(getClass(), ranks);

		// Load module transformers
		for (IEdgeModule module : ModuleManager.getLoadedModules()) {
			loadTransformers(module.getClass(), ranks);
		}

		// Load all transformers from disk
		File transformersRanks = new File("ranktransformers");
		if (transformersRanks.exists()) {
			for (File transformer : transformersRanks.listFiles(t -> t.getName().endsWith(".xml") || t.isDirectory())) {
				loadRankTransformer(transformer, ranks);
			}
		}

		// Apply
		this.ranks = ranks;

		// Fire event
		logger.info("Dispatching load event...");
		EventBus.getInstance().dispatchEvent(new AchievementManagerLoadEvent(this));
	}

	private void loadTransformers(Class<?> cls, HashMap<Integer, RankInfo> ranks) {
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
			InputStream strm = new URL(baseURL + "ranktransformers/index.json").openStream();
			JsonArray index = JsonParser.parseString(new String(strm.readAllBytes(), "UTF-8")).getAsJsonArray();
			strm.close();

			// Load all transformers
			for (JsonElement ele : index) {
				logger.debug("Loading transformer: 'ranktransformers/" + ele.getAsString() + ".xml'...");
				try {
					// Find the transformer
					strm = new URL(baseURL + "ranktransformers/" + ele.getAsString() + ".xml").openStream();

					// Load transformer
					XmlMapper mapper = new XmlMapper();
					UserRankData def = mapper.reader().readValue(new String(strm.readAllBytes(), "UTF-8"),
							UserRankData.class);
					strm.close();

					// Define if needed
					if (!ranks.containsKey(def.rankID)) {
						// Register
						ranks.put(def.rankID, new RankInfo(def));
						logger.debug("Registered rank: " + def.rankID + ": " + def.rankName);
					} else {
						// Update
						RankInfo r = ranks.get(def.rankID);
						if (r == null)
							throw new IllegalArgumentException("Rank definition not found: " + def.rankID);

						// Load data
						UserRankData raw = r.getRawObject();
						raw.audio = def.audio;
						raw.globalRankID = def.globalRankID;
						raw.image = def.image;
						raw.pointTypeID = def.pointTypeID;
						raw.rankDescription = def.rankDescription;
						raw.rankID = def.rankID;
						raw.rankName = def.rankName;
						raw.value = def.value;

						// Reload
						r.reload();
						logger.debug("Updated rank: " + r.getID() + ": " + r.getName());
					}
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

	private void loadRankTransformer(File transformer, HashMap<Integer, RankInfo> ranks) {
		if (transformer.isFile()) {
			logger.debug("Loading transformer: '" + transformer.getPath() + "'...");
			try {
				// Find the transformer
				InputStream strm = new FileInputStream(transformer);

				// Load transformer
				XmlMapper mapper = new XmlMapper();
				UserRankData def = mapper.reader().readValue(new String(strm.readAllBytes(), "UTF-8"),
						UserRankData.class);
				strm.close();

				// Define if needed
				if (!ranks.containsKey(def.rankID)) {
					// Register
					ranks.put(def.rankID, new RankInfo(def));
					logger.debug("Registered rank: " + def.rankID + ": " + def.rankName);
				} else {
					// Update
					RankInfo r = ranks.get(def.rankID);
					if (r == null)
						throw new IllegalArgumentException("Rank definition not found: " + def.rankID);

					// Load data
					UserRankData raw = r.getRawObject();
					raw.audio = def.audio;
					raw.globalRankID = def.globalRankID;
					raw.image = def.image;
					raw.pointTypeID = def.pointTypeID;
					raw.rankDescription = def.rankDescription;
					raw.rankID = def.rankID;
					raw.rankName = def.rankName;
					raw.value = def.value;

					// Reload
					r.reload();
					logger.debug("Updated rank: " + r.getID() + ": " + r.getName());
				}
			} catch (Exception e) {
				logger.error("Transformer failed to load: " + transformer.getPath(), e);
			}
		} else {
			logger.debug("Loading transformers from " + transformer.getPath() + "...");
			for (File tr : transformer.listFiles(t -> t.getName().endsWith(".xml") || t.isDirectory())) {
				loadRankTransformer(tr, ranks);
			}
		}
	}

	@Override
	public RankInfo[] getRankDefinitions() {
		return ranks.values().toArray(t -> new RankInfo[t]);
	}

	@Override
	public RankInfo getRankDefinition(int id) {
		return ranks.get(id);
	}

	@Override
	public void registerRankDefinition(int id, UserRankData def) {
		// Check
		if (ranks.containsKey(id))
			throw new IllegalArgumentException("Rank definition already exists " + id);
		def.rankID = id;

		// Register
		ranks.put(id, new RankInfo(def));
		logger.debug("Registered rank: " + def.rankID + ": " + def.rankName);
	}

	@Override
	public void updateRankDefinition(int id, UserRankData def) {
		// Find def
		RankInfo r = getRankDefinition(id);
		if (r == null)
			throw new IllegalArgumentException("Rank definition not found: " + id);

		// Load data
		UserRankData raw = r.getRawObject();
		raw.audio = def.audio;
		raw.globalRankID = def.globalRankID;
		raw.image = def.image;
		raw.pointTypeID = def.pointTypeID;
		raw.rankDescription = def.rankDescription;
		raw.rankID = def.rankID;
		raw.rankName = def.rankName;
		raw.value = def.value;

		// Reload
		r.reload();
		logger.debug("Updated rank: " + r.getID() + ": " + r.getName());

	}

	@Override
	public void reload() {
		// Reload
		loadData();
	}

}

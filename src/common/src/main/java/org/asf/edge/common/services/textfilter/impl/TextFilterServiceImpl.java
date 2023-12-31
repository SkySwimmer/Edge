package org.asf.edge.common.services.textfilter.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.asf.connective.tasks.AsyncTaskManager;
import org.asf.edge.common.events.textfilter.TextFilterLoadEvent;
import org.asf.edge.common.services.commondata.CommonDataContainer;
import org.asf.edge.common.services.commondata.CommonDataManager;
import org.asf.edge.common.services.textfilter.FilterMode;
import org.asf.edge.common.services.textfilter.FilterSeverity;
import org.asf.edge.common.services.textfilter.PhraseFilter;
import org.asf.edge.common.services.textfilter.PhraseFilterSet;
import org.asf.edge.common.services.textfilter.TextFilterService;
import org.asf.edge.common.services.textfilter.result.FilterResult;
import org.asf.edge.common.services.textfilter.result.WordMatch;
import org.asf.edge.modules.eventbus.EventBus;

import com.google.gson.JsonPrimitive;

public class TextFilterServiceImpl extends TextFilterService {

	private HashMap<String, PhraseFilterSet> filters = new HashMap<String, PhraseFilterSet>();
	private long lastReloadTime;

	@Override
	public void initService() {
		// Start reload watchdog
		CommonDataContainer cont = CommonDataManager.getInstance().getContainer("TEXTFILTER");
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

		// Load
		loadData();
	}

	private void loadData() {
		// Prepare
		HashMap<String, PhraseFilterSet> filters = new HashMap<String, PhraseFilterSet>();

		// Load from disk
		// Check disk
		try {
			File filter = new File("textfilter");
			if (!filter.exists()) {
				filter.mkdirs();

				// Write files
				InputStream strm = getClass().getClassLoader().getResourceAsStream("defaultfilters/alwaysfilter.etfd");
				FileOutputStream strmO = new FileOutputStream(new File(filter, "alwaysfilter.etfd"));
				strm.transferTo(strmO);
				strmO.close();
				strm.close();
				strm = getClass().getClassLoader().getResourceAsStream("defaultfilters/instamute.etfd");
				strmO = new FileOutputStream(new File(filter, "instamute.etfd"));
				strm.transferTo(strmO);
				strmO.close();
				strm.close();
				strm = getClass().getClassLoader().getResourceAsStream("defaultfilters/userfilter.etfd");
				strmO = new FileOutputStream(new File(filter, "userfilter.etfd"));
				strm.transferTo(strmO);
				strmO.close();
				strm.close();
			}

			// Load filter
			loadFilters(filter, filters);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// Apply
		this.filters = filters;

		// Call load event
		EventBus.getInstance().dispatchEvent(new TextFilterLoadEvent(this));
	}

	private void loadFilters(File filter, HashMap<String, PhraseFilterSet> filters) {
		// Go through files
		for (File d : filter.listFiles(t -> t.isDirectory()))
			loadFilters(d, filters);
		for (File f : filter.listFiles(t -> t.isFile() && t.getName().endsWith(".etfd"))) {
			// Load filter
			try {
				String name = null;
				String desc = null;
				String reason = null;
				FilterSeverity severity = FilterSeverity.NONE;

				boolean inBlock = false;
				String phrase = null;
				FilterSeverity phraseSeverity = FilterSeverity.NONE;
				String phraseReason = null;
				ArrayList<String> variants = new ArrayList<String>();
				ArrayList<FilterMode> modes = new ArrayList<FilterMode>();
				PhraseFilterSet set = null;

				int ln = 0;
				for (String line : Files.readAllLines(f.toPath())) {
					ln++;
					if (line.startsWith("#") || line.isEmpty())
						continue;
					while (line.startsWith(" "))
						line = line.substring(1);
					if (line.startsWith("#") || line.isEmpty())
						continue;

					String command = line.trim();
					String args = command;
					if (!command.equals("Endphrase") && !command.contains(" ")) {
						throw new IOException(
								"No argument for command " + command + " (" + f.getPath() + ", line " + ln + ")");
					}
					if (!command.equals("Endphrase")) {
						args = args.substring(args.indexOf(" ") + 1).trim();
						if (args.isBlank())
							throw new IOException(
									"No argument for command " + command + " (" + f.getPath() + ", line " + ln + ")");
						command = command.substring(0, command.indexOf(" "));
					}

					// Handle command
					switch (command.toLowerCase()) {

					// Set name
					case "name": {
						name = args;
						break;
					}

					// Set description
					case "description": {
						desc = args;
						break;
					}

					// Set or phrase reason
					case "reason": {
						if (!inBlock)
							reason = args;
						else
							phraseReason = args;
						break;
					}

					// Set or phrase severity
					case "severity": {
						// Find
						FilterSeverity s;
						switch (args.toLowerCase()) {

						case "always_filtered": {
							s = FilterSeverity.ALWAYS_FILTERED;
							break;
						}

						case "user_strict_mode": {
							s = FilterSeverity.USER_STRICT_MODE;
							break;
						}

						case "instamute": {
							s = FilterSeverity.INSTAMUTE;
							break;
						}

						default:
							throw new IOException(
									"Invalid severity: " + args + " (" + f.getPath() + ", line " + ln + ")");

						}

						// Assign
						if (inBlock)
							phraseSeverity = s;
						else
							severity = s;
						break;
					}

					// Variant
					case "variant": {
						variants.add(args);
						break;
					}

					// Mode
					case "mode": {
						// Find
						FilterMode m;
						switch (args.toLowerCase()) {

						case "whole_phrase": {
							m = FilterMode.WHOLE_PHRASE;
							break;
						}

						case "phrase_combined": {
							m = FilterMode.PHRASE_COMBINED;
							break;
						}

						case "word_contains": {
							m = FilterMode.WORD_CONTAINS;
							break;
						}

						case "word_combined": {
							m = FilterMode.WORD_COMBINED;
							break;
						}

						default:
							throw new IOException("Invalid mode: " + args + " (" + f.getPath() + ", line " + ln + ")");

						}

						// Add mode
						if (!inBlock)
							throw new IOException("Unexpected mode command (" + f.getPath() + ", line " + ln + ")");
						modes.add(m);

						break;
					}

					// Create phrase
					case "phrase": {
						// Check
						if (name == null)
							throw new IOException("No set name assigned (" + f.getPath() + ", line " + ln + ")");
						if (desc == null)
							throw new IOException("No set description assigned (" + f.getPath() + ", line " + ln + ")");
						if (reason == null)
							throw new IOException("No set reason assigned (" + f.getPath() + ", line " + ln + ")");

						// Create set if needed
						if (set == null)
							set = new PhraseFilterSet(name, desc, reason);

						// Setup
						phraseSeverity = severity;
						phraseReason = reason;
						phrase = args;
						inBlock = true;
						break;
					}

					// End phrase
					case "endphrase": {
						if (!inBlock)
							throw new IOException(
									"Cannot end a nonexistent phrase (" + f.getPath() + ", line " + ln + ")");

						// Check phrase
						if (phraseSeverity == FilterSeverity.NONE)
							throw new IOException("No phrase severity assigned (" + f.getPath() + ", line " + ln + ")");

						// Check modes
						if (modes.size() == 0)
							throw new IOException(
									"No phrase filter modes assigned (" + f.getPath() + ", line " + ln + ")");

						// Create
						set.addPhraseFilter(phraseSeverity, modes.toArray(t -> new FilterMode[t]), phraseReason, phrase,
								variants.toArray(t -> new String[t]));

						// Reset
						modes.clear();
						variants.clear();
						phraseReason = null;
						phrase = null;
						phraseSeverity = FilterSeverity.NONE;
						inBlock = false;

						break;
					}

					}
				}

				// Check
				if (inBlock)
					throw new IOException("Unclosed filter phrase block (" + f.getPath() + ", line " + ln + ")");
				if (name == null)
					throw new IOException("No set name assigned (" + f.getPath() + ", line " + ln + ")");
				if (desc == null)
					throw new IOException("No set description assigned (" + f.getPath() + ", line " + ln + ")");
				if (reason == null)
					throw new IOException("No set reason assigned (" + f.getPath() + ", line " + ln + ")");

				// Create set if needed
				if (set == null)
					set = new PhraseFilterSet(name, desc, reason);

				// Add
				filters.put(set.getSetName().toLowerCase(), set);
			} catch (IOException e) {
				LogManager.getLogger().error("Failed to load filter: " + f.getPath(), e);
			}
		}
	}

	@Override
	public PhraseFilterSet[] getFilterSets() {
		return filters.values().toArray(t -> new PhraseFilterSet[t]);
	}

	@Override
	public PhraseFilterSet getFilterSet(String name) {
		return filters.get(name.toLowerCase());
	}

	@Override
	public void addFilterSet(PhraseFilterSet set) {
		// Check
		if (filters.containsKey(set.getSetName().toLowerCase()))
			throw new IllegalArgumentException("Filter with name '" + set.getSetName() + "' already exists");
		filters.put(set.getSetName().toLowerCase(), set);
	}

	private boolean match(FilterMode mode, String text, String filterWord, String filterVariant) {
		// Run for specific modes
		if (filterVariant.contains(" ")) {
			String textFull = " " + text + " ";
			if (mode == FilterMode.WHOLE_PHRASE || mode == FilterMode.PHRASE_COMBINED) {
				// Check phrase
				if (textFull.toLowerCase().contains(" " + filterVariant.toLowerCase() + " "))
					return true;
			}
			if (mode == FilterMode.PHRASE_COMBINED) {
				// Check phrase
				if (textFull.toLowerCase().contains(" " + filterVariant.replace(" ", "").toLowerCase() + " "))
					return true;
			} else if (mode == FilterMode.WORD_CONTAINS || mode == FilterMode.WORD_COMBINED) {
				// Check phrase
				boolean match = false;
				boolean foundStart = false;
				String[] variantWords = filterVariant.split(" ");
				int i = 1;
				if (variantWords.length != 0) {
					String firstVariant = variantWords[0];
					for (String word : text.split(" ")) {
						if (!foundStart) {
							if (word.toLowerCase().contains(firstVariant.toLowerCase())) {
								foundStart = true;
								match = true;
							}
						} else {
							if (i == variantWords.length)
								break;
							if (!word.toLowerCase().contains(variantWords[i++].toLowerCase())) {
								match = false;
								foundStart = false;
								i = 1;
							}
						}
					}
					if (i == variantWords.length)
						if (match)
							return true;
				}
			}
			if (mode == FilterMode.WORD_COMBINED) {
				// Check phrase
				if (match(FilterMode.WORD_CONTAINS, text, filterWord, filterVariant.replace(" ", "").toLowerCase()))
					return true;
			}
		}
		if (filterWord != null) {
			if (!filterVariant.contains(" ")) {
				if (mode == FilterMode.WHOLE_PHRASE || mode == FilterMode.PHRASE_COMBINED) {
					// Check phrase
					if (filterWord.equalsIgnoreCase(filterVariant))
						return true;
				} else if (mode == FilterMode.PHRASE_COMBINED) {
					// Check phrase
					if (filterWord.equalsIgnoreCase(filterVariant.replace(" ", "").toLowerCase()))
						return true;
				} else if (mode == FilterMode.WORD_CONTAINS || mode == FilterMode.WORD_COMBINED) {
					// Check phrase
					if (filterWord.contains(filterVariant))
						return true;
				} else if (mode == FilterMode.WORD_COMBINED) {
					// Check phrase
					if (filterWord.toLowerCase().contains(filterVariant.replace(" ", "").toLowerCase()))
						return true;
				}
			}
		}

		// Unfiltered
		return false;
	}

	@Override
	public boolean isFiltered(String text, boolean strictMode) {
		// Check filter
		if (filters.values().stream().anyMatch(t -> Stream.of(t.getFilteredPhrases()).anyMatch(filter -> {
			// Check filter
			if (strictMode || (filter.getSeverity() != FilterSeverity.USER_STRICT_MODE)) {
				for (FilterMode mode : filter.getModes()) {
					if (mode == FilterMode.WHOLE_PHRASE || mode == FilterMode.PHRASE_COMBINED
							|| mode == FilterMode.WORD_CONTAINS || mode == FilterMode.WORD_COMBINED)
						continue;

					// Check phrase
					if (match(mode, text, null, filter.getPhrase()))
						return true;
					for (String variant : filter.getVariants())
						if (match(mode, null, text, variant))
							return true;
				}
			}

			// Unfiltered
			return false;
		}))) {
			// Filtered
			return true;
		}

		// Check word-by-word
		for (String word : text.split(" ")) {
			// Check filters
			String filterWord = word.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
			if (filters.values().stream().anyMatch(t -> Stream.of(t.getFilteredPhrases()).anyMatch(filter -> {
				// Check filter
				if (strictMode || (filter.getSeverity() != FilterSeverity.USER_STRICT_MODE)) {
					for (FilterMode mode : filter.getModes()) {
						if (mode != FilterMode.WHOLE_PHRASE && mode != FilterMode.PHRASE_COMBINED
								&& mode != FilterMode.WORD_CONTAINS && mode != FilterMode.WORD_COMBINED)
							continue;

						// Check phrase
						if (match(mode, text, filterWord, filter.getPhrase()))
							return true;
						for (String variant : filter.getVariants())
							if (match(mode, text, filterWord, variant))
								return true;
					}
				}

				// Unfiltered
				return false;
			}))) {
				// Filtered
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean shouldFilterMute(String text) {
		// Check filter
		if (filters.values().stream().anyMatch(t -> Stream.of(t.getFilteredPhrases()).anyMatch(filter -> {
			// Check filter
			if (filter.getSeverity() == FilterSeverity.INSTAMUTE) {
				for (FilterMode mode : filter.getModes()) {
					if (mode == FilterMode.WHOLE_PHRASE || mode == FilterMode.PHRASE_COMBINED
							|| mode == FilterMode.WORD_CONTAINS || mode == FilterMode.WORD_COMBINED)
						continue;

					// Check phrase
					if (match(mode, text, null, filter.getPhrase()))
						return true;
					for (String variant : filter.getVariants())
						if (match(mode, null, text, variant))
							return true;
				}
			}

			// Unfiltered
			return false;
		}))) {
			// Filtered
			return true;
		}

		// Check word-by-word
		for (String word : text.split(" ")) {
			// Check filters
			String filterWord = word.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
			if (filters.values().stream().anyMatch(t -> Stream.of(t.getFilteredPhrases()).anyMatch(filter -> {
				// Check filter
				if (filter.getSeverity() == FilterSeverity.INSTAMUTE) {
					for (FilterMode mode : filter.getModes()) {
						if (mode != FilterMode.WHOLE_PHRASE && mode != FilterMode.PHRASE_COMBINED
								&& mode != FilterMode.WORD_CONTAINS && mode != FilterMode.WORD_COMBINED)
							continue;

						// Check phrase
						if (match(mode, text, filterWord, filter.getPhrase()))
							return true;
						for (String variant : filter.getVariants())
							if (match(mode, text, filterWord, variant))
								return true;
					}
				}

				// Unfiltered
				return false;
			}))) {
				// Filtered
				return true;
			}
		}

		return false;
	}

	@Override
	public FilterResult filter(String text, boolean strictMode) {
		ArrayList<WordMatch> matches = new ArrayList<WordMatch>();
		ArrayList<String> matchedPhrases = new ArrayList<String>();

		// Handle word contains filters
		for (PhraseFilterSet set : filters.values()) {
			for (PhraseFilter filter : set.getFilteredPhrases()) {
				// Check mode
				if (!strictMode && filter.getSeverity() == FilterSeverity.USER_STRICT_MODE)
					continue;

				// Preload phrases
				String[] phrases = filter.getAllPhrases();

				// Go through modes
				for (FilterMode mode : filter.getModes()) {
					for (String phrase : phrases) {
						// Check phrase
						if (!phrase.contains(" ")) {
							// Simple indexof and substring if present
							while (true) {
								boolean match = false;

								// Go through words
								int i = 0;
								for (String word : text.split(" ")) {
									// Check filter
									if (((mode == FilterMode.WORD_CONTAINS || mode == FilterMode.WORD_COMBINED)
											&& word.toLowerCase().contains(phrase.toLowerCase()))
											|| ((mode == FilterMode.WHOLE_PHRASE || mode == FilterMode.PHRASE_COMBINED)
													&& word.equalsIgnoreCase(phrase))) {
										// Filter
										String textStart = text.substring(0, i);
										String textEnd = text.substring(i + word.length());
										for (int i2 = 0; i2 < word.length(); i2++) {
											textStart += "#";
										}
										text = textStart + textEnd;

										// Add match
										if (!matchedPhrases.contains(phrase.toLowerCase())) {
											matchedPhrases.add(phrase.toLowerCase());
											matches.add(new WordMatch(filter, phrase));
										}
										match = true;
									}

									// Increase
									i += word.length() + 1;
								}

								// Check
								if (!match)
									break;
							}
						} else {
							// This is a whole lot more complex-
							String[] variantWords = phrase.split(" ");
							String[] words = text.split(" ");
							while (true) {
								// Prepare
								int i = 1;
								boolean match = false;
								boolean foundStart = false;
								int startIndex = 0;
								int endIndex = 0;

								// Go through words
								if (variantWords.length != 0) {
									String firstVariant = variantWords[0];
									int ind = 0;
									for (String word : words) {
										if (!foundStart) {
											if (((mode == FilterMode.WORD_CONTAINS || mode == FilterMode.WORD_COMBINED)
													&& word.toLowerCase().contains(firstVariant.toLowerCase()))
													|| ((mode == FilterMode.WHOLE_PHRASE
															|| mode == FilterMode.PHRASE_COMBINED)
															&& word.equalsIgnoreCase(firstVariant))) {
												foundStart = true;
												startIndex = ind;
												match = true;
											}
										} else {
											if (i == variantWords.length)
												break;
											if (((mode == FilterMode.WORD_CONTAINS || mode == FilterMode.WORD_COMBINED)
													&& !word.toLowerCase().contains(variantWords[i++].toLowerCase()))
													|| ((mode == FilterMode.WHOLE_PHRASE
															|| mode == FilterMode.PHRASE_COMBINED)
															&& !word.equalsIgnoreCase(variantWords[i++]))) {
												match = false;
												foundStart = false;
												i = 1;
											}
										}
										ind++;
									}
									if (i == variantWords.length) {
										endIndex = ind - 1;

										// Check result
										if (match) {
											// Filter
											int ind2 = 0;
											int ind3 = 0;
											String textStart = "";
											String textEnd = "";
											boolean startFound = false;
											for (String word : words) {
												if (ind2 == startIndex) {
													textStart = text.substring(0, ind3);
													startFound = true;
													for (int i2 = 0; i2 < word.length(); i2++) {
														textStart += "#";
													}
												} else if (ind2 == endIndex) {
													textEnd = text.substring(ind3 + word.length());
													startFound = false;
													textStart += " ";
													for (int i2 = 0; i2 < word.length(); i2++) {
														textStart += "#";
													}
												} else if (startFound) {
													textStart += " ";
													for (int i2 = 0; i2 < word.length(); i2++) {
														textStart += "#";
													}
												}
												ind3 += word.length() + 1;
												ind2++;
											}
											text = textStart + textEnd;
											words = text.split(" ");

											// Add match
											if (!matchedPhrases.contains(phrase.toLowerCase())) {
												matchedPhrases.add(phrase.toLowerCase());
												matches.add(new WordMatch(filter, phrase));
											}
										}
									}
									if (i != variantWords.length)
										match = false;
									if (!match)
										break;
								}
							}
						}

						// Check combined mode
						if (mode == FilterMode.WORD_COMBINED || mode == FilterMode.PHRASE_COMBINED) {
							// Check phrase
							phrase = phrase.replace(" ", "");

							// Simple indexof and substring if present
							while (true) {
								boolean match = false;

								// Go through words
								int i = 0;
								for (String word : text.split(" ")) {
									// Check filter
									if ((mode == FilterMode.WORD_COMBINED
											&& word.toLowerCase().contains(phrase.toLowerCase()))
											|| (mode == FilterMode.PHRASE_COMBINED && word.equalsIgnoreCase(phrase))) {
										// Filter
										String textStart = text.substring(0, i);
										String textEnd = text.substring(i + word.length());
										for (int i2 = 0; i2 < word.length(); i2++) {
											textStart += "#";
										}
										text = textStart + textEnd;

										// Add match
										if (!matchedPhrases.contains(phrase.toLowerCase())) {
											matchedPhrases.add(phrase.toLowerCase());
											matches.add(new WordMatch(filter, phrase));
										}
										match = true;
									}

									// Increase
									i += word.length() + 1;
								}

								// Check
								if (!match)
									break;
							}
						}
					}
				}
			}
		}

		return new FilterResult(matches.toArray(t -> new WordMatch[t]), text);
	}

	@Override
	public void reload() {
		loadData();
	}

}

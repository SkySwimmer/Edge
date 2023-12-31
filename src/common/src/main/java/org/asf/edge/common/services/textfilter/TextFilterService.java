package org.asf.edge.common.services.textfilter;

import org.asf.edge.common.services.AbstractService;
import org.asf.edge.common.services.ServiceImplementationPriorityLevels;
import org.asf.edge.common.services.ServiceManager;
import org.asf.edge.common.services.textfilter.impl.TextFilterServiceImpl;
import org.asf.edge.common.services.textfilter.result.FilterResult;

/**
 * 
 * Text filtering service
 * 
 * @author Sky Swimmer
 *
 */
public abstract class TextFilterService extends AbstractService {

	static {
		ServiceManager.registerServiceImplementation(TextFilterService.class, new TextFilterServiceImpl(),
				ServiceImplementationPriorityLevels.DEFAULT);
	}

	/**
	 * Retrieves the text filter service instance
	 * 
	 * @return TextFilterService instance
	 */
	public static TextFilterService getInstance() {
		return ServiceManager.getService(TextFilterService.class);
	}

	/**
	 * Checks if a string is filtered
	 * 
	 * @param text       String to check
	 * @param strictMode True for strict-mode filtering, false otherwise
	 * @return True if filtered, false otherwise
	 */
	public abstract boolean isFiltered(String text, boolean strictMode);

	/**
	 * Checks if a string results in a mute
	 * 
	 * @param text String to check
	 * @return True if severely filtered, false otherwise
	 */
	public abstract boolean shouldFilterMute(String text);

	/**
	 * Filters strings
	 * 
	 * @param text       String to filter
	 * @param strictMode True for strict-mode filtering, false otherwise
	 * @return Result string
	 */
	public String filterString(String text, boolean strictMode) {
		return filter(text, strictMode).getFilterResult();
	}

	/**
	 * Filters strings
	 * 
	 * @param text       String to filter
	 * @param strictMode True for strict-mode filtering, false otherwise
	 * @return FilterResult value
	 */
	public abstract FilterResult filter(String text, boolean strictMode);

	/**
	 * Retrieves all filter sets
	 * 
	 * @return Array of PhraseFilterSet instances
	 */
	public abstract PhraseFilterSet[] getFilterSets();

	/**
	 * Retrieves filter sets by name
	 * 
	 * @param name Filter set name
	 * @return PhraseFilterSet instance or null
	 */
	public abstract PhraseFilterSet getFilterSet(String name);

	/**
	 * Adds filter sets
	 * 
	 * @param set Filter set to add
	 */
	public abstract void addFilterSet(PhraseFilterSet set);

	/**
	 * Called to reload the text filter service
	 */
	public abstract void reload();

}

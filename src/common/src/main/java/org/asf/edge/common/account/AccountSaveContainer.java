package org.asf.edge.common.account;

/**
 * 
 * Account Save Container
 * 
 * @author Sky Swimmer
 *
 */
public abstract class AccountSaveContainer {

	/**
	 * Retrieves the save username
	 * 
	 * @return Container username string
	 */
	public abstract String getUsername();

	/**
	 * Changes the save username
	 * 
	 * @param name New save username
	 * @return True if successful, false otherwise
	 */
	public abstract boolean updateUsername(String name);

	/**
	 * Retrieves the save ID
	 * 
	 * @return Container ID string
	 */
	public abstract String getSaveID();

	/**
	 * Retrieves the save creation timestamp
	 * 
	 * @return Save creation timestamp
	 */
	public abstract long getCreationTime();

	/**
	 * Retrieves the data container for this save
	 * 
	 * @return AccountDataContainer instance
	 */
	public abstract AccountDataContainer getSaveData();

	/**
	 * Deletes this save
	 */
	public abstract void deleteSave();

}
package org.asf.edge.common.entities.items;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * 
 * Player inventory container
 * 
 * @author Sky Swimmer
 *
 */
public abstract class PlayerInventoryContainer {

	/**
	 * Retrieves the container ID
	 * 
	 * @return Container ID
	 */
	public abstract int getContainerId();

	/**
	 * Retrieves all item IDs in the container
	 * 
	 * @return Array of unique
	 */
	public abstract int[] getItemUniqueIDs();

	/**
	 * Retrieves items by ID
	 * 
	 * @param uniqueID Item unique ID
	 * @return PlayerInventoryItem or null
	 */
	public abstract PlayerInventoryItem getItem(int uniqueID);

	/**
	 * Creates a item
	 * 
	 * @param defID Item DefID
	 * @return PlayerInventoryItem instance
	 */
	public PlayerInventoryItem createItem(int defID) {
		return createItem(defID, 1);
	}

	/**
	 * Creates a item
	 * 
	 * @param defID    Item DefID
	 * @param quantity Item quantity
	 * @return PlayerInventoryItem instance
	 */
	public PlayerInventoryItem createItem(int defID, int quantity) {
		return createItem(defID, quantity, -1);
	}

	/**
	 * Creates a item
	 * 
	 * @param defID    Item DefID
	 * @param quantity Item quantity
	 * @param uses     Item uses
	 * @return PlayerInventoryItem instance
	 */
	public abstract PlayerInventoryItem createItem(int defID, int quantity, int uses);

	/**
	 * Finds all items of the given ID
	 * 
	 * @param defID Item DefID
	 * @return Array of PlayerInventoryItem instances
	 */
	public PlayerInventoryItem[] find(int defID) {
		return Stream.of(getItems()).filter(t -> t.getItemDefID() == defID).toArray(t -> new PlayerInventoryItem[t]);
	}

	/**
	 * Finds the first item of the given ID
	 * 
	 * @param defID Item DefID
	 * @return PlayerInventoryItem instance or null
	 */
	public PlayerInventoryItem findFirst(int defID) {
		Optional<PlayerInventoryItem> opt = Stream.of(getItems()).filter(t -> t.getItemDefID() == defID).findFirst();
		if (!opt.isPresent())
			return null;
		return opt.get();
	}

	/**
	 * Retrieves all items
	 * 
	 * @return Array of PlayerInventoryItem instances
	 */
	public PlayerInventoryItem[] getItems() {
		int[] ids = getItemUniqueIDs();
		PlayerInventoryItem[] invs = new PlayerInventoryItem[ids.length];
		for (int i = 0; i < invs.length; i++)
			invs[i] = getItem(ids[i]);
		return invs;
	}

}

package org.asf.edge.common.services.commondata;

import java.io.IOException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/**
 * 
 * Common data container
 * 
 * @author Sky Swimmer
 *
 */
public abstract class CommonDataContainer {
	protected CommonDataContainer parent;
	protected String name = "";
	protected String path = "";

	public class ContainerUnsafe {
		public JsonElement get(String key) throws IOException {
			return CommonDataContainer.this.get(key);
		}

		public void set(String key, JsonElement value) throws IOException {
			CommonDataContainer.this.set(key, value);
		}

		public void create(String key, JsonElement value) throws IOException {
			CommonDataContainer.this.create(key, value);
		}

		public boolean exists(String key) throws IOException {
			return CommonDataContainer.this.exists(key);
		}

		public void delete(String key) throws IOException {
			CommonDataContainer.this.delete(key);
		}
	}

	/**
	 * Utility to directly access the data container functions that are protected,
	 * not recommended except for remote data containers
	 */
	public ContainerUnsafe unsafeAccessor() {
		return new ContainerUnsafe();
	}

	/**
	 * Retrieves the parent data container
	 * 
	 * @return CommonDataContainer instance or null
	 */
	public CommonDataContainer getParent() {
		return parent;
	}

	/**
	 * Called to retrieve elements
	 * 
	 * @param key Element key
	 * @return JsonElement instance or null
	 * @throws IOException If the entry cannot be retrieved
	 */
	protected abstract JsonElement get(String key) throws IOException;

	/**
	 * Called to assign elements
	 * 
	 * @param key   Element key
	 * @param value Value to assign
	 * @throws IOException If the entry cannot be assigned
	 */
	protected abstract void set(String key, JsonElement value) throws IOException;

	/**
	 * Called to create elements
	 * 
	 * @param key   Element key
	 * @param value Value to assign
	 * @throws IOException If the entry cannot be assigned
	 */
	protected abstract void create(String key, JsonElement value) throws IOException;

	/**
	 * Called to check if keys exist
	 * 
	 * @param key Key to check
	 * @return True if present, false otherwise
	 * @throws IOException If the entry cannot be verified
	 */
	protected abstract boolean exists(String key) throws IOException;

	/**
	 * Called to delete elements
	 * 
	 * @param key Element key
	 * @throws IOException If the deletion errors
	 */
	protected abstract void delete(String key) throws IOException;

	private boolean validName(String key) {
		// Check if internal
		if (key.equalsIgnoreCase("datamap") || key.contains("/"))
			return false;

		// Check validity
		if (key.replace(" ", "").trim().isEmpty())
			return false;

		// Check length
		if (key.length() > 64)
			return false;

		// Valid
		return true;
	}

	/**
	 * Retrieves data entries
	 * 
	 * @param key Entry key to retrieve
	 * @return JsonElement instance or null
	 * @throws IOException
	 */
	public JsonElement getEntry(String key) throws IOException {
		if (!validName(key))
			return null;
		return get(key);
	}

	/**
	 * Verifies if data entries exist
	 * 
	 * @param key Key to check
	 * @return True if present, false otherwise
	 * @throws IOException If the entry cannot be verified
	 */
	public boolean entryExists(String key) throws IOException {
		if (!validName(key))
			return false;
		return exists(key);
	}

	/**
	 * Assigns data entries
	 * 
	 * @param key   Element key
	 * @param value Value to assign
	 * @throws IOException If the entry cannot be assigned
	 */
	public void setEntry(String key, JsonElement value) throws IOException {
		if (!validName(key))
			throw new IOException("Invalid key");

		// Check if it previously existed
		boolean existed = exists(key);

		// Set
		if (existed)
			set(key, value);
		else
			create(key, value);

		// Add to registry table if new
		if (!existed) {
			JsonArray table = retrieveRegistry();
			table.add(key);
			set("datamap", table);
		}
	}

	/**
	 * Deletes data entries
	 * 
	 * @param key Element key
	 * @throws IOException If the entry cannot be assigned
	 */
	public void deleteEntry(String key) throws IOException {
		if (!validName(key))
			throw new IOException("Invalid key");

		// Check if it exists
		if (exists(key)) {
			// Delete
			delete(key);

			// Remove from registry table
			JsonArray table = retrieveRegistry();
			for (JsonElement ele : table)
				if (ele.getAsString().equals(key)) {
					table.remove(ele);
					break;
				}
			set("datamap", table);
		}
	}

	/**
	 * Retrieves child data containers
	 * 
	 * @param key Container key
	 * @return CommonDataContainer instance
	 * @throws IOException If loading the container fails
	 */
	public CommonDataContainer getChildContainer(String key) throws IOException {
		// Verify name
		if (!validName(key))
			throw new IOException("Invalid key");

		// Verify path length limit
		if ((path + key + "/").length() + 64 > 256)
			throw new IOException("Invalid key: key name too long, path length limit would be hit");

		// Return container
		return new ChildDataContainer(path + key + "/", key, this);
	}

	/**
	 * Deletes the data container
	 * 
	 * @throws IOException If deletion fails
	 */
	public void deleteContainer() throws IOException {
		// If there is a parent container, if there is we need to remove this container
		// from it, else there will be a ghost entry
		CommonDataContainer parent = getParent();
		if (parent != null) {
			JsonArray reg = parent.retrieveRegistry();
			for (JsonElement elem : reg) {
				if (elem.getAsString().equals(name + "/")) {
					reg.remove(elem);
					parent.set("datamap", reg);
					break;
				}
			}
		}

		// Find map
		JsonElement ele = get("datamap");
		if (ele == null) {
			// No data map, nothing to delete in this container
			return;
		}

		// Load data array
		JsonArray dataMap = ele.getAsJsonArray();

		// Delete entries
		for (JsonElement elem : dataMap) {
			if (elem.getAsString().endsWith("/")) {
				// Child container
				getChildContainer(elem.getAsString().substring(0, elem.getAsString().length() - 1)).deleteContainer();
				continue;
			}
			delete(elem.getAsString());
		}

		// Delete data map
		delete("datamap");
	}

	protected JsonArray retrieveRegistry() throws IOException {
		JsonElement ele = get("datamap");
		if (ele == null) {
			ele = new JsonArray();
			create("datamap", ele);
		}
		return ele.getAsJsonArray();
	}

	private class ChildDataContainer extends CommonDataContainer {

		private boolean registryChecked;

		public ChildDataContainer(String path, String name, CommonDataContainer parent) {
			this.path = path;
			this.parent = parent;
			this.name = name;
		}

		@Override
		protected JsonElement get(String key) throws IOException {
			return parent.get(name + "/" + key);
		}

		@Override
		protected boolean exists(String key) throws IOException {
			return parent.exists(name + "/" + key);
		}

		@Override
		protected void set(String key, JsonElement value) throws IOException {
			parent.set(name + "/" + key, value);
		}

		@Override
		protected void create(String key, JsonElement value) throws IOException {
			parent.create(name + "/" + key, value);
		}

		@Override
		protected void delete(String key) throws IOException {
			parent.delete(name + "/" + key);
		}

		@Override
		protected JsonArray retrieveRegistry() throws IOException {
			// Check if it exists in the parent
			if (!registryChecked) {
				registryChecked = true;

				// Verify existence, and if needed, create the container
				JsonArray table = parent.retrieveRegistry();
				boolean found = false;
				for (JsonElement ele : table) {
					if (ele.getAsString().equals(name + "/")) {
						found = true;
						break;
					}
				}
				if (!found) {
					// Add to table
					table.add(name + "/");
					set("datamap", table);
				}
			}
			return super.retrieveRegistry();
		}

	}

}

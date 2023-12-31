package org.asf.edge.gameplayapi.xmls.inventories;

import org.asf.edge.common.xmls.items.inventory.InventoryItemEntryData;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class CommonInventoryData {

	@JsonProperty("uid")
	public String userID;

	@JsonProperty("i")
	@JacksonXmlElementWrapper(useWrapping = false)
	public InventoryItemEntryData[] items;

}

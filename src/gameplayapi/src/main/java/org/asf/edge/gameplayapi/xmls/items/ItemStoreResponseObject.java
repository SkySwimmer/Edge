package org.asf.edge.gameplayapi.xmls.items;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class ItemStoreResponseObject {

	@JsonProperty("i")
	public int storeID;

	@JsonProperty("s")
	public String storeName;

	@JsonProperty("d")
	public String storeDescription;

	@JsonProperty("is")
	@JacksonXmlElementWrapper(useWrapping = false)
	@JsonInclude(Include.NON_DEFAULT)
	public ObjectNode[] items;

	@JsonProperty("pitem")
	@JacksonXmlElementWrapper(useWrapping = false)
	@JsonInclude(Include.NON_DEFAULT)
	public PopularItemBlock[] popularItems;

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
	public static class PopularItemBlock {

		@JsonProperty("id")
		public int itemID;

		@JsonProperty("r")
		public int rank;

	}

}
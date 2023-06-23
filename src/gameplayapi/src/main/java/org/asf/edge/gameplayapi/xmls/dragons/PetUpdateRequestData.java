package org.asf.edge.gameplayapi.xmls.dragons;

import org.asf.edge.gameplayapi.xmls.inventories.SetCommonInventoryRequestData;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class PetUpdateRequestData {

	@JsonProperty("rpd")
	public DragonData dragonData;

	@JsonProperty("ptid")
	public int typeID;

	@JsonProperty("SASP")
	public boolean setAsSelected;

	@JsonProperty("cid")
	public int containerID = -1;

	@JsonProperty("cir")
	@JacksonXmlElementWrapper(useWrapping = false)
	public SetCommonInventoryRequestData[] commonInventoryRequests;

}
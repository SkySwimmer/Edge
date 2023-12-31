package org.asf.edge.gameplayapi.xmls.achievements;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class StableQuestRewardBlock {

	@JsonProperty("ii")
	public int itemID;

	@JsonProperty("a")
	public int amount;

	// 1, 9, 10, 12: achievement points, 2: coins, 5: gems, 6: item, 8: dragon XP
	@JsonProperty("p")
	@JsonInclude(Include.NON_DEFAULT)
	public int pointTypeID = -1;

	@JsonProperty("t")
	public int entityTypeID;

	@JsonProperty("r")
	public int rewardID;

}

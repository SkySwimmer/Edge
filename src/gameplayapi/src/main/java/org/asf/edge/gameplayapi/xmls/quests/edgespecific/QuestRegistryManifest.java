package org.asf.edge.gameplayapi.xmls.quests.edgespecific;

import org.asf.edge.gameplayapi.xmls.quests.MissionData;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class QuestRegistryManifest {

	public String questDataVersion;
	public QuestDefsBlock defaultQuestDefs;

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
	public static class QuestDefsBlock {

		@JsonProperty("QuestDef")
		@JacksonXmlElementWrapper(useWrapping = false)
		public MissionData[] questDefs;

	}
}

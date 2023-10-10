package org.asf.edge.common.xmls.achievements.edgespecific;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class AchievementRewardTransformerList {

	@JsonInclude(Include.NON_NULL)
	@JsonProperty("Transformer")
	@JacksonXmlElementWrapper(useWrapping = false)
	public AchievementRewardTransformerData[] transformers = new AchievementRewardTransformerData[0];

}

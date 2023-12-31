package org.asf.edge.common.xmls.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class KeyValuePairSetData {

	@JacksonXmlProperty(localName = "xmlns", isAttribute = true)
	public String xmlns = "";

	@JsonProperty("Pair")
	@JsonInclude(Include.NON_NULL)
	@JacksonXmlElementWrapper(useWrapping = false)
	public KeyValuePairData[] items = new KeyValuePairData[0];

}

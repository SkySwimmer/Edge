package org.asf.edge.common.entities.tables.items;

import org.asf.nexus.tables.TableRow;
import org.asf.nexus.tables.annotations.ForceUseFilterFields;
import org.asf.nexus.tables.annotations.TableColumn;
import org.asf.nexus.tables.annotations.UseAsFilter;

@ForceUseFilterFields
public class PopularItemRow extends TableRow {

	@TableColumn
	@UseAsFilter
	public int itemID;

	@TableColumn
	public int popularity;

}

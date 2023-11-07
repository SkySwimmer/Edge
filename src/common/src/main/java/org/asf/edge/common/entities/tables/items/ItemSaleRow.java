package org.asf.edge.common.entities.tables.items;

import java.util.Date;

import org.asf.edge.common.services.tabledata.TableRow;
import org.asf.edge.common.services.tabledata.annotations.ForceUseFilterFields;
import org.asf.edge.common.services.tabledata.annotations.TableColumn;
import org.asf.edge.common.services.tabledata.annotations.UseAsFilter;

@ForceUseFilterFields
public class ItemSaleRow extends TableRow {

	@TableColumn
	@UseAsFilter
	public int categoryID;

	@TableColumn
	public Date startTime;

	@TableColumn
	public Date endTime;

	@TableColumn
	public float modifier;

	@TableColumn
	public int[] itemIDs;

	@TableColumn
	public boolean memberOnly;

}

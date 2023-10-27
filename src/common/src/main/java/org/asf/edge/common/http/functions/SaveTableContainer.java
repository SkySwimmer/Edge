package org.asf.edge.common.http.functions;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.asf.edge.common.services.tabledata.TableRow;

/**
 *
 * Assigns the specified save data table container to the parameter value
 * 
 * @author Sky Swimmer
 *
 */
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface SaveTableContainer {

	/**
	 * Data table container name
	 */
	public String name();

	/**
	 * Row type
	 */
	public Class<? extends TableRow> rowType();

}

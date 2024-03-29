/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.test.internal.performance.results;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.eclipse.test.internal.performance.InternalDimensions;
import org.eclipse.test.internal.performance.db.SQL;

/**
 * Specific implementation for massive database requests.
 */
public class SQL_Results extends SQL {

	private PreparedStatement queryBuildScenarios, queryScenarioFailures, queryScenarioSummaries, queryAllComments, queryScenariosBuilds, queryScenarioDataPoints, queryScenarioTimestampDataPoints, queryDimScalars, queryAllVariations;


SQL_Results(Connection con) throws SQLException {
	    super(con);
	    // TODO Auto-generated constructor stub
    }

protected void dispose() throws SQLException {
	super.dispose();
	if (this.queryBuildScenarios != null)
		this.queryBuildScenarios.close();
	if (this.queryScenarioFailures != null)
		this.queryScenarioFailures.close();
	if (this.queryScenarioSummaries != null)
		this.queryScenarioSummaries.close();
	if (this.queryAllComments != null)
		this.queryAllComments.close();
	if (this.queryScenariosBuilds != null)
		this.queryScenariosBuilds.close();
	if (this.queryScenarioDataPoints != null)
		this.queryScenarioDataPoints.close();
	if (this.queryDimScalars != null)
		this.queryDimScalars.close();
	if (this.queryAllVariations != null)
		this.queryAllVariations.close();
}

/**
 * Get all comments from database
 *
 * @return A set of the query result
 * @throws SQLException
 */
ResultSet queryAllComments() throws SQLException {
	if (this.queryAllComments == null)
		this.queryAllComments = fConnection.prepareStatement("select ID, KIND, TEXT from COMMENT"); //$NON-NLS-1$
	return this.queryAllComments.executeQuery();
}

/**
 * Get all variations from database.
 *
 * @param configPattern The pattern for all the concerned configurations
 * @return A set of the query result
 * @throws SQLException
 */
ResultSet queryAllVariations(String configPattern) throws SQLException {
	long start = System.currentTimeMillis();
	if (DB_Results.DEBUG) DB_Results.DEBUG_WRITER.print("[SQL query (config pattern="+configPattern); //$NON-NLS-1$
	if (this.queryAllVariations == null) {
		this.queryAllVariations = fConnection.prepareStatement("select KEYVALPAIRS from VARIATION where KEYVALPAIRS like ? order by KEYVALPAIRS"); //$NON-NLS-1$
	}
	this.queryAllVariations.setString(1, "%"+configPattern+"%"); //$NON-NLS-1$ //$NON-NLS-2$
	ResultSet resultSet =  this.queryAllVariations.executeQuery();
	if (DB_Results.DEBUG) DB_Results.DEBUG_WRITER.print(")=" + (System.currentTimeMillis() - start) + "ms]"); //$NON-NLS-1$ //$NON-NLS-2$
	return resultSet;
}

/**
 * Query all scenarios corresponding to a given scenario pattern
 * and for a specific build name.
 *
 * @param scenarioPattern The pattern for all the concerned scenarios
 * @param buildName The name of the concerned build
 * @return Set of the query result
 * @throws SQLException
 */
ResultSet queryBuildScenarios(String scenarioPattern, String buildName) throws SQLException {
	if (this.queryBuildScenarios == null) {
		String statement = "select distinct SCENARIO.ID, SCENARIO.NAME , SCENARIO.SHORT_NAME from SCENARIO, SAMPLE, VARIATION where " + //$NON-NLS-1$
			"SAMPLE.VARIATION_ID = VARIATION.ID and VARIATION.KEYVALPAIRS LIKE ? and " + //$NON-NLS-1$
			"SAMPLE.SCENARIO_ID = SCENARIO.ID and SCENARIO.NAME LIKE ? " + //$NON-NLS-1$
			"order by SCENARIO.NAME"; //$NON-NLS-1$
		this.queryBuildScenarios = fConnection.prepareStatement(statement);
	}
	this.queryBuildScenarios.setString(1, "|build=" + buildName + '%'); //$NON-NLS-1$
	this.queryBuildScenarios.setString(2, scenarioPattern);
	return this.queryBuildScenarios.executeQuery();
}

/**
 * Query all scalars for a given data point.
 * 
 * @param datapointId The id of the data point
 * @return Set of the query result
 * @throws SQLException
 */
ResultSet queryDimScalars(int datapointId) throws SQLException {
	if (this.queryDimScalars == null) {
		this.queryDimScalars = fConnection.prepareStatement("select DIM_ID, VALUE from SCALAR where " + //$NON-NLS-1$
			"DATAPOINT_ID = ? and " + //$NON-NLS-1$
			"(DIM_ID = "+InternalDimensions.CPU_TIME.getId()+" or DIM_ID = "+InternalDimensions.ELAPSED_PROCESS.getId()+") " +   //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"order by DIM_ID");  //$NON-NLS-1$
	}
	this.queryDimScalars.setInt(1, datapointId);
	return this.queryDimScalars.executeQuery();
}

/**
 * Get all data points for a given scenario and configuration.
 *
 * @param config The name of the concerned configuration
 * @param scenarioID The id of the scenario
 * @param lastBuildName Name of the last build on which data were stored locally
 * @param lastBuildTime Date in ms of the last build on which data were stored locally
 * @return A set of the query result
 * @throws SQLException
 */
ResultSet queryScenarioTimestampDataPoints(String config, int scenarioID, String lastBuildName, long lastBuildTime) throws SQLException {
	if (DB_Results.LOG) DB_Results.LOG_WRITER.starts("		+ SQL query (config="+config+", scenario ID="+scenarioID+", build name="+lastBuildName); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	if (this.queryScenarioTimestampDataPoints== null) {
		String statement = "select DATAPOINT.ID, DATAPOINT.STEP, VARIATION.KEYVALPAIRS from SAMPLE, DATAPOINT, VARIATION where " + //$NON-NLS-1$
			"SAMPLE.SCENARIO_ID = ? and " + //$NON-NLS-1$
			"DATAPOINT.SAMPLE_ID = SAMPLE.ID and " + //$NON-NLS-1$
			"SAMPLE.STARTTIME > ? and " + //$NON-NLS-1$
			"SAMPLE.VARIATION_ID = VARIATION.ID " + //$NON-NLS-1$
			"ORDER BY DATAPOINT.ID, DATAPOINT.STEP"; //$NON-NLS-1$
		this.queryScenarioTimestampDataPoints = fConnection.prepareStatement(statement);
	}
	this.queryScenarioTimestampDataPoints.setInt(1, scenarioID);
	Timestamp timestamp = new Timestamp(lastBuildTime+(12*3600L*1000));
	this.queryScenarioTimestampDataPoints.setTimestamp(2, timestamp);
	ResultSet resultSet =  this.queryScenarioTimestampDataPoints.executeQuery();
	if (DB_Results.LOG) DB_Results.LOG_WRITER.ends(")"); //$NON-NLS-1$
	return resultSet;
}

/**
 * Get all data points for a given scenario and configuration.
 *
 * @param config The name of the concerned configuration
 * @param scenarioID The id of the scenario
 * @return A set of the query result
 * @throws SQLException
 */
ResultSet queryScenarioDataPoints(String config, int scenarioID) throws SQLException {
	long start = System.currentTimeMillis();
	if (DB_Results.DEBUG) DB_Results.DEBUG_WRITER.print("[SQL query (config="+config+", scenario ID="+scenarioID); //$NON-NLS-1$ //$NON-NLS-2$
	if (this.queryScenarioDataPoints== null) {
		String statement = "select DATAPOINT.ID, DATAPOINT.STEP, VARIATION.KEYVALPAIRS from VARIATION, SAMPLE, DATAPOINT where " + //$NON-NLS-1$
			"VARIATION.KEYVALPAIRS like ? and SAMPLE.VARIATION_ID = VARIATION.ID and " + //$NON-NLS-1$
			"SAMPLE.SCENARIO_ID = ? and " + //$NON-NLS-1$
			"DATAPOINT.SAMPLE_ID = SAMPLE.ID " + //$NON-NLS-1$
			"ORDER BY DATAPOINT.ID, DATAPOINT.STEP"; //$NON-NLS-1$
		this.queryScenarioDataPoints = fConnection.prepareStatement(statement);
	}
	this.queryScenarioDataPoints.setString(1, "%"+config+"%"); //$NON-NLS-1$ //$NON-NLS-2$
	this.queryScenarioDataPoints.setInt(2, scenarioID);
	ResultSet resultSet =  this.queryScenarioDataPoints.executeQuery();
	if (DB_Results.DEBUG) DB_Results.DEBUG_WRITER.print(")=" + (System.currentTimeMillis() - start) + "ms]"); //$NON-NLS-1$ //$NON-NLS-2$
	return resultSet;
}

/**
 * Query all failures from database for a given scenario,
 * configuration and builds.
 *
 * @param config The name of the concerned configuration
 * @param scenarioID The id of the scenario
 * @param currentBuildName The name of the current build
 * @param baselineBuildName The name of the baseline build
 * @return A set of the query result
 * @throws SQLException
 */
ResultSet queryScenarioFailures(int scenarioID, String config, String currentBuildName, String baselineBuildName) throws SQLException {
	if (this.queryScenarioFailures == null) {
		this.queryScenarioFailures = fConnection.prepareStatement("select KEYVALPAIRS, MESSAGE from VARIATION, FAILURE where " + //$NON-NLS-1$
			"(KEYVALPAIRS like ? or KEYVALPAIRS like ?) and " + //$NON-NLS-1$
			"VARIATION_ID = VARIATION.ID and " + //$NON-NLS-1$
			"SCENARIO_ID = ? " + //$NON-NLS-1$
			"ORDER BY VARIATION_ID"); //$NON-NLS-1$
	}
	this.queryScenarioFailures.setString(1, "|build=" + currentBuildName+ "||config="+ config + "||jvm=sun|"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	this.queryScenarioFailures.setString(2, "|build=" + baselineBuildName+ "||config="+ config + "||jvm=sun|"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	this.queryScenarioFailures.setInt(3, scenarioID);
	return this.queryScenarioFailures.executeQuery();
}

/**
 * Query all summaries from database for a given scenario,
 * configuration and builds.
 *
 * @param config The name of the concerned configuration
 * @param scenarioID The id of the scenario
 * @param currentBuildName The name of the current build
 * @param baselineBuildName The name of the baseline build
 * @param dim_id The dim id
 * @return Set of the query result
 * @throws SQLException
 */
ResultSet queryScenarioSummaries(int scenarioID, String config, String currentBuildName, String baselineBuildName, int dim_id) throws SQLException {
	if (this.queryScenarioSummaries == null) {
		this.queryScenarioSummaries= fConnection.prepareStatement("select KEYVALPAIRS, IS_GLOBAL, COMMENT_ID from VARIATION, SUMMARYENTRY where " + //$NON-NLS-1$
			"(KEYVALPAIRS like ? or KEYVALPAIRS like ?) and " + //$NON-NLS-1$
			"VARIATION_ID = VARIATION.ID and " + //$NON-NLS-1$
			"SCENARIO_ID = ? and " + //$NON-NLS-1$
			"DIM_ID = ? " + //$NON-NLS-1$
			" order by VARIATION_ID"); //$NON-NLS-1$
	}
	this.queryScenarioSummaries.setString(1, "|build=" + currentBuildName+ "||config="+ config + "||jvm=sun|"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	this.queryScenarioSummaries.setString(2, "|build=" + baselineBuildName+ "||config="+ config + "||jvm=sun|"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	this.queryScenarioSummaries.setInt(3, scenarioID);
	this.queryScenarioSummaries.setInt(4, dim_id);
	return this.queryScenarioSummaries.executeQuery();
}

}

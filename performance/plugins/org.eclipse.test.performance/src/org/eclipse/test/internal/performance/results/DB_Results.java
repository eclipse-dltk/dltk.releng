/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
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

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.eclipse.test.internal.performance.InternalDimensions;
import org.eclipse.test.internal.performance.PerformanceTestPlugin;
import org.eclipse.test.internal.performance.db.DB;

/**
 * Specific and private implementation of {@link org.eclipse.test.internal.performance.db.DB} class
 * to get massive results from performance results database.
 * TODO (frederic) Should be at least a subclass of {@link DB}...
 */
public class DB_Results {
    
    static final boolean DEBUG = false;
    static final boolean LOG = false;
    
    // the two supported DB types
    private static final String DERBY= "derby"; //$NON-NLS-1$
    private static final String CLOUDSCAPE= "cloudscape"; //$NON-NLS-1$
        
    private static DB_Results fgDefault;
    
    private Connection fConnection;
    private SQL_Results fSQL;
    private boolean fIsEmbedded;
    private String fDBType;	// either "derby" or "cloudscape"

    // Store debug info
    final static StringWriter DEBUG_STR_WRITER = DEBUG ? new StringWriter() : null;
    final static PrintWriter DEBUG_WRITER = DEBUG ? new PrintWriter(DEBUG_STR_WRITER) : null;
    
    // Store log info
    final static StringWriter LOG_STR_WRITER = new StringWriter();
    final static LogWriter LOG_WRITER = new LogWriter();
    static class LogWriter extends PrintWriter {
		long[] starts = new long[10];
		long[] times = new long[10];
    	StringBuffer[] buffers = new StringBuffer[10];
    	int depth = -1, max = -1;
    	public LogWriter() {
	        super(LOG_STR_WRITER);
        }
		void starts(String log) {
    		if (++this.depth >= buffers.length) {
    			System.arraycopy(this.times, 0, this.times = new long[this.depth+10], 0, this.depth);
    			System.arraycopy(this.buffers, 0, this.buffers= new StringBuffer[this.depth+10], 0, this.depth);
    		}
    		StringBuffer buffer = this.buffers[this.depth];
    		if (this.buffers[this.depth] == null) buffer = this.buffers[this.depth] = new StringBuffer();
    		buffer.append(log);
    		this.starts[this.depth] = System.currentTimeMillis();
    		if (this.depth > this.max) this.max = this.depth;
    	}
		void ends(String log) {
			if (this.depth < 0)
				throw new RuntimeException("Invalid call to ends (missing corresponding starts call)!"); //$NON-NLS-1$
    		this.buffers[this.depth].append(log);
    		if (this.depth > 0) {
    			this.times[this.depth] += System.currentTimeMillis() - this.starts[this.depth];
    			this.depth--;
    			return;	
    		}
    		for (int i=0; i<this.max; i++) {
	    		print(this.buffers[i].toString());
	    		print(" ( in "); //$NON-NLS-1$
	    		print(this.times[this.depth]);
    			println("ms)"); //$NON-NLS-1$
    		}
    		this.depth = this.max = -1;
			this.starts = new long[10];
			this.times = new long[10];
    		this.buffers = new StringBuffer[10];
    	}
		public String toString() {
	        return LOG_STR_WRITER.toString();
        }
    }
    
	// Data storage from queries
	private static String[] CONFIGS, COMPONENTS, BUILDS;
	static String LAST_CURRENT_BUILD, LAST_BASELINE_BUILD;
	private static int BUILDS_LENGTH;
	private static String[] SCENARII;
	private static String[] COMMENTS;

	// Static data
	private final static int MAX_CONFIGS = 5;
	private final static String[] SUPPORTED_VMS =  { // Consider only supported VMs a static data
		"sun" //$NON-NLS-1$
	};
	private final static String[] SUPPORTED_COMPONENTS = {
		"org.eclipse.ant", //$NON-NLS-1$
		"org.eclipse.compare", //$NON-NLS-1$
		"org.eclipse.core", //$NON-NLS-1$
		"org.eclipse.dltk", //$NON-NLS-1$
		"org.eclipse.help", //$NON-NLS-1$
		"org.eclipse.jdt.core", //$NON-NLS-1$
		"org.eclipse.jdt.debug", //$NON-NLS-1$
		"org.eclipse.jdt.text", //$NON-NLS-1$
		"org.eclipse.jdt.ui", //$NON-NLS-1$
		"org.eclipse.jface", //$NON-NLS-1$
		"org.eclipse.osgi", //$NON-NLS-1$
		"org.eclipse.pde.ui", //$NON-NLS-1$
		"org.eclipse.swt", //$NON-NLS-1$
		"org.eclipse.team", //$NON-NLS-1$
		"org.eclipse.ua", //$NON-NLS-1$
		"org.eclipse.ui" //$NON-NLS-1$
	};
    
    //---- private implementation
    
	/**
     * Private constructor to block instance creation.
     */
    private DB_Results() {
    	// empty implementation
    }

    synchronized static DB_Results getDefault() {
        if (fgDefault == null) {
            fgDefault= new DB_Results();
            fgDefault.connect();
            if (PerformanceTestPlugin.getDefault() == null) {
            	// not started as plugin
	            Runtime.getRuntime().addShutdownHook(
	                new Thread() {
	                    public void run() {
	                    	shutdown();
	                    }
	                }
	            );
            }
        }
        return fgDefault;
    }
    
    public static void shutdown() {
        if (fgDefault != null) {
            fgDefault.disconnect();
            fgDefault= null;
        }
        if (DEBUG) {
        	DEBUG_WRITER.println("DB.shutdown"); //$NON-NLS-1$
        	System.out.println(DEBUG_STR_WRITER.toString());
        }
        if (LOG) {
        	System.out.println(LOG_STR_WRITER.toString());
        }
    }

/**
 * Return the build id from a given name.
 * 
 * @param name The build name (eg. I20070615-1200)
 * @return The id of the build (ie. the index in the {@link #BUILDS} list)
 */
static int getBuildId(String name) {
	if (BUILDS == null) return -1;
	return Arrays.binarySearch(BUILDS, name);
}

/**
 * Return the build name from a given id.
 * 
 * @param id The build id
 * @return The name of the build (eg. I20070615-1200)
 */
static String getBuildName(int id) {
	if (BUILDS == null) return null;
	return BUILDS[id];
}

/**
 * Returns all the builds names read from the database.
 * 
 * @return The list of all builds names matching the scenario pattern used while reading data
 */
public static List getBuilds() {
	if (BUILDS == null) {
		queryAllVariations("%"); //$NON-NLS-1$
	}
	return Arrays.asList(BUILDS);
}

/**
 * Get component name from a scenario.
 * 
 * @param scenarioName The name of the scenario
 * @return The component name
 */
static String getComponentNameFromScenario(String scenarioName) {
	int length = SUPPORTED_COMPONENTS.length;
	for (int i=0; i<length; i++) {
		if (scenarioName.startsWith(SUPPORTED_COMPONENTS[i])) {
			return SUPPORTED_COMPONENTS[i];
		}
	}
	return null;
}

/**
 * Get all components read from database.
 *
 * @return A list of component names matching the given pattern
 */
public static List getComponents() {
	return Arrays.asList(COMPONENTS);
}

/**
 * Return the name of the configuration from the given id.
 * 
 * @param id The index of the configuration in the stored list.
 * @return The name of the configuration (eg. eclipseperflnx1_R3.3)
 */
static String getConfig(int id) {
	return CONFIGS[id];
}

/**
 * Returns all the scenarios names read from the database.
 * 
 * @return The list of all scenarios matching the pattern for a given build.
 * @see #internalQueryBuildScenarios(String, String)
 */
public static List getScenarios() {
	return Arrays.asList(SCENARII);
}

/**
 * Get all scenarios read from database matching a given pattern.
 * Note that all scenarios are returned if the pattern is <code>null</code>.
 *
 * @param scenarioPattern The pattern of the requested scenarios
 * @param buildName The build name
 * @return A list of scenario names matching the given pattern
 */
static Map queryAllScenarios(String scenarioPattern, String buildName) {
	return getDefault().internalQueryBuildScenarios(scenarioPattern, buildName);
}

/**
 * Get all variations read from database matching a given configuration pattern.
 *
 * @param configPattern The pattern of the requested configurations
 */
static void queryAllVariations(String configPattern) {
	getDefault().internalQueryAllVariations(configPattern);
}

/**
 * Get all the failures from DB for a given scenario, configuration
 * pattern and builds.
 * 
 * @param scenarioResults The scenario results where to store data
 * @param configPattern The configuration pattern concerned by the query
 * @param currentBuild The current build to narrow the query
 * @param baselineBuild The baseline build to narrow the query 
 */
static void queryScenarioFailures(ScenarioResults scenarioResults, String configPattern, BuildResults currentBuild, BuildResults baselineBuild) {
	getDefault().internalQueryScenarioFailures(scenarioResults, configPattern, currentBuild, baselineBuild);
}

/**
 * Get all summaries from DB for a given scenario, configuration
 * pattern and builds.
 *
 * @param scenarioResults The scenario results where to store data
 * @param configPattern The configuration pattern concerned by the query
 * @param currentBuild The current build to narrow the query
 * @param baselineBuild The baseline build to narrow the query 
 */
static void queryScenarioSummaries(ScenarioResults scenarioResults, String configPattern, BuildResults currentBuild, BuildResults baselineBuild) {
	getDefault().internalQueryScenarioSummaries(scenarioResults, configPattern, currentBuild, baselineBuild);
}

/**
 * Query and store all values for given scenario results
 *
 * @param scenarioResults The scenario results where the values has to be put
 * @param configPattern The pattern of the configuration concerned by the query 
 *
*/
static void queryScenarioValues(ScenarioResults scenarioResults, String configPattern) {
	getDefault().internalQueryScenarioValues(scenarioResults, configPattern, null, -1);
}

/**
 * Query and store all values for given scenario results
 *
 * @param scenarioResults The scenario results where the values has to be put
 * @param configPattern The pattern of the configuration concerned by the query 
 * @param lastBuildName Name of the last build on which data were stored locally
 * @param lastBuildDate Date of the last build on which data were stored locally
 *
*/
static void queryScenarioValues(ScenarioResults scenarioResults, String configPattern, String lastBuildName, long lastBuildDate) {
	getDefault().internalQueryScenarioValues(scenarioResults, configPattern, lastBuildName, lastBuildDate);
}

/**
 * dbloc=						embed in home directory
 * dbloc=/tmp/performance			embed given location
 * dbloc=net://localhost			connect to local server
 * dbloc=net://www.eclipse.org	connect to remove server
 */
private void connect() {

	if (fConnection != null)
		return;

	if (DEBUG) DriverManager.setLogWriter(new PrintWriter(System.out));
	String dbloc = PerformanceTestPlugin.getDBLocation();
	if (dbloc == null)
		return;

	String dbname = PerformanceTestPlugin.getDBName();
	String url = null;
	java.util.Properties info = new java.util.Properties();

	if (DEBUG) {
		DEBUG_WRITER.println();
		DEBUG_WRITER.println("==========================================================="); //$NON-NLS-1$
		DEBUG_WRITER.println("Database debug information stored while processing"); //$NON-NLS-1$
	}
	if (LOG) {
		LOG_WRITER.println();
		LOG_WRITER.println("==========================================================="); //$NON-NLS-1$
		LOG_WRITER.println("Database log information stored while processing"); //$NON-NLS-1$
	}

	fDBType = DERBY; // assume we are using Derby
	try {
		if (dbloc.startsWith("net://")) { //$NON-NLS-1$
			// remote
			fIsEmbedded = false;
			// connect over network
			if (DEBUG)
				DEBUG_WRITER.println("Trying to connect over network..."); //$NON-NLS-1$
			Class.forName("com.ibm.db2.jcc.DB2Driver"); //$NON-NLS-1$
			info.put("user", PerformanceTestPlugin.getDBUser()); //$NON-NLS-1$
			info.put("password", PerformanceTestPlugin.getDBPassword()); //$NON-NLS-1$
			info.put("retrieveMessagesFromServerOnGetMessage", "true"); //$NON-NLS-1$ //$NON-NLS-2$
			info.put("create", "true"); //$NON-NLS-1$ //$NON-NLS-2$
			url = dbloc + '/' + dbname;
		} else if (dbloc.startsWith("//")) { //$NON-NLS-1$
			// remote
			fIsEmbedded = false;
			// connect over network
			if (DEBUG)
				DEBUG_WRITER.println("Trying to connect over network..."); //$NON-NLS-1$
			Class.forName("org.apache.derby.jdbc.ClientDriver"); //$NON-NLS-1$
			info.put("user", PerformanceTestPlugin.getDBUser()); //$NON-NLS-1$
			info.put("password", PerformanceTestPlugin.getDBPassword()); //$NON-NLS-1$
			info.put("create", "true"); //$NON-NLS-1$ //$NON-NLS-2$
			url = dbloc + '/' + dbname;
		} else {
			// workaround for Derby issue:
			// http://nagoya.apache.org/jira/browse/DERBY-1
			if ("Mac OS X".equals(System.getProperty("os.name"))) //$NON-NLS-1$//$NON-NLS-2$
				System.setProperty("derby.storage.fileSyncTransactionLog", "true"); //$NON-NLS-1$ //$NON-NLS-2$

			// embedded
			fIsEmbedded = true;
			try {
				Class.forName("org.apache.derby.jdbc.EmbeddedDriver"); //$NON-NLS-1$
			} catch (ClassNotFoundException e) {
				Class.forName("com.ihost.cs.jdbc.CloudscapeDriver"); //$NON-NLS-1$
				fDBType = CLOUDSCAPE;
			}
			if (DEBUG)
				DEBUG_WRITER.println("Loaded embedded " + fDBType); //$NON-NLS-1$
			File f;
			if (dbloc.length() == 0) {
				String user_home = System.getProperty("user.home"); //$NON-NLS-1$
				if (user_home == null)
					return;
				f = new File(user_home, fDBType);
			} else
				f = new File(dbloc);
			url = new File(f, dbname).getAbsolutePath();
			info.put("user", PerformanceTestPlugin.getDBUser()); //$NON-NLS-1$
			info.put("password", PerformanceTestPlugin.getDBPassword()); //$NON-NLS-1$
			info.put("create", "true"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		try {
			fConnection = DriverManager.getConnection("jdbc:" + fDBType + ":" + url, info); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (SQLException e) {
			if ("08001".equals(e.getSQLState()) && DERBY.equals(fDBType)) { //$NON-NLS-1$
				if (DEBUG)
					DEBUG_WRITER.println("DriverManager.getConnection failed; retrying for cloudscape"); //$NON-NLS-1$
				// try Cloudscape
				fDBType = CLOUDSCAPE;
				fConnection = DriverManager.getConnection("jdbc:" + fDBType + ":" + url, info); //$NON-NLS-1$ //$NON-NLS-2$
			} else
				throw e;
		}
		if (DEBUG)
			DEBUG_WRITER.println("connect succeeded!"); //$NON-NLS-1$

		fConnection.setAutoCommit(false);
		fSQL = new SQL_Results(fConnection);
		fConnection.commit();

	} catch (SQLException ex) {
		PerformanceTestPlugin.logError(ex.getMessage());

	} catch (ClassNotFoundException e) {
		PerformanceTestPlugin.log(e);
	}
}

private void disconnect() {
	if (DEBUG)
		DEBUG_WRITER.println("disconnecting from DB"); //$NON-NLS-1$
	if (fSQL != null) {
		try {
			fSQL.dispose();
		} catch (SQLException e1) {
			PerformanceTestPlugin.log(e1);
		}
		fSQL = null;
	}
	if (fConnection != null) {
		try {
			fConnection.commit();
		} catch (SQLException e) {
			PerformanceTestPlugin.log(e);
		}
		try {
			fConnection.close();
		} catch (SQLException e) {
			PerformanceTestPlugin.log(e);
		}
		fConnection = null;
	}

	if (fIsEmbedded) {
		try {
			DriverManager.getConnection("jdbc:" + fDBType + ":;shutdown=true"); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (SQLException e) {
			String message = e.getMessage();
			if (message.indexOf("system shutdown.") < 0) //$NON-NLS-1$
				e.printStackTrace();
		}
	}
}

/*
 * Return the index of the given configuration in the stored list.
 */
private int getConfigId(String config) {
	if (CONFIGS == null) return -1;
	return Arrays.binarySearch(CONFIGS, config);
}

SQL_Results getSQL() {
    return fSQL;
}

/*
 * Query all comments from database
 */
private void internalQueryAllComments() {
	if (fSQL == null) return;
	if (COMMENTS != null) return;
	long start = System.currentTimeMillis();
	if (DEBUG) DEBUG_WRITER.print("		[DB query all comments..."); //$NON-NLS-1$
	ResultSet result = null;
	try {
		String[] comments = null;
		result = fSQL.queryAllComments();
		while (result.next()) {
			int commentID = result.getInt(1);
			// Ignore kind as there's only one
			// int commentKind = result.getInt(2); 
			String comment = result.getString(3);
			if (comments == null) {
				comments = new String[commentID+10];
			} else if (commentID >= comments.length) {
				int length = comments.length;
				System.arraycopy(comments, 0, comments = new String[commentID+10], 0, length);
			}
			comments[commentID] = comment;
		}
		COMMENTS = comments;
	} catch (SQLException e) {
		PerformanceTestPlugin.log(e);
	} finally {
		if (result != null) {
			try {
				result.close();
			} catch (SQLException e1) {
				// ignored
			}
		}
		if (DEBUG) DEBUG_WRITER.println("done in " + (System.currentTimeMillis() - start) + "ms]"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}

/*
 * Query all variations. This method stores all config and build names.
 */
private void internalQueryAllVariations(String configPattern) {
	if (fSQL == null) return;
	if (BUILDS != null) return;
	long start = System.currentTimeMillis();
	if (DEBUG) {
		DEBUG_WRITER.print("	- DB query all variations for configuration pattern: "+configPattern); //$NON-NLS-1$
		DEBUG_WRITER.print("..."); //$NON-NLS-1$
	}
	ResultSet result = null;
	try {
		CONFIGS = new String[MAX_CONFIGS];
		BUILDS = null;
		BUILDS_LENGTH = 0;
		result = fSQL.queryAllVariations(configPattern);
		while (result.next()) {
			String variation = result.getString(1); //  something like "||build=I20070615-1200||config=eclipseperfwin2_R3.3||jvm=sun|"
			StringTokenizer tokenizer = new StringTokenizer(variation, "=|"); //$NON-NLS-1$
			tokenizer.nextToken(); 												// 'build'
			String buildName = tokenizer.nextToken();				// 'I20070615-1200'
			tokenizer.nextToken();												// 'config'
			storeConfig(tokenizer.nextToken()); 	// 'eclipseperfwin2_R3.3'
			tokenizer.nextToken();												// 'jvm'
			String vmName = tokenizer.nextToken();					// 'sun'
			if (vmName.equals(SUPPORTED_VMS[0])) {
				storeBuildName(buildName);
			}
		}
		System.arraycopy(BUILDS, 0, BUILDS = new String[BUILDS_LENGTH], 0, BUILDS_LENGTH);
		for (int i=0; i<MAX_CONFIGS; i++) {
			if (CONFIGS[i] == null) {
				System.arraycopy(CONFIGS, 0, CONFIGS = new String[i], 0, i);
				break;
			}
		}
	} catch (SQLException e) {
		PerformanceTestPlugin.log(e);
	} finally {
		if (result != null) {
			try {
				result.close();
			} catch (SQLException e1) {
				// ignored
			}
		}
		if (DEBUG) DEBUG_WRITER.println("done in " + (System.currentTimeMillis() - start) + "ms]"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}

private Map internalQueryBuildScenarios(String scenarioPattern, String buildName) {
	if (fSQL == null) return null;
	long start = System.currentTimeMillis();
	if (DEBUG) {
		DEBUG_WRITER.print("	- DB query all scenarios"); //$NON-NLS-1$
		if (scenarioPattern != null) DEBUG_WRITER.print(" with pattern "+scenarioPattern); //$NON-NLS-1$
		DEBUG_WRITER.print(" for build: "+buildName); //$NON-NLS-1$
	}
	ResultSet result = null;
	Map allScenarios = new HashMap();
	try {
		result = fSQL.queryBuildScenarios(scenarioPattern, buildName);
		int previousId = -1;
		List scenarios = null;
		List scenariosNames = new ArrayList();
		for (int i = 0; result.next(); i++) {
			int id = result.getInt(1);
			String name = result.getString(2);
			scenariosNames.add(name);
			String shortName = result.getString(3);
			int component_id = storeComponent(getComponentNameFromScenario(name));
			if (component_id != previousId) {
				allScenarios.put(COMPONENTS[component_id], scenarios = new ArrayList());
				previousId = component_id;
			}
			scenarios.add(new ScenarioResults(id, name, shortName));
		}
		SCENARII = new String[scenariosNames.size()];
		scenariosNames.toArray(SCENARII);
	} catch (SQLException e) {
		PerformanceTestPlugin.log(e);
	} finally {
		if (result != null) {
			try {
				result.close();
			} catch (SQLException e1) { // ignored
			}
		}
		if (DEBUG) DEBUG_WRITER.println("done in " + (System.currentTimeMillis() - start) + "ms]"); //$NON-NLS-1$ //$NON-NLS-2$
	}
	return allScenarios;
}

private void internalQueryScenarioValues(ScenarioResults scenarioResults, String configPattern, String lastBuildName, long lastBuildDate) {
	if (fSQL == null) return;
	if (LOG) LOG_WRITER.starts("	- DB query all data points for config pattern: "+configPattern+" for scenario: " + scenarioResults.getShortName()); //$NON-NLS-1$ //$NON-NLS-2$
	internalQueryAllVariations(configPattern); // need to read all variations to have all build names
	ResultSet result = null;
	try {
		int count = 0;
		result = lastBuildName == null
			?	fSQL.queryScenarioDataPoints(configPattern, scenarioResults.getId())
			:	fSQL.queryScenarioTimestampDataPoints(configPattern, scenarioResults.getId(), lastBuildName, lastBuildDate);
		while (result.next()) {
			int dp_id = result.getInt(1);
			int step = result.getInt(2);
			String variation = result.getString(3); //  something like "|build=I20070615-1200||config=eclipseperfwin2_R3.3||jvm=sun|"
			StringTokenizer tokenizer = new StringTokenizer(variation, "=|"); //$NON-NLS-1$
			tokenizer.nextToken(); 													// 'build'
			String buildName = tokenizer.nextToken();					// 'I20070615-1200'
			tokenizer.nextToken();													// 'config'
			int config_id = getConfigId(tokenizer.nextToken()); 		// 'eclipseperfwin2_R3.3'
			int build_id = getBuildId(buildName);
			ResultSet rs2 = fSQL.queryDimScalars(dp_id);
			while (rs2.next()) {
				int dim_id = rs2.getInt(1);
				long value = rs2.getBigDecimal(2).longValue();
				if (build_id >= 0) { // build id may be negative (i.e. not stored in the array) if new run starts while we're getting results
					scenarioResults.setValue(build_id, dim_id, config_id, step, value);
				}
				count++;
			}
		}
		if (LOG) LOG_WRITER.ends("		-> " + count + " values read");  //$NON-NLS-1$ //$NON-NLS-2$
	} catch (SQLException e) {
		PerformanceTestPlugin.log(e);
	} finally {
		if (result != null) {
			try {
				result.close();
			} catch (SQLException e1) {
				// ignored
			}
		}
	}
}

private void internalQueryScenarioFailures(ScenarioResults scenarioResults, String config, BuildResults currentBuild, BuildResults baselineBuild) {
	if (fSQL == null) return;
	long start = System.currentTimeMillis();
	if (DEBUG) DEBUG_WRITER.print("	- DB query all failures for config pattern: "+config+" for scenario : "+scenarioResults.getShortName()+"..."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	ResultSet result = null;
	try {
		String currentBuildName = currentBuild.getName();
		String baselineBuildName = baselineBuild.getName();
		result = fSQL.queryScenarioFailures(scenarioResults.getId(), config, currentBuildName, baselineBuildName);
		while (result.next()) {
			String variation = result.getString(1);
			String failure = result.getString(2);
			StringTokenizer tokenizer = new StringTokenizer(variation, "=|"); //$NON-NLS-1$
			tokenizer.nextToken(); 									// 'build'
			String buildName = tokenizer.nextToken();	// 'I20070615-1200'
			if (buildName.equals(currentBuildName)) {
				currentBuild.setFailure(failure);
			} else if (buildName.equals(baselineBuildName)) {
				baselineBuild.setFailure(failure);
			}
		}
	} catch (SQLException e) {
		PerformanceTestPlugin.log(e);

	} finally {
		if (result != null) {
			try {
				result.close();
			} catch (SQLException e1) {
				// ignored
			}
		}
		if (DEBUG) DEBUG_WRITER.println("	-> done in " + (System.currentTimeMillis() - start) + "ms]"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}

private void internalQueryScenarioSummaries(ScenarioResults scenarioResults, String config, BuildResults currentBuild, BuildResults baselineBuild) {
	if (fSQL == null) return;
	long start = System.currentTimeMillis();
	if (DEBUG) DEBUG_WRITER.print("	- DB query all summaries for config: "+config+" for scenario: "+scenarioResults.getShortName()+"..."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	internalQueryAllComments();
	ResultSet result = null;
	try {
		String cBuildName = currentBuild.getName();
		String currentBuildName = cBuildName;
		String bBuildName = baselineBuild.getName();
		String baselineBuildName = bBuildName;
		int scenarioID = scenarioResults.getId();
		// First try to get summaries of elapsed process dimension
		result = fSQL.queryScenarioSummaries(scenarioID, config, cBuildName, bBuildName, InternalDimensions.ELAPSED_PROCESS.getId());
		while (result.next()) {
			String variation = result.getString(1);
			int summaryKind = result.getShort(2);
			int comment_id = result.getInt(3);
			StringTokenizer tokenizer = new StringTokenizer(variation, "=|"); //$NON-NLS-1$
			tokenizer.nextToken(); 									// 'build'
			String buildName = tokenizer.nextToken();	// 'I20070615-1200'
			BuildResults buildResults = null;
			if (buildName.equals(currentBuildName)) {
				buildResults = currentBuild;
			} else if (buildName.equals(baselineBuildName)) {
				buildResults = baselineBuild;
			}
			if (buildResults != null) {
				if(null != COMMENTS) {
					buildResults.setSummary(summaryKind, COMMENTS[comment_id]);
				} else {
					buildResults.setSummary(summaryKind, ""); //$NON-NLS-1$
				}
			}
		}
		// Update scenario comment if any
		result = fSQL.queryScenarioSummaries(scenarioID, config, cBuildName, bBuildName, 0);
		while (result.next()) {
			String variation = result.getString(1);
			int comment_id = result.getInt(3);
			StringTokenizer tokenizer = new StringTokenizer(variation, "=|"); //$NON-NLS-1$
			tokenizer.nextToken(); 									// 'build'
			String buildName = tokenizer.nextToken();	// 'I20070615-1200'
			BuildResults buildResults = null;
			if (buildName.equals(currentBuildName)) {
				buildResults = currentBuild;
			} else if (buildName.equals(baselineBuildName)) {
				buildResults = baselineBuild;
			}
			if (buildResults != null) {
				if(null != COMMENTS) {
					buildResults.setComment(COMMENTS[comment_id]);
				} else {
					buildResults.setComment(""); //$NON-NLS-1$
				}
			}
		}
	} catch (SQLException e) {
		PerformanceTestPlugin.log(e);
	} finally {
		if (result != null) {
			try {
				result.close();
			} catch (SQLException e1) {
				// ignored
			}
		}
		if (DEBUG) DEBUG_WRITER.println("done in " + (System.currentTimeMillis() - start) + "ms]"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}

/*
 * Store a component in the dynamic list. The list is sorted alphabetically.
 */
private int storeComponent(String component) {
	if (COMPONENTS == null) {
		COMPONENTS = new String[SUPPORTED_COMPONENTS.length];
	}
	int idx = Arrays.binarySearch(SUPPORTED_COMPONENTS, component);
	if (idx < 0) {
		throw new RuntimeException("Unexpected component name: "+component); //$NON-NLS-1$
	}
	if (COMPONENTS[idx] == null) {
		COMPONENTS[idx] = SUPPORTED_COMPONENTS[idx];
	}
	return idx;
}

/*
 * Store a build name in the dynamic list.
 * The list is sorted alphabetically.
 */
private int storeBuildName(String build) {
	boolean isVersion = Character.isDigit(build.charAt(0));
	if (BUILDS == null) {
		BUILDS = new String[1];
		BUILDS[BUILDS_LENGTH++] = build;
		if (isVersion) {
			LAST_BASELINE_BUILD = build;
		} else {
			LAST_CURRENT_BUILD = build;
		}
		return 0;
	}
	int idx = Arrays.binarySearch(BUILDS, build);
	if (idx >= 0) return idx;
	int index = -idx-1;
	int length = BUILDS.length;
	if (BUILDS_LENGTH == length) {
		String[] array = new String[length+1];
		if (index > 0) System.arraycopy(BUILDS, 0, array, 0, index);
		array[index] = build;
		if (index < length) {
			System.arraycopy(BUILDS, index, array, index+1, length-index);
		}
		BUILDS = array;
	} else if (index < length) {
		System.arraycopy(BUILDS, index, BUILDS, index+1, length-index);
		BUILDS[index] = build;
	}
	BUILDS_LENGTH++;
	if (isVersion) {
		if (LAST_BASELINE_BUILD == null || build.compareTo(LAST_BASELINE_BUILD) > 0) {
			LAST_BASELINE_BUILD = build;
		}
	} else {
		if (LAST_CURRENT_BUILD == null || build.substring(1).compareTo(LAST_CURRENT_BUILD.substring(1)) >= 0) {
			LAST_CURRENT_BUILD = build;
		}
	}
	return index;
}

/*
 * Store a configuration in the dynamic list.
 * The list is sorted alphabetically.
 */
private int storeConfig(String config) {
	for (int i=0; i<MAX_CONFIGS; i++) {
		if (CONFIGS[i] == null) {
			CONFIGS[i] = config;
			return i;
		}
		if (config.equals(CONFIGS[i])) {
			return i;
		}
	}
	return -1;
}

}

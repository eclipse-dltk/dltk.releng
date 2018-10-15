/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.dltk.core.tests;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.dltk.core.DLTKCore;
import org.eclipse.dltk.core.IBuildpathEntry;
import org.eclipse.dltk.core.IModelElement;
import org.eclipse.dltk.core.IProjectFragment;
import org.eclipse.dltk.core.IScriptFolder;
import org.eclipse.dltk.core.IScriptProject;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.core.ModelException;
import org.eclipse.dltk.core.search.indexing.IndexManager;
import org.eclipse.dltk.core.tests.junit.extension.TestCase;
import org.eclipse.dltk.core.tests.model.AbstractModelTests;
import org.eclipse.dltk.core.tests.performance.util.DLTKCorePerformanceMeter;
import org.eclipse.dltk.core.tests.performance.util.Statistics;
import org.eclipse.dltk.core.tests.util.Util;
import org.eclipse.dltk.internal.core.ArchiveProjectFragment;
import org.eclipse.dltk.internal.core.DLTKCorePreferenceInitializer;
import org.eclipse.dltk.internal.core.ModelManager;
import org.eclipse.dltk.internal.core.ScriptProject;
import org.eclipse.test.internal.performance.data.DataPoint;
import org.eclipse.test.performance.Dimension;
import org.eclipse.test.performance.Performance;

public abstract class FullSourceWorkspaceTests extends TestCase {

	// Debug variables
	protected final static boolean DEBUG = "true".equals(System
			.getProperty("debug"));
	protected final static boolean PRINT = "true".equals(System
			.getProperty("print"));
	/**
	 * Flag to validate test run environnement.
	 * <p>
	 * This property has been added to speed-up check-up of local shells to run
	 * the entire performance tests.
	 * <p>
	 * WARNING: if this property is set, *nothing at all* will be run, neither
	 * measure nor warm-up.
	 */
	final static boolean TOUCH = "true".equals(System.getProperty("touch"));

	// Garbage collect constants
	final static int MAX_GC = 10; // Max gc iterations
	final static int TIME_GC = 500; // Sleep to wait gc to run (in ms)
	final static int DELTA_GC = 1000; // Threshold to remaining free memory

	// Workspace variables
	protected static TestingEnvironment ENV = null;
	protected static IScriptProject[] ALL_PROJECTS;
	protected static IScriptProject JDT_CORE_PROJECT;
	protected final static String BIG_PROJECT_NAME = "BigProject";
	protected static ScriptProject BIG_PROJECT;
	// protected final static String JUNIT_PROJECT_NAME = "junit";
	// protected static IJavaProject JUNIT_PROJECT;
	protected boolean SKIP_UNZIP = false;

	// Index variables
	protected static IndexManager INDEX_MANAGER = ModelManager
			.getModelManager().getIndexManager();

	// Tests infos
	protected static int ALL_TESTS_COUNT = 0;
	protected static int TEST_POSITION = 0;
	protected static List TESTS_NAME_LIST;

	/**
	 * Count of measures done for all tests. <b> Default value is 10 but can be
	 * modified using system property "measures". <b> For example,
	 * "-Dmeasures=1" will make all performance test suites to run only 1
	 * iteration for each test.
	 */
	protected final static int MEASURES_COUNT;
	static {
		String measures = System.getProperty("measures", "5");
		int count = 10;
		try {
			count = Integer.parseInt(measures);
			if (count < 0 || count > 20) {
				System.out
						.println("INFO: Measures parameter ("
								+ count
								+ ") is ignored as it is an invalid value! (should be between 0 and 20)");
				count = 10;
			} else if (count != 10) {
				System.out
						.println("WARNING: Measures count has been changed while running this test = "
								+ count + " instead of 10 normally!");
			}
		} catch (NumberFormatException nfe) {
			// use default value
			System.out
					.println("INFO: Specified 'measures' VM argument (="
							+ measures
							+ ") is ignored as it is not an integer (0-20)!");
		}
		MEASURES_COUNT = count;
	}

	// Scenario information
	String scenarioReadableName;
	protected String scenarioShortName;
	protected StringBuffer scenarioComment;
	static Map SCENARII_COMMENT = new HashMap();

	// Time measuring
	long startMeasuring, testDuration;

	// Error threshold. Statistic should not be take into account when it's
	// reached
	protected final static double ERROR_THRESHOLD = 0.005; // default is 0.5%
	protected final static String ERROR_STRING;
	static {
		NumberFormat valueFormat = NumberFormat.getNumberInstance();
		valueFormat.setMaximumFractionDigits(1);
		ERROR_STRING = valueFormat.format(ERROR_THRESHOLD * 100);
	}

	/**
	 * Variable used for log files. Log files are used in conjonction with
	 * {@link JdtCorePerformanceMeter} class. These are file where CPU times of
	 * each test of subclasses are stored. This specific way to run performance
	 * tests is activated by specifying following options:
	 * -DPerformanceMeterFactory
	 * =org.eclipse.jdt.core.tests.performance:org.eclipse
	 * .jdt.core.tests.performance.util.JdtCorePerformanceMeterFactory
	 * -DlogDir=directory where you want to write log files (for example
	 * d:/usr/OTI/tests/perfs/stats)
	 * 
	 */
	// Store directory where to put files
	private final static File INVALID_DIR = new File("Invalid");
	protected static File LOG_DIR;
	// Types of statistic which can be stored.
	protected final static String[] DIM_NAMES = { "cpu", "elapsed", "heap" };
	// Main version which is logged
	protected final static String LOG_VERSION = "1.0";
	// Patch version currently applied: may be null!
	protected final static String PATCH_ID = System.getProperty("patch");
	public static String RUN_ID;

	// Format to store/display numbers
	private NumberFormat percentFormat = NumberFormat.getPercentInstance();
	private final NumberFormat d2Format = NumberFormat.getNumberInstance();
	protected boolean WORKSPACE_REQUIRED = true;
	{
		this.percentFormat.setMaximumFractionDigits(1);
		this.d2Format.setMaximumFractionDigits(2);
	}

	/**
	 * Initialize log directory.
	 * 
	 * Directory where log files must be put is specified by System property
	 * <code>logDir</code>. For example, if user want to store log files in
	 * d:/usr/OTI/tests/perfs/stats, then he has to specify:
	 * -DlogDir=d:/usr/OTI/tests/perfs/stats in VM Arguments of his performance
	 * test launch configuration.
	 * 
	 * CAUTION: Parent directory at least <b>must</b> exist before running test
	 * otherwise it won't be created and times won't be logged. This was
	 * intentional to avoid unexpected log files creation (especially during
	 * nightly/integration builds).
	 */
	protected static void initLogDir() {
		String logDir = System.getProperty("logDir");
		File dir = null;
		if (logDir != null) {
			// Verify that parent log dir is valid if exist
			dir = new File(logDir);
			if (dir.exists()) {
				if (!dir.isDirectory()) {
					System.err
							.println(logDir
									+ " is not a valid directory, log files will NOT be written!");
					dir = INVALID_DIR;
				}
			} else {
				// Create parent dir if necessary
				int n = 0;
				boolean created = false;
				while (!created && n < 3) {
					created = dir.mkdir();
					if (!created) {
						dir = dir.getParentFile();
					}
					n++;
				}
				if (!created) {
					System.err.println("Cannot create " + logDir
							+ ", log files will NOT be written!");
					dir = INVALID_DIR;
				}
			}

			// Create Log dir
			String[] subdirs = (PATCH_ID == null) ? new String[] { LOG_VERSION,
					RUN_ID } : new String[] { LOG_VERSION, PATCH_ID, RUN_ID };
			for (int i = 0; i < subdirs.length; i++) {
				dir = new File(dir, subdirs[i]);
				if (dir.exists()) {
					if (!dir.isDirectory()) {
						System.err
								.println(dir.getPath()
										+ " is not a valid directory, log files will NOT be written!");
						dir = INVALID_DIR;
						break;
					}
				} else if (!dir.mkdirs()) {
					System.err.println("Cannot create " + dir.getPath()
							+ ", log files will NOT be written!");
					dir = INVALID_DIR;
					break;
				}
			}
		}
		LOG_DIR = dir;
	}

	/**
	 * @param name
	 */
	public FullSourceWorkspaceTests(String name) {
		super(name);
	}

	/**
	 * Create test suite for a given TestCase class.
	 * 
	 * Use this method for all JDT/Core performance test using full source
	 * workspace. All test count is computed to know when tests are about to be
	 * finished.
	 * 
	 * @param testClass
	 *            TestCase test class
	 * @return test suite
	 */
	protected static Test buildSuite(Class testClass) {

		// Create tests
		String className = testClass.getName();
		TestSuite suite = new TestSuite(className);
		List tests = buildTestsList(testClass);
		int size = tests.size();
		TESTS_NAME_LIST = new ArrayList(size);
		for (int i = 0; i < size; i++) {
			FullSourceWorkspaceTests test = (FullSourceWorkspaceTests) tests
					.get(i);
			suite.addTest(test);
			TESTS_NAME_LIST.add(test.getName());
		}
		ALL_TESTS_COUNT += suite.testCount();

		// Init log dir if necessary
		if (LOG_DIR == null) {
			if (RUN_ID == null) {
				RUN_ID = suiteTypeShortName(testClass);
			}
			initLogDir();
		}

		// Return created tests
		return suite;
	}

	/**
	 * Create print streams (one for each type of statistic). Log file names
	 * have all same prefix based on test class name, include type of statistic
	 * stored in it and always have extension ".log".
	 * 
	 * If log file does not exist, then add column headers at the beginning of
	 * the file.
	 * 
	 * This method does nothing if log files directory has not been initialized
	 * (which should be the case most of times and especially while running
	 * nightly/integration build performance tests).
	 */
	protected static void createPrintStream(Class testClass,
			PrintStream[] logStreams, int count, String prefix) {
		if (LOG_DIR != null) {
			for (int i = 0, ln = DIM_NAMES.length; i < ln; i++) {
				String suiteTypeName = suiteTypeShortName(testClass);
				File logFile = new File(LOG_DIR, suiteTypeName + '_'
						+ DIM_NAMES[i] + ".log");
				if (TOUCH) {
					System.out.println("Log file " + logFile.getPath()
							+ " would be opened.");
					return;
				}
				try {
					boolean fileExist = logFile.exists();
					if (logStreams[i] != null) { // closing previous series;
						// last series is closed by
						// the process
						logStreams[i].close();
					}
					logStreams[i] = new PrintStream(new FileOutputStream(
							logFile, true));
					if (logStreams[i] != null) {
						if (!fileExist) {
							logStreams[i].print("Date  \tTime  \t");
							for (int j = 0; j < count; j++) {
								String testName = ((String) TESTS_NAME_LIST
										.get(j))
										.substring(4 + (prefix == null ? 0
												: prefix.length())); // 4="test".
								// length
								// ()
								logStreams[i].print(testName + '\t');
							}
							logStreams[i].println("Comment");

						}
						// Log date and time
						Date date = new Date(System.currentTimeMillis());
						logStreams[i].print(DateFormat.getDateInstance(3)
								.format(date) + '\t');
						logStreams[i].print(DateFormat.getTimeInstance(3)
								.format(date) + '\t');
						System.out.println("Log file " + logFile.getPath()
								+ " opened.");
					} else {
						System.err.println("Cannot open " + logFile.getPath()
								+ "!!!");
					}
				} catch (FileNotFoundException e) {
					System.err.println("Cannot find file " + logFile.getPath()
							+ "!!!");
				}
			}
		}
	}

	/*
	 * Returns the OS path to the directory that contains this plugin.
	 */
	public static String getPluginDirectoryPath(String plugin) {
		try {
			URL platformURL = Platform.getBundle(plugin).getEntry("/");
			return new File(FileLocator.toFileURL(platformURL).getFile())
					.getAbsolutePath();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Return a short name for a given suite test. Typically remove prefix
	 * "FullSourceWorkspace" and suffix "Test"
	 */
	public static String suiteTypeShortName(Class testClass) {
		String className = testClass.getName();
		int startIndex = className.indexOf("FullSourceWorkspace");
		int endIndex = className.lastIndexOf("Test");
		if (startIndex < 0)
			return null;
		startIndex += "FullSourceWorkspace".length();
		return className.substring(startIndex, endIndex);
	}

	/**
	 * Log test performance result and close stream if it was last one.
	 */
	protected void logPerfResult(PrintStream[] logStreams, int count) {
		if (TOUCH)
			return;

		// Perfs comment buffers
		int length = DIM_NAMES.length;
		String[] comments = new String[length];

		// Log perf result
		boolean haveStats = DLTKCorePerformanceMeter.STATISTICS != null;
		if (haveStats) {
			DataPoint[] dataPoints = (DataPoint[]) DLTKCorePerformanceMeter.STATISTICS
					.get(this.scenarioReadableName);
			if (dataPoints != null) {
				Statistics statistics = new Statistics(dataPoints);
				for (int idx = 0; idx < length; idx++) {
					storeDimension(logStreams, comments, statistics, idx);
				}
			} else {
				try {
					haveStats = false;
					Thread.sleep(1000);
					System.err.println(this.scenarioShortName
							+ ": we should have stored statistics!");
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// do nothing
				}
			}
		}

		// Update comment buffers
		StringBuffer[] scenarioComments = (StringBuffer[]) SCENARII_COMMENT
				.get(getClass());
		if (scenarioComments == null) {
			scenarioComments = new StringBuffer[length];
			SCENARII_COMMENT.put(getClass(), scenarioComments);
		}
		for (int i = 0; i < length; i++) {
			if (this.scenarioComment != null || comments[i] != null) {
				if (scenarioComments[i] == null) {
					scenarioComments[i] = new StringBuffer();
				} else {
					scenarioComments[i].append(' ');
				}
				if (this.scenarioComment == null) {
					scenarioComments[i].append("[" + TEST_POSITION + "]");
				} else {
					scenarioComments[i].append(this.scenarioComment);
				}
				if (comments[i] != null) {
					if (this.scenarioComment != null)
						scenarioComments[i].append(',');
					scenarioComments[i].append(comments[i]);
				}
			}
		}

		// Close log
		if (count == 0) {
			for (int i = 0, ln = logStreams.length; i < ln; i++) {
				if (logStreams[i] != null) {
					if (haveStats) {
						if (scenarioComments[i] != null) {
							logStreams[i].print(scenarioComments[i].toString());
						}
						logStreams[i].println();
					}
					logStreams[i].close();
				}
			}
			TEST_POSITION = 0;
		}
	}

	private void storeDimension(PrintStream[] logStreams, String[] comments,
			Statistics statistics, int index) {
		System.out.println("	- " + statistics.toString(index));
		double stddev = statistics.getStddev(index);
		double average = statistics.getAverage(index);
		long count = statistics.getCount(index);
		double error = stddev / Math.sqrt(count);
		if ((error / average) > ERROR_THRESHOLD) {
			// if (logStreams[0] != null) logStreams[0].print("'"); // disable
			// over threshold result for xls table
			System.out.println("	WARNING: " + DIM_NAMES[index]
					+ " error is over " + ERROR_STRING + "%: "
					+ this.d2Format.format(stddev) + "/sqrt(" + count + ")="
					+ this.percentFormat.format(error / average));
			comments[index] = "err="
					+ this.percentFormat.format(error / average);
		}
		if (logStreams[index] != null) {
			logStreams[index].print("" + statistics.getSum(index) + "\t");
		}
	}

	/**
	 * Perform gc several times to be sure that it won't take time while
	 * executing current test.
	 */
	protected void runGc() {
		int iterations = 0;
		long delta = 0, free = 0;
		for (int i = 0; i < MAX_GC; i++) {
			free = Runtime.getRuntime().freeMemory();
			System.gc();
			delta = Runtime.getRuntime().freeMemory() - free;
			// if (DEBUG) System.out.println("Loop gc "+ ++iterations +
			// " (free="+free+", delta="+delta+")");
			try {
				Thread.sleep(TIME_GC);
			} catch (InterruptedException e) {
				// do nothing
			}
		}
		if (iterations == MAX_GC && delta > DELTA_GC) {
			// perhaps gc was not well executed
			System.out.println("WARNING: " + this.scenarioShortName
					+ " still get " + delta + " unfreeable memory (free="
					+ free + ",total=" + Runtime.getRuntime().totalMemory()
					+ ") after " + MAX_GC + " gc...");
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// do nothing
			}
		}
	}

	/**
	 * Override super implementation to:
	 * <ul>
	 * <li>store scenario names and comment (one scenario per test)</li>
	 * <li> init workspace if first test run</li>
	 * <li>increment test position</li>
	 * </ul>
	 * 
	 * @see org.eclipse.test.performance.PerformanceTestCase#setUp()
	 */
	protected void setUp() throws Exception {

		super.setUp();

		// Store scenario readable name
		String scenario = Performance.getDefault().getDefaultScenarioId(this);
		this.scenarioReadableName = scenario.substring(scenario
				.lastIndexOf('.') + 1, scenario.length() - 2);
		this.scenarioShortName = this.scenarioReadableName.substring(
				this.scenarioReadableName.lastIndexOf('#') + 5/*
																 * 1+"test" .
																 * length ()
																 */, this.scenarioReadableName.length());
		this.scenarioComment = null;

		// Set testing environment if null
		if (ENV == null) {
			ENV = new TestingEnvironment();
			ENV.openEmptyWorkspace();
			if (WORKSPACE_REQUIRED) {
				setUpFullSourceWorkspace();
			}
			// if (JUNIT_PROJECT == null) {
			// setUpJunitProject();
			// }
		}

		// Verify that all used projects were found in wksp
		// assertNotNull("We should have found " + DLTKCore.PLUGIN_ID
		// + " project in workspace!!!", JDT_CORE_PROJECT);

		// Increment test position
		TEST_POSITION++;

		// Abort if only touch
		if (TOUCH) {
			String testPrintName = "'" + this.scenarioShortName + "' test";
			System.out.println("Touch " + testPrintName
					+ " to verify that it will run correctly.");
			throw new Error(testPrintName + " execution has been aborted!");
		}

		// Print test name
		System.out
				.println("================================================================================");
		System.out.println("Running " + this.scenarioReadableName + "...");

		// Time measuring
		this.testDuration = 0;

		// Wait 2 seconds
		Thread.sleep(2000);
	}

	/*
	 * Set up full source workpsace from zip file.
	 */
	private void setUpFullSourceWorkspace() throws IOException, CoreException {

		// Get wksp info
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		final IWorkspaceRoot workspaceRoot = workspace.getRoot();
		String targetWorkspacePath = workspaceRoot.getLocation().toFile()
				.getCanonicalPath();

		// Modify resources workspace preferences to avoid disturbing tests
		// while running them
		IEclipsePreferences resourcesPreferences = new InstanceScope()
				.getNode(ResourcesPlugin.PI_RESOURCES);
		resourcesPreferences.put(ResourcesPlugin.PREF_AUTO_REFRESH, "false");
		workspace.getDescription().setSnapshotInterval(Long.MAX_VALUE);
		workspace.getDescription().setAutoBuilding(false);

		// Get projects directories
		File wkspDir = new File(targetWorkspacePath);
		// FullSourceProjectsFilter filter = new FullSourceProjectsFilter();
		FileFilter filter = new FileFilter() {
			public boolean accept(File pathname) {
				if (pathname.getName().startsWith(".")) {
					return false;
				}
				return true;
			}

		};
		File[] directories = wkspDir.listFiles(filter);
		long start = System.currentTimeMillis();
		int dirLength = directories.length;
		if (dirLength != 62) {
			String fullSourceZipPath = getFullWorkspaceZip();
			System.out.println("Unzipping " + fullSourceZipPath);
			System.out.print("	in " + targetWorkspacePath + "...");
			if (!SKIP_UNZIP) {
				Util.unzip(fullSourceZipPath, targetWorkspacePath);
			}
			System.out.println(" done in "
					+ (System.currentTimeMillis() - start) + "ms.");
			directories = wkspDir.listFiles(filter);
			dirLength = directories.length;
		}

		// Init environment with existing porjects
		System.out.print("Create and open projects in environment...");
		start = System.currentTimeMillis();
		for (int i = 0; i < dirLength; i++) {
			String dirName = directories[i].getName();
			IProject project = workspaceRoot.getProject(dirName);
			if (project.exists()) {
				ENV.addProject(project);
			} else {
				ENV.addProject(dirName);
			}
		}
		System.out.println("(" + (System.currentTimeMillis() - start) + "ms)");

		ALL_PROJECTS = DLTKCore.create(workspaceRoot).getScriptProjects();

		// Set classpaths (workaround bug 73253 Project references not set on
		// project open)
		System.out.println("done");

		// Initialize Parser wokring copy
	}

	public abstract String getFullWorkspaceZip();

	// {
	// return getPluginDirectoryPath() + File.separator
	// + "full-source-R3_0.zip";
	// }

	/*
	 * Create JUnit project and add it to the workspace
	 * 
	 * private void setUpJunitProject() throws CoreException, IOException {
	 * IWorkspace workspace = ResourcesPlugin.getWorkspace(); IWorkspaceRoot
	 * workspaceRoot = workspace.getRoot(); final String targetWorkspacePath =
	 * workspaceRoot.getLocation().toFile().getCanonicalPath(); // Print for log
	 * in case of project creation troubles... System.out.println ("Create
	 * '"+JUNIT_PROJECT_NAME+"' project in "+workspaceRoot .getLocation()+":");
	 * long start = System.currentTimeMillis(); // Print for log in case of
	 * project creation troubles... String genericsZipPath =
	 * getPluginDirectoryPath() + File.separator + JUNIT_PROJECT_NAME +
	 * "src.zip"; start = System.currentTimeMillis();
	 * System.out.println("Unzipping "+genericsZipPath); System.out.print(" in
	 * "+targetWorkspacePath+"..."); // Unzip file Util.unzip(genericsZipPath,
	 * targetWorkspacePath); System.out.println("
	 * "+(System.currentTimeMillis()-start)+"ms."); // Add project to workspace
	 * System.out.print(" - add project to full source workspace..."); start =
	 * System.currentTimeMillis(); ENV.addProject(JUNIT_PROJECT_NAME);
	 * JUNIT_PROJECT = createJavaProject(JUNIT_PROJECT_NAME, new String[]{ "src" },
	 * "bin", "1.5");
	 * JUNIT_PROJECT.setRawClasspath(JUNIT_PROJECT.getResolvedClasspath(true),
	 * null); // Print for log in case of project creation troubles...
	 * System.out.println(" "+(System.currentTimeMillis()-start)+"ms."); }
	 */

	/**
	 * @deprecated Use {@link #tagAsGlobalSummary(String,Dimension,boolean)}
	 *             instead
	 */
	public void tagAsGlobalSummary(String shortName, Dimension dimension) {
		tagAsGlobalSummary(shortName, dimension, false); // do NOT put in
		// fingerprint
	}

	protected void tagAsGlobalSummary(String shortName, boolean fingerprint) {
		tagAsGlobalSummary(shortName, Dimension.ELAPSED_PROCESS, fingerprint);
	}

	protected void tagAsGlobalSummary(String shortName, Dimension dimension,
			boolean fingerprint) {
		if (DEBUG)
			System.out.println(shortName);
		if (fingerprint)
			super.tagAsGlobalSummary(shortName, dimension);
	}

	/**
	 * @deprecated We do not use this method...
	 */
	public void tagAsGlobalSummary(String shortName, Dimension[] dimensions) {
		System.out
				.println("ERROR: tagAsGlobalSummary(String, Dimension[]) is not implemented!!!");
	}

	/**
	 * @deprecated Use {@link #tagAsSummary(String,Dimension,boolean)} instead
	 */
	public void tagAsSummary(String shortName, Dimension dimension) {
		tagAsSummary(shortName, dimension, false); // do NOT put in fingerprint
	}

	protected void tagAsSummary(String shortName, boolean fingerprint) {
		tagAsSummary(shortName, Dimension.ELAPSED_PROCESS, fingerprint);
	}

	public void tagAsSummary(String shortName, Dimension dimension,
			boolean fingerprint) {
		if (DEBUG)
			System.out.println(shortName);
		if (fingerprint)
			super.tagAsSummary(shortName, dimension);
	}

	/**
	 * @deprecated We do not use this method...
	 */
	public void tagAsSummary(String shortName, Dimension[] dimensions) {
		System.out
				.println("ERROR: tagAsGlobalSummary(String, Dimension[]) is not implemented!!!");
	}

	public void tagAsSummary(String shortName, Dimension[] dimensions,
			boolean fingerprint) {
		if (DEBUG)
			System.out.println(shortName);
		if (fingerprint)
			super.tagAsSummary(shortName, dimensions);
	}

	public void startMeasuring() {
		this.startMeasuring = System.currentTimeMillis();
		super.startMeasuring();
	}

	public void stopMeasuring() {
		super.stopMeasuring();
		this.testDuration += System.currentTimeMillis() - this.startMeasuring;
	}

	public void commitMeasurements() {
		if (PRINT)
			System.out.println("	Test duration = " + this.testDuration + "ms");
		super.commitMeasurements();
	}

	/**
	 * Override super implementation to:
	 * <ul>
	 * <li>decrement all test count</li>
	 * <li>reset workspace and back to initial options if last test run</li>
	 * </ul>
	 * 
	 * @see org.eclipse.test.performance.PerformanceTestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		ALL_TESTS_COUNT--;
		if (ALL_TESTS_COUNT == 0) {
		}
		if (!WORKSPACE_REQUIRED) {
			ENV.resetWorkspace();
			ENV = null;
		}
		super.tearDown();
	}

	/*
	 * Delete a directory from file system.
	 * 
	 * @param directory
	 * 
	 * protected void cleanupDirectory(File directory) { if
	 * (!directory.isDirectory() || !directory.exists()) { return; } String[]
	 * fileNames = directory.list(); for (int i = 0; i < fileNames.length; i++) {
	 * File file = new File(directory, fileNames[i]); if (file.isDirectory()) {
	 * cleanupDirectory(file); } else { if (!file.delete())
	 * System.out.println("Could not delete file " + file.getPath());
	 * //$NON-NLS-1$ } } if (!directory.delete()) System.out.println("Could not
	 * delete directory " + directory.getPath()); //$NON-NLS-1$ }
	 */

	/*
	 * Clear given options
	 */
	Map clearOptions(Map options) {
		// turn all errors and warnings into ignore. The customizable set of
		// compiler
		// options only contains additional Eclipse options. The standard JDK
		// compiler
		// options can't be changed anyway.
		for (Iterator iter = options.keySet().iterator(); iter.hasNext();) {
			String key = (String) iter.next();
			String value = (String) options.get(key);
			if ("error".equals(value) || "warning".equals(value)) { //$NON-NLS-1$//$NON-NLS-2$
				// System.out.println("Ignoring - " + key);
				options.put(key, "ignore"); //$NON-NLS-1$
			} else if ("enabled".equals(value)) {
				// System.out.println(" - disabling " + key);
				options.put(key, "disabled");
			}
		}
		return options;
	}

	/**
	 * @see org.eclipse.jdt.core.tests.model.AbstractJavaModelTests#createJavaProject(String,
	 *      String[], String[], String[][], String[][], String[], String[][],
	 *      String[][], boolean[], String, String[], String[][], String[][],
	 *      String)
	 */
	protected IScriptProject createJavaProject(final String projectName,
			final String[] sourceFolders, final String projectOutput,
			final String compliance) throws CoreException {
		final IScriptProject[] result = new IScriptProject[1];
		IWorkspaceRunnable create = new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {

				// create classpath entries
				IProject project = ENV.getProject(projectName);
				IPath projectPath = project.getFullPath();
				int sourceLength = sourceFolders == null ? 0
						: sourceFolders.length;
				IBuildpathEntry[] entries = new IBuildpathEntry[sourceLength + 1];
				for (int i = 0; i < sourceLength; i++) {
					IPath sourcePath = new Path(sourceFolders[i]);
					int segmentCount = sourcePath.segmentCount();
					if (segmentCount > 0) {
						// create folder and its parents
						IContainer container = project;
						for (int j = 0; j < segmentCount; j++) {
							IFolder folder = container.getFolder(new Path(
									sourcePath.segment(j)));
							if (!folder.exists()) {
								folder.create(true, true, null);
							}
							container = folder;
						}
					}
					// create source entry
					entries[i] = DLTKCore.newSourceEntry(projectPath
							.append(sourcePath), new IPath[0], new IPath[0],
							null);
				}

				// Add JRE_LIB entry
				// entries[sourceLength] = DLTKCore.newVariableEntry(new Path(
				// "JRE_LIB"), null, null);

				// create project's output folder
				IPath outputPath = new Path(projectOutput);
				if (outputPath.segmentCount() > 0) {
					IFolder output = project.getFolder(outputPath);
					if (!output.exists()) {
						output.create(true, true, null);
					}
				}

				// set classpath and output location
				IScriptProject javaProject = ENV.getScriptProject(projectName);

				result[0] = javaProject;
			}
		};
		ResourcesPlugin.getWorkspace().run(create, null);
		return result[0];
	}

	private void collectAllFiles(File root, ArrayList collector,
			FileFilter fileFilter) {
		File[] files = root.listFiles(fileFilter);
		for (int i = 0; i < files.length; i++) {
			final File currentFile = files[i];
			if (currentFile.isDirectory()) {
				collectAllFiles(currentFile, collector, fileFilter);
			} else {
				collector.add(currentFile);
			}
		}
	}

	protected File[] getAllFiles(File root, FileFilter fileFilter) {
		ArrayList files = new ArrayList();
		if (root.isDirectory()) {
			collectAllFiles(root, files, fileFilter);
			File[] result = new File[files.size()];
			files.toArray(result);
			return result;
		} else {
			return null;
		}
	}

	/**
	 * Returns the specified source module in the given project, root, and
	 * folder or <code>null</code> if it does not exist.
	 */
	public ISourceModule getSourceModule(String projectName, String rootPath,
			IPath path) throws ModelException {
		IScriptFolder folder = getScriptFolder(projectName, rootPath, path
				.removeLastSegments(1));
		if (folder == null) {
			return null;
		}
		return folder.getSourceModule(path.lastSegment());
	}

	public ISourceModule getSourceModule(String projectName, String rootPath,
			String path) throws ModelException {
		IScriptFolder folder = getScriptFolder(projectName, rootPath, new Path(
				path).removeLastSegments(1));
		if (folder == null) {
			return null;
		}
		return folder.getSourceModule(new Path(path).lastSegment().toString());
	}

	/**
	 * Returns the specified script folder in the given project and fragment, or
	 * <code>null</code> if it does not exist. The rootPath must be specified
	 * as a project relative path. The empty path refers to the default package
	 * fragment.
	 */
	public IScriptFolder getScriptFolder(String projectName,
			String fragmentPath, IPath path) throws ModelException {
		IProjectFragment root = getProjectFragment(projectName, fragmentPath);
		if (root == null) {
			return null;
		}
		return root.getScriptFolder(path);
	}

	/**
	 * Returns the specified package fragment root in the given project, or
	 * <code>null</code> if it does not exist. If relative, the rootPath must
	 * be specified as a project relative path. The empty path refers to the
	 * package fragment root that is the project folder iteslf. If absolute, the
	 * rootPath refers to either an external zip, or a resource internal to the
	 * workspace
	 */
	public IProjectFragment getProjectFragment(String projectName,
			String fragmentPath) throws ModelException {

		IScriptProject project = getProject(projectName);
		if (project == null) {
			return null;
		}
		IPath path = new Path(fragmentPath);
		if (path.isAbsolute()) {
			IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace()
					.getRoot();
			IResource resource = workspaceRoot.findMember(path);
			IProjectFragment root;
			// resource in the workspace
			root = project.getProjectFragment(resource);
			return root;
		} else {
			IProjectFragment[] roots = project.getProjectFragments();
			if (roots == null || roots.length == 0) {
				return null;
			}
			for (int i = 0; i < roots.length; i++) {
				IProjectFragment root = roots[i];
				if (root.getUnderlyingResource().getProjectRelativePath()
						.equals(path)) {
					return root;
				}
			}
		}
		return null;
	}

	/**
	 * Returns the specified package fragment in the given project and root, or
	 * <code>null</code> if it does not exist. The rootPath must be specified
	 * as a project relative path. The empty path refers to the default package
	 * fragment.
	 */
	protected IScriptFolder getPackageFragment(IScriptProject project,
			String rootPath, String packageName) throws ModelException {
		IProjectFragment root = getPackageFragmentRoot(project, rootPath);
		if (root == null) {
			return null;
		}
		return root.getScriptFolder(packageName);
	}

	/**
	 * Returns the specified package fragment root in the given project, or
	 * <code>null</code> if it does not exist. If relative, the rootPath must
	 * be specified as a project relative path. The empty path refers to the
	 * package fragment root that is the project folder iteslf. If absolute, the
	 * rootPath refers to either an external jar, or a resource internal to the
	 * workspace
	 */
	public IProjectFragment getPackageFragmentRoot(IScriptProject project,
			String rootPath) throws ModelException {

		if (project == null) {
			return null;
		}
		IPath path = new Path(rootPath);
		if (path.isAbsolute()) {
			IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace()
					.getRoot();
			IResource resource = workspaceRoot.findMember(path);
			IProjectFragment root;
			if (resource == null) {
				// external jar
				root = project.getProjectFragment(rootPath);
			} else {
				// resource in the workspace
				root = project.getProjectFragment(resource);
			}
			return root;
		} else {
			IProjectFragment[] roots = project.getProjectFragments();
			if (roots == null || roots.length == 0) {
				return null;
			}
			for (int i = 0; i < roots.length; i++) {
				IProjectFragment root = roots[i];
				if (!root.isExternal()
						&& root.getUnderlyingResource()
								.getProjectRelativePath().equals(path)) {
					return root;
				}
			}
		}
		return getExternalJarFile(project, rootPath);
	}

	/**
	 * Returns project corresponding to given name or null if none is found.
	 * 
	 * @param projectName
	 * @return IScriptProject
	 */
	protected IScriptProject getProject(String projectName) {
		for (int i = 0, length = ALL_PROJECTS.length; i < length; i++) {
			if (ALL_PROJECTS[i].getElementName().equals(projectName))
				return ALL_PROJECTS[i];
		}
		return null;
	}

	/**
	 * Returns all compilation units of a given project.
	 * 
	 * @param javaProject
	 *            Project to collect units
	 * @return List of org.eclipse.jdt.core.ISourceModule
	 */
	protected List getProjectSourceModules(IScriptProject javaProject)
			throws ModelException {
		IProjectFragment[] fragmentRoots = javaProject.getProjectFragments();
		int length = fragmentRoots.length;
		List allUnits = new ArrayList();
		for (int i = 0; i < length; i++) {
			if (fragmentRoots[i] instanceof ArchiveProjectFragment)
				continue;
			IModelElement[] packages = fragmentRoots[i].getChildren();
			for (int k = 0; k < packages.length; k++) {
				IScriptFolder pack = (IScriptFolder) packages[k];
				ISourceModule[] units = pack.getSourceModules();
				for (int u = 0; u < units.length; u++) {
					allUnits.add(units[u]);
				}
			}
		}
		return allUnits;
	}

	protected IProjectFragment getExternalJarFile(IScriptProject project,
			String jarSimpleName) throws ModelException {
		IProjectFragment[] roots = project.getProjectFragments();
		if (roots == null || roots.length == 0) {
			return null;
		}
		for (int i = 0; i < roots.length; i++) {
			IProjectFragment root = roots[i];
			if (root.isExternal()
					&& root.getElementName().equals(jarSimpleName)) {
				return root;
			}
		}
		return null;
	}

	/*
	 * Simulate a save/exit of the workspace
	 */
	protected void simulateExit() throws CoreException {
		AbstractModelTests.waitForAutoBuild();
		ResourcesPlugin.getWorkspace().save(true/* full save */, null/*
																		 * no
																		 * progress
																		 */);
		ModelManager.getModelManager().shutdown();
	}

	/*
	 * Simulate a save/exit/restart of the workspace
	 */
	protected void simulateExitRestart() throws CoreException {
		simulateExit();
		simulateRestart();
	}

	/*
	 * Simulate a restart of the workspace
	 */
	protected void simulateRestart() throws CoreException {
		// ModelManager.doNotUse(); // reset the MANAGER singleton
		ModelManager.getModelManager().startup();
		new DLTKCorePreferenceInitializer().initializeDefaultPreferences();
	}

	/**
	 * Split a list of compilation units in several arrays.
	 * 
	 * @param units
	 *            List of org.eclipse.jdt.core.ISourceModule
	 * @param splitSize
	 *            Size of the arrays
	 * @return List of ISourceModule[]
	 */
	protected List splitListInSmallArrays(List units, int splitSize)
			throws ModelException {
		int size = units.size();
		if (size == 0)
			return Collections.EMPTY_LIST;
		int length = size / splitSize;
		int remind = size % splitSize;
		List splitted = new ArrayList(remind == 0 ? length : length + 1);
		if (length == 0) {
			ISourceModule[] sublist = new ISourceModule[size];
			units.toArray(sublist);
			splitted.add(sublist);
			return splitted;
		}
		int ptr = 0;
		for (int i = 0; i < length; i++) {
			ISourceModule[] sublist = new ISourceModule[splitSize];
			units.subList(ptr, ptr + splitSize).toArray(sublist);
			splitted.add(sublist);
			ptr += splitSize;
		}
		if (remind > 0) {
			if (remind < 10) {
				ISourceModule[] lastList = (ISourceModule[]) splitted
						.remove(length - 1);
				System.arraycopy(lastList, 0,
						lastList = new ISourceModule[splitSize + remind], 0,
						splitSize);
				for (int i = ptr, j = splitSize; i < size; i++, j++) {
					lastList[j] = (ISourceModule) units.get(i);
				}
				splitted.add(lastList);
			} else {
				ISourceModule[] sublist = new ISourceModule[remind];
				units.subList(ptr, size).toArray(sublist);
				splitted.add(sublist);
			}
		}
		return splitted;
	}

	/*
	 * Create hashtable of none or all warning options. Possible kind: -1: no
	 * options 0: default options 1: all options
	 */
	protected Hashtable warningOptions(int kind) {

		// Values
		Hashtable optionsMap = DLTKCore.getDefaultOptions();

		// Return created options map
		return optionsMap;
	}
}

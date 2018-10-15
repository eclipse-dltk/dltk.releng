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
package org.eclipse.dltk.tests.performance;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import junit.framework.Test;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.dltk.core.DLTKCore;
import org.eclipse.dltk.core.IScriptModelMarker;
import org.eclipse.dltk.core.IScriptProject;

/**
 * Class to test compiler performance. This includes tests on build, batch
 * compiler, Scanner and Parser.
 */
public class FullSourceWorkspaceBuildTests extends TclFullSourceWorkspaceTests {

	// Tests counters
	private static int TESTS_COUNT = 0;
	private final static int ITERATIONS_COUNT = 10;
	private final static int WARMUP_COUNT = 3;
	// private final static int SCAN_REPEAT = 800; // 800 is default

	// Tests thresholds
	private final static int TIME_THRESHOLD = 150;

	// Log files
	private static PrintStream[] LOG_STREAMS = new PrintStream[DIM_NAMES.length];

	/**
	 * @param name
	 */
	public FullSourceWorkspaceBuildTests(String name) {
		super(name);
	}

	static {
		// TESTS_PREFIX = "testCompile";
		// TESTS_NAMES = new String[] { "testFullBuildProjectNoWarning" };
	}

	public static Test suite() {
		Test suite = buildSuite(testClass());
		TESTS_COUNT = suite.countTestCases();
		createPrintStream(testClass(), LOG_STREAMS, TESTS_COUNT, null);
		return suite;
	}

	private static Class testClass() {
		return FullSourceWorkspaceBuildTests.class;
	}

	/**
	 * Start a build on given project or workspace using given options.
	 * 
	 * @param javaProject
	 *            Project which must be (full) build or null if all workspace
	 *            has to be built.
	 * @param options
	 *            Options used while building
	 */
	void build(final IScriptProject javaProject, Hashtable options,
			boolean noWarning) throws IOException, CoreException {
		if (DEBUG)
			System.out.print("\tstart build...");
		DLTKCore.setOptions(options);
		if (PRINT)
			System.out.println("JavaCore options: " + options);

		// Build workspace if no project
		if (javaProject == null) {
			// single measure
			runGc();
			startMeasuring();
			ENV.fullBuild();
			stopMeasuring();
		} else {
			if (PRINT)
				System.out.println("Project options: "
						+ javaProject.getOptions(false));
			// warm-up
			for (int i = 0; i < WARMUP_COUNT; i++) {
				ENV.fullBuild(javaProject.getProject().getName());
			}

			// measures
			int max = MEASURES_COUNT;
			for (int i = 0; i < max; i++) {
				runGc();
				startMeasuring();
				IWorkspaceRunnable compilation = new IWorkspaceRunnable() {
					public void run(IProgressMonitor monitor)
							throws CoreException {
						ENV.fullBuild(javaProject.getPath());
					}
				};
				IWorkspace workspace = ResourcesPlugin.getWorkspace();
				workspace.run(compilation, null/* don't take any lock */,
						IWorkspace.AVOID_UPDATE, null/*
													 * no progress available
													 * here
													 */);
				stopMeasuring();
			}
		}

		// Verify markers
		IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		IMarker[] markers = workspaceRoot.findMarkers(
				IScriptModelMarker.DLTK_MODEL_PROBLEM_MARKER, true,
				IResource.DEPTH_INFINITE);
		List resources = new ArrayList();
		List messages = new ArrayList();
		int warnings = 0;
		for (int i = 0, length = markers.length; i < length; i++) {
			IMarker marker = markers[i];
			switch (((Integer) marker.getAttribute(IMarker.SEVERITY))
					.intValue()) {
			case IMarker.SEVERITY_ERROR:
				resources.add(marker.getResource().getName());
				messages.add(marker.getAttribute(IMarker.MESSAGE));
				break;
			case IMarker.SEVERITY_WARNING:
				warnings++;
				if (noWarning) {
					resources.add(marker.getResource().getName());
					messages.add(marker.getAttribute(IMarker.MESSAGE));
				}
				break;
			}
		}
		workspaceRoot.deleteMarkers(
				IScriptModelMarker.DLTK_MODEL_PROBLEM_MARKER, true,
				IResource.DEPTH_INFINITE);

		// Assert result
		int size = messages.size();
		if (size > 0) {
			StringBuffer debugBuffer = new StringBuffer();
			for (int i = 0; i < size; i++) {
				debugBuffer.append(resources.get(i));
				debugBuffer.append(":\n\t");
				debugBuffer.append(messages.get(i));
				debugBuffer.append('\n');
			}
			System.out.println(this.scenarioShortName
					+ ": Unexpected ERROR marker(s):\n"
					+ debugBuffer.toString());
			System.out.println("--------------------");
		}
		if (DEBUG)
			System.out.println("done");

		// Commit measure
		commitMeasurements();
		assertPerformance();

		// Store warning
		if (warnings > 0) {
			System.out.println("\t- " + warnings
					+ " warnings found while performing build.");
		}
		if (this.scenarioComment == null) {
			this.scenarioComment = new StringBuffer("[" + TEST_POSITION + "]");
		} else {
			this.scenarioComment.append(' ');
		}
		this.scenarioComment.append("warn=");
		this.scenarioComment.append(warnings);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {

		// End of execution => one test less
		TESTS_COUNT--;

		// Log perf result
		if (LOG_DIR != null) {
			logPerfResult(LOG_STREAMS, TESTS_COUNT);
		}

		// Call super at the end as it close print streams
		super.tearDown();
	}

	/**
	 * Full build with JavaCore default options.
	 * 
	 * WARNING: This test must be and _ever_ stay at first position as it build
	 * the entire workspace. It also cannot be removed as it's a Global
	 * fingerprint! Move it would have great consequence on all other tests
	 * results...
	 * 
	 * @throws CoreException
	 * @throws IOException
	 */
	public void testFullBuildDefault() throws CoreException, IOException {
		tagAsSummary("Build entire workspace", true); // do NOT put in
		// fingerprint
		build(null, warningOptions(0/* default warnings */), false);
	}
}

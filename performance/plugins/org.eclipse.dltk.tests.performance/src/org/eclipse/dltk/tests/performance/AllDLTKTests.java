/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
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
import java.io.OutputStream;
import java.io.PrintStream;

import junit.framework.Test;
import junit.textui.TestRunner;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.dltk.core.ModelException;
import org.eclipse.dltk.core.tests.model.AbstractModelTests;

/**
 */
public class AllDLTKTests extends TclFullSourceWorkspaceTests {

	// Tests counter
	private static int TESTS_COUNT = 0;
	private int RUNS = 2;
	int nodesCount = 0;

	// Log files
	private static PrintStream[] LOG_STREAMS = new PrintStream[DIM_NAMES.length];

	/**
	 * @param name
	 */
	public AllDLTKTests(String name) {
		super(name);
	}

	static {
		// TESTS_PREFIX = "testDomAstCreationJLS2";
	}

	public static Test suite() {
		Test suite = buildSuite(testClass());
		TESTS_COUNT = suite.countTestCases();

		createPrintStream(testClass(), LOG_STREAMS, TESTS_COUNT, null);
		return suite;
	}

	private static Class testClass() {
		return AllDLTKTests.class;
	}

	protected void setUp() throws Exception {
		WORKSPACE_REQUIRED = false;
		AbstractModelTests.waitUntilIndexesReady();
		super.setUp();
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

		// Print statistics
		if (TESTS_COUNT == 0) {
			System.out.println("-------------------------------------");
			// System.out.println(
			// "DOM/AST creation performance test statistics:"
			// );
			// NumberFormat intFormat = NumberFormat.getIntegerInstance();
			// System.out.println("  - " + intFormat.format(this.nodesCount)
			// + " nodes have been parsed.");
			// System.out.println("-------------------------------------\n");
		}

		removeProjects();
		// Call super at the end as it close print streams
		super.tearDown();
	}

	private TestRunner mkRunner() {
		// We need to clear workspace before running each new test
		removeProjects();
		TestRunner runner = new TestRunner(new PrintStream(new OutputStream() {
			public void write(int b) throws IOException {
			}
		}));
		return runner;
	}

	private void removeProjects() {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject[] projects = root.getProjects();
		for (int i = 0; i < projects.length; i++) {
			try {
				projects[i].delete(true, new NullProgressMonitor());
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
	}

	public void testCoreTests() throws ModelException {
		tagAsSummary("DLTK Core tests", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(org.eclipse.dltk.core.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}

	public void testDebugTests() throws ModelException {
		tagAsSummary("DLTK Debug tests", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(org.eclipse.dltk.debug.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}

	public void testUITests() throws ModelException {
		tagAsSummary("DLTK UI tests", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(org.eclipse.dltk.ui.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}

	public void testJavaScriptCoreTests() throws ModelException {
		tagAsSummary("DLTK JavaScript Core tests", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(
					org.eclipse.dltk.javascript.core.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}

	public void testPythonTests() throws ModelException {
		tagAsSummary("DLTK Python Core tests", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(org.eclipse.dltk.python.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}

	public void testPythonUI() throws ModelException {
		tagAsSummary("DLTK Python UI tests", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(org.eclipse.dltk.python.ui.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}

	public void testRubyCoreTests() throws ModelException {
		tagAsSummary("DLTK Ruby Core tests", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(org.eclipse.dltk.ruby.core.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}

	public void testRubyDebugTests() throws ModelException {
		tagAsSummary("DLTK Ruby Debug tests", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(org.eclipse.dltk.ruby.debug.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}

	public void testRubyFormatterTests() throws ModelException {
		tagAsSummary("DLTK Ruby Formatter tests", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(
					org.eclipse.dltk.ruby.formatter.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}

	public void testRubyUITests() throws ModelException {
		tagAsSummary("DLTK Ruby UI", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(org.eclipse.dltk.ruby.ui.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}

	public void testTclCoreTests() throws ModelException {
		tagAsSummary("DLTK Tcl Core tests", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(org.eclipse.dltk.tcl.core.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}

	public void testTclUITests() throws ModelException {
		tagAsSummary("DLTK Tcl UI tests", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(org.eclipse.dltk.tcl.ui.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}

	public void testXOTclTests() throws ModelException {
		tagAsSummary("DLTK XOTcl tests", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(org.eclipse.dltk.xotcl.core.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}

	public void testTclParserTests() throws ModelException {
		tagAsSummary("DLTK TCL Parser Core tests", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(org.eclipse.dltk.tcl.parser.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}

	public void testTclValidatorsTests() throws ModelException {
		tagAsSummary("DLTK TCL Validator tests", true);
		startMeasuring();
		for (int i = 0; i < RUNS; i++) {
			mkRunner().run(
					org.eclipse.dltk.tcl.validators.tests.AllTests.suite());
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
	}
}

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

import java.io.PrintStream;
import java.text.NumberFormat;

import junit.framework.Test;

import org.eclipse.dltk.core.CompletionProposal;
import org.eclipse.dltk.core.CompletionRequestor;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.core.ModelException;
import org.eclipse.dltk.core.SourceParserUtil;
import org.eclipse.dltk.core.tests.model.AbstractModelTests;

/**
 */
public class FullSourceWorkspaceCompletionTests extends
		TclFullSourceWorkspaceTests {

	// Counters
	private static final int WARMUP_COUNT = 10;
	private static final int ITERATION_COUNT = 40;
	static int[] PROPOSAL_COUNTS;
	static int TESTS_COUNT = 0;
	static int TESTS_LENGTH;
	static int COMPLETIONS_COUNT = 0;

	// Log files
	private static PrintStream[] LOG_STREAMS = new PrintStream[DIM_NAMES.length];

	public FullSourceWorkspaceCompletionTests(String name) {
		super(name);
	}

	public static Test suite() {

		Test suite = buildSuite(testClass());
		TESTS_LENGTH = TESTS_COUNT = suite.countTestCases();
		PROPOSAL_COUNTS = new int[TESTS_COUNT];
		createPrintStream(testClass(), LOG_STREAMS, TESTS_COUNT, "Complete");
		return suite;
	}

	private static Class testClass() {
		return FullSourceWorkspaceCompletionTests.class;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.jdt.core.tests.performance.FullSourceWorkspaceTests#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		COMPLETIONS_COUNT = 0;
		AbstractModelTests.waitUntilIndexesReady();
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
			System.out.println("Completion performance test statistics:");
			NumberFormat intFormat = NumberFormat.getIntegerInstance();
			System.out.println("  - " + intFormat.format(COMPLETIONS_COUNT)
					+ " completions have been performed");
			System.out.println("  - following proposals have been done:");
			for (int i = 0; i < TESTS_LENGTH; i++) {
				System.out.println("  	+ test " + i + ": "
						+ intFormat.format(PROPOSAL_COUNTS[i]) + " proposals");
			}
			System.out.println("-------------------------------------\n");
		}

		// Call super at the end as it close print streams
		super.tearDown();
	}

	class TestCompletionRequestor extends CompletionRequestor {
		public void accept(CompletionProposal proposal) {
			PROPOSAL_COUNTS[TESTS_LENGTH - TESTS_COUNT]++;
		}
	}

	private void complete(String projectName, String packageName,
			String unitName, String completeAt, String completeBehind,
			int warmupCount, int iterationCount) throws ModelException {
		complete(projectName, packageName, unitName, completeAt,
				completeBehind, null, warmupCount, iterationCount);
	}

	private void complete(String projectName, String packageName,
			String unitName, String completeAt, String completeBehind,
			int[] ignoredKinds, int warmupCount, int iterationCount)
			throws ModelException {

		AbstractModelTests.waitUntilIndexesReady();

		TestCompletionRequestor requestor = new TestCompletionRequestor();
		if (ignoredKinds != null) {
			for (int i = 0; i < ignoredKinds.length; i++) {
				requestor.setIgnored(ignoredKinds[i], true);
			}
		}

		ISourceModule unit = getSourceModule(projectName, packageName, unitName);

		String str = unit.getSource();
		int completionIndex = str.indexOf(completeAt) + completeBehind.length();

		if (DEBUG)
			System.out.print("Perform code assist inside " + unitName + "...");

		// Warm up
		if (warmupCount > 0) {
			unit.codeComplete(completionIndex, requestor);
			for (int i = 1; i < warmupCount; i++) {
				unit.codeComplete(completionIndex, requestor);
			}
		}

		// Clean memory
		runGc();

		// Measures
		for (int i = 0; i < MEASURES_COUNT; i++) {
			startMeasuring();
			for (int j = 0; j < iterationCount; j++) {
				unit.codeComplete(completionIndex, requestor);
				COMPLETIONS_COUNT++;
			}
			stopMeasuring();
		}
		if (DEBUG)
			System.out.println("done!");

		// Commit
		commitMeasurements();
		assertPerformance();
	}

	public void testPerfComplete001() throws ModelException {
		tagAsSummary("Tcl completion test", true);
		complete("ats", "ats_easy/bin", "aem", "tcl:", "t", WARMUP_COUNT,
				ITERATION_COUNT);
	}

	public void testPerfComplete001b() throws ModelException {
		SourceParserUtil.clearCache();
		tagAsSummary("Tcl completion test 2", true);
		complete("ats", "ats_easy/bin", "aem", "tcl:", "t", WARMUP_COUNT,
				ITERATION_COUNT);
	}

	public void testPerfComplete002() throws ModelException {
		complete("ats", "ats_easy/bin", "aem", "tcl:", "t", WARMUP_COUNT,
				ITERATION_COUNT);
	}

	public void testPerfComplete002b() throws ModelException {
		SourceParserUtil.clearCache();
		SourceParserUtil.disableCache();
		complete("ats", "ats_easy/bin", "aem", "tcl:", "tc", WARMUP_COUNT,
				ITERATION_COUNT);
	}

	public void testPerfComplete003() throws ModelException {
		complete("ats", "ats_easy/bin", "aem", "tcl:", "t", WARMUP_COUNT,
				ITERATION_COUNT);
	}

	public void testPerfComplete003b() throws ModelException {
		SourceParserUtil.enableCache();
		complete("ats", "ats_easy/bin", "aem", "tcl:", "t", WARMUP_COUNT,
				ITERATION_COUNT);
	}

	public void testPerfComplete004() throws ModelException {
		complete("ats", "ats_easy/bin", "aem", "tcl:", "tcl:", WARMUP_COUNT,
				ITERATION_COUNT);
	}

	public void testPerfComplete004b() throws ModelException {
		complete("ats", "ats_easy/bin", "aem", "tcl:", "tcl:", WARMUP_COUNT,
				ITERATION_COUNT);
	}
}

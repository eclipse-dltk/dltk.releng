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
import java.util.List;

import junit.framework.Test;
import junit.framework.TestCase;

import org.eclipse.dltk.ast.ASTNode;
import org.eclipse.dltk.ast.declarations.ModuleDeclaration;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.core.ModelException;
import org.eclipse.dltk.core.SourceParserUtil;
import org.eclipse.dltk.core.tests.model.AbstractModelTests;

/**
 */
public class FullSourceWorkspaceASTTests extends TclFullSourceWorkspaceTests {

	// Tests counter
	private static int TESTS_COUNT = 0;
	private final static int ITERATIONS_COUNT = 10;
	int nodesCount = 0;

	// Log files
	private static PrintStream[] LOG_STREAMS = new PrintStream[DIM_NAMES.length];

	/**
	 * @param name
	 */
	public FullSourceWorkspaceASTTests(String name) {
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
		return FullSourceWorkspaceASTTests.class;
	}

	protected void setUp() throws Exception {
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
			// System.out.println("DOM/AST creation performance test statistics:"
			// );
			// NumberFormat intFormat = NumberFormat.getIntegerInstance();
			// System.out.println("  - " + intFormat.format(this.nodesCount)
			// + " nodes have been parsed.");
			// System.out.println("-------------------------------------\n");
		}

		// Call super at the end as it close print streams
		super.tearDown();
	}

	/*
	 * Create AST nodes tree for all compilation units of all projects
	 */
	private int runAllProjectsAstCreation(int astLevel) throws ModelException {
		int unitsCount = 0;
		startMeasuring();
		if (DEBUG)
			System.out
					.println("Creating AST hierarchy for all units of projects:");
		for (int i = 0; i < ALL_PROJECTS.length; i++) {
			// Get project compilation units
			if (DEBUG)
				System.out.print("\t- " + ALL_PROJECTS[i].getElementName());
			List units = getProjectSourceModules(ALL_PROJECTS[i]);
			int size = units.size();
			if (size == 0) {
				if (DEBUG)
					System.out.println(": empty!");
				continue;
			}
			unitsCount += size;
			List unitsArrays = splitListInSmallArrays(units, 20);
			int n = unitsArrays.size();
			if (DEBUG)
				if (n == 1)
					System.out.print(": " + size + " units to proceed (" + n
							+ " step): ");
				else
					System.out.print(": " + size + " units to proceed (" + n
							+ " steps): ");
			while (unitsArrays.size() > 0) {
				ISourceModule[] unitsArray = (ISourceModule[]) unitsArrays
						.remove(0);
				if (DEBUG)
					System.out.print('.');
				int length = unitsArray.length;
				ModuleDeclaration[] compilationUnits = new ModuleDeclaration[length];
				// Create AST tree
				for (int ptr = 0; ptr < length; ptr++) {
					ISourceModule unit = unitsArray[ptr];
					unitsArray[ptr] = null; // release memory handle
					ASTNode result = SourceParserUtil
							.getModuleDeclaration(unit);
					TestCase.assertNotNull(result);
					compilationUnits[ptr] = (ModuleDeclaration) result;
				}
			}
			if (DEBUG)
				System.out.println(" done!");
		}
		stopMeasuring();
		commitMeasurements();
		assertPerformance();
		return unitsCount;
	}

	public void testBuildAllAST() throws ModelException {
		SourceParserUtil.disableCache();
		tagAsSummary("AST tree without cache for workspace files", true);
		runAllProjectsAstCreation(0);
		SourceParserUtil.enableCache();
	}

	public void testBuildAllASTWithCache() throws ModelException {
		tagAsSummary("AST tree with cache for workspace files", true);
		runAllProjectsAstCreation(0);
	}

	public void testBuildAllASTFromCache() throws ModelException {
		tagAsSummary("AST from cache for workspace files", true);
		runAllProjectsAstCreation(0);
	}
}

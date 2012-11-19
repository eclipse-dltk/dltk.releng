/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 
 *******************************************************************************/
package org.eclipse.dltk.tests.all;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.Bundle;

public class AllTests {

	public static Test suite() {
		TestSuite suite = new TestSuite("DLTK tests"); //$NON-NLS-1$
		suite.addTestSuite(PrepareWorkbenchTest.class);
		suite.addTest(createCore());
		suite.addTest(createJavaScript());
		suite.addTest(createTCL());
		suite.addTest(createRuby());
		suite.addTest(createPython());
		return suite;
	}

	private static TestSuite createCore() {
		TestSuite suite = new TestSuite("Core"); //$NON-NLS-1$
		suite.addTest(org.eclipse.dltk.core.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.debug.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.ui.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.validators.core.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.formatter.tests.AllTests.suite());
		return suite;
	}

	private static TestSuite createPython() {
		TestSuite suite = new TestSuite("Python"); //$NON-NLS-1$
		suite.addTest(org.eclipse.dltk.python.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.python.ui.tests.AllTests.suite());
		return suite;
	}

	private static TestSuite createRuby() {
		TestSuite suite = new TestSuite("Ruby"); //$NON-NLS-1$
		suite.addTest(org.eclipse.dltk.ruby.core.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.ruby.debug.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.ruby.ui.tests.AllTests.suite());
		// load class from the o.e.d.ruby.formatter.tests fragment
		suite.addTest(loadClass("org.eclipse.dltk.ruby.formatter.tests", //$NON-NLS-1$
				"org.eclipse.dltk.ruby.formatter.tests.AllTests")); //$NON-NLS-1$
		// load class from the o.e.d.ruby.testing.tests fragment
		suite.addTest(loadClass("org.eclipse.dltk.ruby.testing", //$NON-NLS-1$
				"org.eclipse.dltk.ruby.testing.tests.AllTests")); //$NON-NLS-1$
		return suite;
	}

	private static TestSuite createTCL() {
		TestSuite suite = new TestSuite("TCL"); //$NON-NLS-1$
		suite.addTest(org.eclipse.dltk.tcl.core.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.tcl.parser.tests.AllTestsRunner.suite());
		suite.addTest(org.eclipse.dltk.tcl.validators.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.tcl.ui.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.xotcl.core.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.itcl.core.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.tcl.activestatedebugger.tests.AllTests
				.suite());
		suite.addTest(org.eclipse.dltk.tcl.formatter.tests.AllTests.suite());
		return suite;
	}

	private static TestSuite createJavaScript() {
		TestSuite suite = new TestSuite("JavaScript"); //$NON-NLS-1$
		suite.addTest(org.eclipse.dltk.javascript.core.tests.AllTests.suite());
		suite
				.addTest(org.eclipse.dltk.javascript.parser.tests.AllTests
						.suite());
		suite.addTest(org.eclipse.dltk.javascript.formatter.tests.AllTests
				.suite());
		return suite;
	}

	/**
	 * Loads the specified class and calls suite() method
	 * 
	 * @return
	 */
	private static Test loadClass(final String pluginName, String className) {
		try {
			final Bundle bundle = Platform.getBundle(pluginName);
			if (bundle == null) {
				return new TestCase(className) {
					@Override
					protected void runTest() throws Throwable {
						fail(NLS.bind("Bundle {0} is not found", pluginName)); //$NON-NLS-1$
					}
				};
			}
			final Class<?> clazz = bundle.loadClass(className);
			final Method suiteMethod = clazz.getMethod("suite", new Class[0]); //$NON-NLS-1$
			if (Modifier.isStatic(suiteMethod.getModifiers())
					&& Modifier.isPublic(suiteMethod.getModifiers())) {
				return (Test) suiteMethod.invoke(null, (Object[]) null);
			} else {
				return new TestCase(className) {
					@Override
					protected void runTest() throws Throwable {
						fail("suite() method is not public and static"); //$NON-NLS-1$
					}
				};
			}
		} catch (final Exception e) {
			return new TestCase(className) {
				@Override
				protected void runTest() throws Throwable {
					throw e;
				}
			};
		}
	}
}

/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 
 *******************************************************************************/
package org.eclipse.dltk.tests.all;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllTests {

	public static Test suite() {
		TestSuite suite = new TestSuite("Test all DLTK"); //$NON-NLS-1$
		// $JUnit-BEGIN$
		suite.addTest(org.eclipse.dltk.core.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.debug.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.ui.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.validators.core.tests.AllTests.suite());

		suite.addTest(org.eclipse.dltk.ruby.core.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.ruby.debug.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.ruby.ui.tests.AllTests.suite());

		suite.addTest(org.eclipse.dltk.tcl.core.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.tcl.ui.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.xotcl.core.tests.AllTests.suite());
		suite.addTest(org.eclipse.dltk.itcl.core.tests.AllTests.suite());
		// $JUnit-END$
		return suite;
	}

}

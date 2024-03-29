/*******************************************************************************
 * Copyright (c) 2004, 2005 IBM Corporation and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.test.internal.performance.tests;

import junit.framework.Test;
import junit.framework.TestSuite;


public class AllTests {

	public static Test suite() {
		TestSuite suite= new TestSuite("Performance Test plugin tests"); //$NON-NLS-1$
		
		//suite.addTestSuite(SimplePerformanceMeterTest.class);
		suite.addTestSuite(VariationsTests.class);
		suite.addTestSuite(DBTests.class);
		suite.addTestSuite(PerformanceMeterFactoryTest.class);
		
		return suite;
	}
}

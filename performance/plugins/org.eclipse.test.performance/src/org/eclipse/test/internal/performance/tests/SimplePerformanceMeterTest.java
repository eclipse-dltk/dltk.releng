/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
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

import junit.framework.TestCase;

import org.eclipse.test.internal.performance.OSPerformanceMeter;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;

public class SimplePerformanceMeterTest extends TestCase {
	
    public void testPerformanceMeterFactory() {
		PerformanceMeter meter= Performance.getDefault().createPerformanceMeter("scenarioId"); //$NON-NLS-1$
		
		assertTrue(meter instanceof OSPerformanceMeter);
	
		meter.start();
		meter.stop();
		
		meter.commit();
		
		meter.dispose();
	}

}

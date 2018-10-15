/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.dltk.core.tests.performance.util;

import org.eclipse.test.internal.performance.PerformanceMeterFactory;
import org.eclipse.test.performance.PerformanceMeter;


public class DLTKCorePerformanceMeterFactory extends PerformanceMeterFactory {
	protected PerformanceMeter doCreatePerformanceMeter(String scenario) {
		return new DLTKCorePerformanceMeter(scenario);
	}
}

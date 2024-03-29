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

package org.eclipse.test.internal.performance.eval;

import org.eclipse.test.performance.PerformanceMeter;

/**
 * The empty evaluator. Does nothing.
 */
public class EmptyEvaluator implements IEvaluator {

	/*
	 * @see org.eclipse.test.internal.performance.eval.IEvaluator#evaluate(org.eclipse.jdt.ui.tests.performance.PerformanceMeter)
	 */
	public void evaluate(PerformanceMeter performanceMeter) throws RuntimeException {
		// empty
	}

	/*
	 * @see org.eclipse.test.internal.performance.eval.IEvaluator#setAssertCheckers(org.eclipse.test.internal.performance.eval.AssertChecker[])
	 */
	public void setAssertCheckers(AssertChecker[] asserts) {
		// empty
	}

	/*
	 * @see org.eclipse.test.internal.performance.eval.IEvaluator#setReferenceFilterProperties(java.lang.String, java.lang.String)
	 */
	public void setReferenceFilterProperties(String driver, String timestamp) {
		// empty
	}
}

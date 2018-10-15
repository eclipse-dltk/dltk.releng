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
package org.eclipse.test.internal.performance.data;


/**
 * @since 3.1
 */
public class Scalar {
	private Dim fDimension;
	private long fMagnitude;
	
	public Scalar(Dim dimension, long extent) {
		fDimension= dimension;
		fMagnitude= extent;
	}
	
	public Dim getDimension() {
		return fDimension;
	}
	
	public long getMagnitude() {
		return fMagnitude;
	}
	
	public String toString() {
		if (fDimension == null)
			return "Scalar [dimension= " + fDimension + ", magnitude= " + fMagnitude + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return "Scalar [" + fDimension.getName() + ": " + fDimension.getDisplayValue(this) + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}

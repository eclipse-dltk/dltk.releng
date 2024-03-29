/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.dltk.core.tests;

import java.io.PrintWriter;
import java.io.StringWriter;

public class NullPrintWriter extends PrintWriter {

public NullPrintWriter() {
	super(new StringWriter());
}

public void flush() {
	// do nothing
}

public void write(char[] buf, int off, int len) {
	// do nothing
}

public void write(int c) {
	// do nothing
}

public void write(String s, int off, int len) {
	// do nothing
}

}

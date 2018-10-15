/*******************************************************************************
 * Copyright (c) 2008 xored software, Inc.  
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0  
 *
 * Contributors:
 *     xored software, Inc. - initial API and Implementation (Andrei Sobolev)
 *******************************************************************************/
package org.eclipse.dltk.tests.performance;

import org.eclipse.dltk.core.tests.FullSourceWorkspaceTests;

public abstract class TclFullSourceWorkspaceTests extends
		FullSourceWorkspaceTests {
	public TclFullSourceWorkspaceTests(String name) {
		super(name);
		AllPerformanceTests.init();
	}

	public String getFullWorkspaceZip() {
		// return getPluginDirectoryPath(Activator.PLUGIN_ID + ".data")
		// + File.separator + "bigFile.zip";
		String property = System.getProperty("BUILD_HOME", "/home/dltk");
		return property + "/data/bigFile.zip";
	}
}

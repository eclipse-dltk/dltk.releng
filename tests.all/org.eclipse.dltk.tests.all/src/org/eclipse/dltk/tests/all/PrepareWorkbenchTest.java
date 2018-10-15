/*******************************************************************************
 * Copyright (c) 2012 NumberFour AG
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     NumberFour AG - initial API and Implementation (Alex Panchenko)
 *******************************************************************************/
package org.eclipse.dltk.tests.all;

import junit.framework.TestCase;

import org.eclipse.dltk.ui.tests.UITestUtils;

public class PrepareWorkbenchTest extends TestCase {

    public void testCloseIntro() {
        UITestUtils.closeIntro();
    }

}

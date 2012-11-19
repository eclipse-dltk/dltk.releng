/*******************************************************************************
 * Copyright (c) 2012 NumberFour AG
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     NumberFour AG - initial API and Implementation (Alex Panchenko)
 *******************************************************************************/
package org.eclipse.dltk.tests.all;

import junit.framework.TestCase;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.intro.IIntroManager;
import org.eclipse.ui.intro.IIntroPart;

public class PrepareWorkbenchTest extends TestCase {

    public void testCloseIntro() {
        final IIntroManager manager = PlatformUI.getWorkbench().getIntroManager();
        final IIntroPart part = manager.getIntro();
        if (part != null) {
            manager.closeIntro(part);
        }
    }

}

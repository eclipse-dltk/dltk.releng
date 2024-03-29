/*
 * Copyright (c) 2005 IBM Corporation and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - initial API and implementation
 *
 * $Id: BuildUtil.java,v 1.3 2007/02/22 08:40:26 asobolev Exp $
 */
package org.eclipse.dltk.common.build;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @version 1.1.0
 */
public class BuildUtil {

	public static String readFile(File file) {
		StringBuffer stringBuffer = new StringBuffer();

		try {
			BufferedReader in = new BufferedReader(new FileReader(file));

			try {
				char[] buffer = new char[1024];
				int size = 0;
				while ((size = in.read(buffer)) > -1) {
					stringBuffer.append(buffer, 0, size);
				}
			} finally {
				if (in != null) {
					in.close();
				}
			}
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
		return stringBuffer.toString();
	}

	public static void writeFile(File file, String content)
			throws IOException {
		if (!file.getParentFile().isDirectory()) {
			file.getParentFile().mkdirs();
		}

		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter(file));
			out.write(content);
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

}

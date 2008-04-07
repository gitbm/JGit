/*
 *  Copyright (C) 2008  Shawn Pearce <spearce@spearce.org>
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public
 *  License, version 2, as published by the Free Software Foundation.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 */
package org.spearce.jgit.util;

import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;

class FS_Win32 extends FS {
	static boolean detect() {
		final String osDotName = AccessController
				.doPrivileged(new PrivilegedAction<String>() {
					public String run() {
						return System.getProperty("os.name");
					}
				});
		return osDotName != null
				&& osDotName.toLowerCase().indexOf("windows") != -1;
	}

	public boolean supportsExecute() {
		return false;
	}

	public boolean canExecute(final File f) {
		return false;
	}

	public boolean setExecute(final File f, final boolean canExec) {
		return false;
	}
}
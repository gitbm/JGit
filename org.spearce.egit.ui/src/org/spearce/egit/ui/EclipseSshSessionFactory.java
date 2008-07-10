/*******************************************************************************
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * See LICENSE for the full license text, also available.
 *******************************************************************************/
package org.spearce.egit.ui;

import java.io.IOException;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.eclipse.jsch.core.IJSchService;
import org.eclipse.jsch.ui.UserInfoPrompter;
import org.spearce.jgit.transport.SshSessionFactory;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

class EclipseSshSessionFactory extends SshSessionFactory {
	private final IJSchService provider;

	EclipseSshSessionFactory(final IJSchService p) {
		provider = p;
	}

	@Override
	public Session getSession(final String user, final String pass,
			final String host, final int port) throws JSchException {
		final Session session = provider.createSession(host, port > 0 ? port
				: -1, user != null ? user : userName());
		if (pass != null)
			session.setPassword(pass);
		else
			new UserInfoPrompter(session);
		return session;
	}

	private static String userName() {
		return AccessController.doPrivileged(new PrivilegedAction<String>() {
			public String run() {
				return System.getProperty("user.name");
			}
		});
	}

	@Override
	public OutputStream getErrorStream() {
		return new OutputStream() {

			StringBuilder all = new StringBuilder();
			StringBuilder sb = new StringBuilder();

			public String toString() {
				String r = all.toString();
				while (r.endsWith("\n"))
					r = r.substring(0, r.length() - 1);
				return r;
			}

			@Override
			public void write(int b) throws IOException {
				if (b == '\r')
					return;
				sb.append((char) b);
				if (b == '\n') {
					String s = sb.toString();
					all.append(s);
					sb = new StringBuilder();
					Activator.logError(s, new Throwable());
				}
			}
		};
	}

}
/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.help.internal.base;
import java.io.*;
import java.lang.reflect.*;
import java.nio.channels.*;
import java.util.*;

import org.eclipse.core.runtime.*;
import org.eclipse.help.internal.appserver.*;
import org.osgi.framework.*;

/**
 * Help application.
 * Starts webserver and help web application for use
 * by infocenter and stand-alone help.
 * Application takes a parameter "mode", that can take values:
 *  "infocenter" - when help system should run as infocenter,
 *  "standalone" - when help system should run as standalone.
 */
public class HelpApplication
	implements IPlatformRunnable, IExecutableExtension {
	private static final String APPLICATION_LOCK_FILE = ".applicationlock";
	private static final int STATUS_EXITTING = 0;
	private static final int STATUS_RESTARTING = 2;
	private static final int STATUS_RUNNING = 1;
	private static int status = STATUS_RUNNING;
	private File metadata;
	private FileLock lock;
	/**
	 * Causes help service to stop and exit
	 */
	public static void stop() {
		status = STATUS_EXITTING;
	}
	/**
	 * Causes help service to exit and start again
	 */
	public static void restart() {
		if (status != STATUS_EXITTING) {
			status = STATUS_RESTARTING;
		}
	}
	/**
	 * Runs help service application.
	 */
	public Object run(Object args) throws Exception {
		if (status == STATUS_RESTARTING) {
			return EXIT_RESTART;
		}

		metadata = new File(Platform.getLocation().toFile(), ".metadata/");
		if (!BaseHelpSystem.ensureWebappRunning()) {
			System.out.println(
				"Help web application could not start.  Check log file for details.");
			return EXIT_OK;
		}

		if (status == STATUS_RESTARTING) {
			return EXIT_RESTART;

		}
		writeHostAndPort();
		obtainLock();
		
		if (BaseHelpSystem.MODE_STANDALONE == BaseHelpSystem.getMode()) {
			//try running UI loop if possible
			runUI();
		}
		//run a headless loop;
		while (status == STATUS_RUNNING) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException ie) {
				break;
			}
		}
		releaseLock();
		if (status == STATUS_RESTARTING) {
			return EXIT_RESTART;
		} else {
			return EXIT_OK;
		}
	}
	private void runUI(){
		try {
			Bundle bundle = Platform.getBundle("org.eclipse.help.ui");
			if(bundle == null){
				return;
			}
			Class c = bundle.loadClass("org.eclipse.help.ui.internal.HelpUIEventLoop");
			Object o = c.newInstance();
			Method m=c.getMethod("run", new Class[]{} );
			m.invoke(null, new Object[]{});
		} catch (Exception e) {
		}
	}
	/**
	 * @see IExecutableExtension
	 */
	public void setInitializationData(
		IConfigurationElement configElement,
		String propertyName,
		Object data) {
		String value = (String) ((Map) data).get("mode");
		if ("infocenter".equalsIgnoreCase(value)) {
			BaseHelpSystem.setMode(BaseHelpSystem.MODE_INFOCENTER);
		} else if ("standalone".equalsIgnoreCase(value)) {
			BaseHelpSystem.setMode(BaseHelpSystem.MODE_STANDALONE);
		}
	}
	private void writeHostAndPort() throws IOException {
		Properties p = new Properties();
		p.put("host", WebappManager.getHost());
		p.put("port", "" + WebappManager.getPort());

		File hostPortFile = new File(metadata, ".connection");
		hostPortFile.deleteOnExit();
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(hostPortFile);
			p.store(out, null);
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException ioe2) {
				}
			}
		}

	}
	private void obtainLock() {
		File lockFile = new File(metadata, APPLICATION_LOCK_FILE);
		try {
			RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
			lock = raf.getChannel().lock();
		} catch (IOException ioe) {
			lock = null;
		}
	}
	private void releaseLock() {
		if (lock != null) {
			try {
				lock.channel().close();
			} catch (IOException ioe) {
			}
		}
	}
	public static boolean isRunning(){
		return status==STATUS_RUNNING;
	}
}

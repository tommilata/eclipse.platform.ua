/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Common Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.ui.internal.intro.impl.util;



public class StringUtil {

    public static String concat(String string1, String string2,
            String string3) {
        StringBuffer buffer = new StringBuffer(string1);
        buffer.append(string2);
        buffer.append(string3);
        return buffer.toString();
    }

    public static String concat(String string1, String string2,
            String string3, String string4) {
        StringBuffer buffer = new StringBuffer(string1);
        buffer.append(string2);
        buffer.append(string3);
        buffer.append(string4);
        return buffer.toString();
    }

    public static String concat(String string1, String string2,
            String string3, String string4, String string5) {
        StringBuffer buffer = new StringBuffer(string1);
        buffer.append(string2);
        buffer.append(string3);
        buffer.append(string4);
        buffer.append(string5);
        return buffer.toString();
    }

    public static String concat(String string1, String string2,
            String string3, String string4, String string5, String string6) {
        StringBuffer buffer = new StringBuffer(string1);
        buffer.append(string2);
        buffer.append(string3);
        buffer.append(string4);
        buffer.append(string5);
        buffer.append(string6);
        return buffer.toString();
    }



}
/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.intro.config;

import org.eclipse.swt.widgets.*;
import org.eclipse.ui.forms.widgets.*;
import org.eclipse.ui.intro.*;

/**
 * An Intro standby part. It is a UI component that represents some standby
 * content. Standby parts can be contributed to the Eclipse intro using the
 * following extension point:
 * 
 * <pre>
 * <p>
 *      &lt;extension
 *             point=&quot;org.eclipse.ui.intro.configExtension&quot;&gt;
 *          &lt;standbyPart
 *                pluginId=&quot;org.eclipse.ui.intro&quot;
 *                class=&quot;org.eclipse.ui.internal.intro.impl.parts.ContextHelpStandbyPart&quot;
 *                id=&quot;org.eclipse.ui.intro.contextHelp&quot;&gt;
 *          &lt;/standbyPart&gt; 
 *       &lt;/extension&gt;
 * </pre>
 * 
 * TODO: should be renamed to IStandbyPart.
 * 
 * </p>
 * 
 * @since 3.0
 */
public interface IStandbyContentPart {

    /**
     * Creates the SWT controls for this standby part.
     * <p>
     * Clients should not call this method. The intro framework calls this
     * method when it needs to.
     * 
     * @param parent
     *            the parent control
     * @param toolkit
     *            the form toolkit being used by the IIntroPart implementation
     */
    public void createPartControl(Composite parent, FormToolkit toolkit);

    /**
     * Returns the primary control associated with this standby part.
     * 
     * @return the SWT control which displays this standby part's content, or
     *         <code>null</code> if this standby part's controls have not yet
     *         been created.
     */
    public Control getControl();

    /**
     * Initializes this standby part with the introPart.
     * <p>
     * This method is automatically called by the intro framework shortly after
     * part construction. It marks the start of the standby part's lifecycle.
     * Clients must not call this method.
     * </p>
     * 
     * @param introPart
     *            the intro part
     */
    public void init(IIntroPart introPart);

    /**
     * Sets the input to show in this standby part.
     * 
     * @param input
     *            the input object to be used by this standby part.
     */
    public void setInput(Object input);

    /**
     * Asks this standby part to take focus.
     * <p>
     * Clients should not call this method (the intro framework calls this
     * method at appropriate times).
     * </p>
     */
    public void setFocus();

    /**
     * Disposes of this standby part.
     * <p>
     * Clients should not call this method. The intro framework calls this
     * method when the Customizable IntroPart is closed.
     * </p>
     */
    public void dispose();
}
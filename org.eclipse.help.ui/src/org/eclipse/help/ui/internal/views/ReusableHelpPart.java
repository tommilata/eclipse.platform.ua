/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.help.ui.internal.views;

import java.util.ArrayList;

import org.eclipse.help.*;
import org.eclipse.help.internal.appserver.WebappManager;
import org.eclipse.help.internal.base.BaseHelpSystem;
import org.eclipse.help.ui.internal.*;
import org.eclipse.jface.action.*;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.forms.*;
import org.eclipse.ui.forms.widgets.*;
import org.eclipse.ui.help.WorkbenchHelp;

public class ReusableHelpPart implements IHelpUIConstants {
	public static final int ALL_TOPICS = 1 << 1;
	public static final int CONTEXT_HELP = 1 << 2;
	public static final int SEARCH = 1 << 3;
	private ManagedForm mform;
	private int verticalSpacing = 15;
	private int bmargin = 5;
	private String defaultContextHelpText;

	private ArrayList pages;
	private Action backAction;
	private Action nextAction;
	private CopyAction copyAction;
	private Action openInfoCenterAction;
	private OpenHrefAction openAction;
	private OpenHrefAction openInHelpAction;
	private ReusableHelpPartHistory history;
	private HelpPartPage currentPage;
	private int style;
	private boolean showDocumentsInPlace=false;
	private int numberOfInPlaceHits =8;

	private IRunnableContext runnableContext;

	private IToolBarManager toolBarManager;
	private IStatusLineManager statusLineManager;
	
	private abstract class BusyRunAction extends Action {
		public BusyRunAction(String name) {
			super(name);
		}
		public void run() {
			BusyIndicator.showWhile(getControl().getDisplay(), 
					new Runnable() {
				public void run() {
					busyRun();
				}
			});
		}
		protected abstract void busyRun();
	}
	
	private abstract class OpenHrefAction extends BusyRunAction {
		private Object target;
		public OpenHrefAction(String name) {
			super(name);
		}
		public void setTarget(Object target) {
			this.target = target;
		}
		public Object getTarget() {
			return target;
		}
	}
	
	private class CopyAction extends Action {
		private FormText target;
		public CopyAction() {
			super("copy"); //$NON-NLS-1$
		}
		public void setTarget(FormText target) {
			this.target = target;
			setEnabled(target.canCopy());
		}
		public void run() {
			if (target!=null)
				target.copy();
		}
	}

	private static class PartRec {
		String id;
		boolean flexible;
		boolean grabVertical;
		IHelpPart part;

		PartRec(String id, boolean flexible, boolean grabVertical) {
			this.id = id;
			this.flexible = flexible;
			this.grabVertical = grabVertical;
		}
	}

	private class HelpPartPage {
		private String id;
		private int vspacing = verticalSpacing;
		private int horizontalMargin = 0;

		private String text;
		private SubToolBarManager toolBarManager;
		private ArrayList partRecs;
		private int nflexible;

		public HelpPartPage(String id, String text) {
			this.id = id;
			this.text = text;
			partRecs = new ArrayList();
			toolBarManager = new SubToolBarManager(ReusableHelpPart.this.toolBarManager);
		}
		public void dispose() {
			toolBarManager.disposeManager();
			partRecs = null;
		}
		public void setVerticalSpacing(int value) {
			this.vspacing = value;
		}
		public int getVerticalSpacing() {
			return vspacing;
		}
		public void setHorizontalMargin(int value) {
			this.horizontalMargin = value;
		}
		public int getHorizontalMargin() {
			return horizontalMargin;
		}
		public IToolBarManager getToolBarManager() {
			return toolBarManager;
		}

		public String getId() {
			return id;
		}
		public String getText() {
			return text;
		}
		
		public void addPart(String id, boolean flexible) {
			addPart(id, flexible, false);
		}

		public void addPart(String id, boolean flexible, boolean grabVertical) {
			partRecs.add(new PartRec(id, flexible, grabVertical));
			if (flexible)
				nflexible++;
		}

		public PartRec[] getParts() {
			return (PartRec[]) partRecs.toArray(new PartRec[partRecs.size()]);
		}
		
		public int getNumberOfFlexibleParts() {
			return nflexible;
			
		}

		public void setVisible(boolean visible) {
			for (int i = 0; i < partRecs.size(); i++) {
				PartRec rec = (PartRec) partRecs.get(i);
				if (visible) {
					if (rec.part == null)
						rec.part = createPart(rec.id);
				}
				rec.part.setVisible(visible);
				toolBarManager.setVisible(visible);
			}
		}
		public IHelpPart findPart(String id) {
			for (int i = 0; i < partRecs.size(); i++) {
				PartRec rec = (PartRec) partRecs.get(i);
				if (rec.id.equals(id))
					return rec.part;
			}
			return null;
		}
		public void setFocus() {
			if (partRecs.size()==0) return;
			PartRec rec = (PartRec)partRecs.get(0);
			rec.part.setFocus();
		}
	}

	class HelpPartLayout extends Layout implements ILayoutExtension {
		public int computeMaximumWidth(Composite parent, boolean changed) {
			return computeSize(parent, SWT.DEFAULT, SWT.DEFAULT, changed).x;
		}

		public int computeMinimumWidth(Composite parent, boolean changed) {
			return computeSize(parent, 0, SWT.DEFAULT, changed).x;
		}

		protected Point computeSize(Composite composite, int wHint, int hHint,
				boolean flushCache) {
			if (currentPage==null)
				return new Point(0, 0);
			PartRec[] parts = currentPage.getParts();
			int hmargin = currentPage.getHorizontalMargin();
			int innerWhint = wHint!=SWT.DEFAULT?wHint-2*hmargin:wHint;
			Point result = new Point(0, 0);
			for (int i=0; i<parts.length; i++) {
				PartRec partRec = parts[i];
				if (!partRec.flexible) {
					Control c = partRec.part.getControl();
					Point size = c.computeSize(innerWhint, SWT.DEFAULT, flushCache);
					result.x = Math.max(result.x, size.x);
					result.y += size.y;
				}
				if (i<parts.length-1)
					result.y += currentPage.getVerticalSpacing();								
			}
			result.x += hmargin * 2;
			result.y += bmargin; 
			return result;
		}

		protected void layout(Composite composite, boolean flushCache) {
			if (currentPage==null) 
				return;
			
			Rectangle clientArea = composite.getClientArea();

			PartRec[] parts = currentPage.getParts();
			int hmargin = currentPage.getHorizontalMargin();			
			int nfixedParts = parts.length - currentPage.getNumberOfFlexibleParts();
			Point [] fixedSizes = new Point[nfixedParts];
			int fixedHeight = 0;
			int index = 0;
			int innerWidth = clientArea.width-hmargin*2;
			for (int i=0; i<parts.length; i++) {
				PartRec partRec = parts[i];
				if (!partRec.flexible) {
					Control c = partRec.part.getControl();
					Point size = c.computeSize(innerWidth, SWT.DEFAULT, false);
					fixedSizes[index++] = size;
					if (!partRec.grabVertical)
						fixedHeight += size.y;
				}
				if (i<parts.length-1)
					fixedHeight += currentPage.getVerticalSpacing();				
			}
			fixedHeight += bmargin;
			int flexHeight = clientArea.height - fixedHeight;
			int flexPortion = 0;
			if (currentPage.getNumberOfFlexibleParts()>0)
				flexPortion = flexHeight/currentPage.getNumberOfFlexibleParts();

			int usedFlexHeight = 0;
			int y = 0;
			index = 0;
			int nflexParts = 0;
			for (int i=0; i<parts.length; i++) {
				PartRec partRec = parts[i];
				Control c = partRec.part.getControl();
				
				if (partRec.flexible) {
					int height;
					if (++nflexParts == currentPage.getNumberOfFlexibleParts())
						height = flexHeight-usedFlexHeight;
					else {
						height = flexPortion;
						usedFlexHeight += height;
					}
					c.setBounds(0, y, clientArea.width, height);
				}
				else {
					Point fixedSize = fixedSizes[index++];
					if (fixedSize.y<flexHeight && partRec.grabVertical)
						c.setBounds(hmargin, y, innerWidth, flexHeight);
					else
						c.setBounds(hmargin, y, innerWidth, fixedSize.y);
				}
				if (i<parts.length-1)
					y += c.getSize().y + currentPage.getVerticalSpacing();
			}
		}
	}

	public ReusableHelpPart(IRunnableContext runnableContext) {
		this(runnableContext, CONTEXT_HELP|SEARCH|ALL_TOPICS);
	}
	public ReusableHelpPart(IRunnableContext runnableContext, int style) {
		this.runnableContext = runnableContext;
		history = new ReusableHelpPartHistory();
		this.style = style;
	}

	private void definePages() {
		pages = new ArrayList();
		// federated search page
		HelpPartPage page = new HelpPartPage(HV_FSEARCH_PAGE, HelpUIResources.getString("ReusableHelpPart.searchPage.name")); //$NON-NLS-1$
		page.addPart(HV_FSEARCH, false, true);
		page.addPart(HV_SEE_ALSO, false);
		pages.add(page);
		// all topics page
		page = new HelpPartPage(HV_ALL_TOPICS_PAGE, HelpUIResources.getString("ReusableHelpPart.allTopicsPage.name")); //$NON-NLS-1$
		page.setVerticalSpacing(0);
		page.setHorizontalMargin(0);		
		page.addPart(HV_TOPIC_TREE, true);
		page.addPart(HV_SEE_ALSO, false);
		pages.add(page);
		// browser page
		page = new HelpPartPage(HV_BROWSER_PAGE, null);
		page.setVerticalSpacing(0);
		page.addPart(HV_BROWSER, true);
		page.addPart(HV_SEE_ALSO, false);
		pages.add(page);
		// context help page
		page = new HelpPartPage(HV_CONTEXT_HELP_PAGE, HelpUIResources.getString("ReusableHelpPart.contextHelpPage.name")); //$NON-NLS-1$
		page.addPart(HV_CONTEXT_HELP, false);
		page.addPart(HV_SEARCH, false);
		page.addPart(HV_SEARCH_RESULT, false, true);
		page.addPart(HV_SEE_ALSO, false);
		pages.add(page);
	}

	public void init(IToolBarManager toolBarManager, IStatusLineManager statusLineManager) {
		this.toolBarManager = toolBarManager;
		this.statusLineManager = statusLineManager;
		makeActions();
		definePages();
	}
	private void makeActions() {
		backAction = new Action("back") { //$NON-NLS-1$
			public void run() {
				doBack();
			}
		};
		backAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_BACK));
		backAction.setDisabledImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_BACK_DISABLED));
		backAction.setEnabled(false);
		backAction.setText(HelpUIResources.getString("ReusableHelpPart.back.label")); //$NON-NLS-1$
		backAction.setToolTipText(HelpUIResources.getString("ReusableHelpPart.back.tooltip")); //$NON-NLS-1$
		
		nextAction = new Action("next") { //$NON-NLS-1$
			public void run() {
				doNext();
			}
		};
		nextAction.setText(HelpUIResources.getString("ReusableHelpPart.forward.label")); //$NON-NLS-1$
		nextAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_FORWARD));
		nextAction.setDisabledImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_FORWARD_DISABLED));
		nextAction.setEnabled(false);
		nextAction.setToolTipText(HelpUIResources.getString("ReusableHelpPart.forward.tooltip")); //$NON-NLS-1$
		toolBarManager.add(backAction);
		toolBarManager.add(nextAction);
		
		openInfoCenterAction = new BusyRunAction("openInfoCenter") { //$NON-NLS-1$
			protected void busyRun() {
				WorkbenchHelp.displayHelp();
			}
		};
		openInfoCenterAction.setText(HelpUIResources.getString("ReusableHelpPart.openInfoCenterAction.label")); //$NON-NLS-1$
		openAction = new OpenHrefAction("open") { //$NON-NLS-1$
			protected void busyRun() {
				doOpen(getTarget(), false);
			}
		};
		openAction.setText(HelpUIResources.getString("ReusableHelpPart.openAction.label")); //$NON-NLS-1$
		openInHelpAction = new OpenHrefAction(HelpUIResources.getString("ReusableHelpPart.openInHelpAction.label")) { //$NON-NLS-1$
			protected void busyRun() {
				doOpenInHelp(getTarget());
			}
		};
		openInHelpAction.setText(HelpUIResources.getString("ReusableHelpPart.openInHelpContentsAction.label")); //$NON-NLS-1$
		copyAction = new CopyAction();
		copyAction.setText(HelpUIResources.getString("ReusableHelpPart.copyAction.label")); //$NON-NLS-1$
	}
	
	private void doBack() {
		HistoryEntry entry = history.prev();
		if (entry!=null)
			executeHistoryEntry(entry);
	}
	private void doNext() {
		HistoryEntry entry = history.next();
		if (entry!=null)
			executeHistoryEntry(entry);
	}

	private void executeHistoryEntry(HistoryEntry entry) {
		history.setBlocked(true);
		if (entry.getType()==HistoryEntry.PAGE)
			showPage(entry.getData());
		else if (entry.getType()==HistoryEntry.URL)
			showURL(entry.getData());
	}

	public void createControl(Composite parent, FormToolkit toolkit) {
		ScrolledForm form = toolkit.createScrolledForm(parent);
		form.getBody().setLayout(new HelpPartLayout());
		mform = new ManagedForm(toolkit, form);
		MenuManager manager = new MenuManager();
		IMenuListener listener = new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				contextMenuAboutToShow(manager);
			}
		};
		manager.setRemoveAllWhenShown(true);
		manager.addMenuListener(listener);
		Menu contextMenu = manager.createContextMenu(form.getForm());
		form.getForm().setMenu(contextMenu);
	}

	public HelpPartPage showPage(String id) {
		if (currentPage!=null && currentPage.getId().equals(id))
			return currentPage;
		for (int i=0; i<pages.size(); i++) {
			HelpPartPage page = (HelpPartPage)pages.get(i);
			if (page.getId().equals(id)) {
				flipPages(currentPage, page);
				return page;
			}
		}
		return null;
	}
	public HelpPartPage showPage(String id, boolean setFocus) {
		HelpPartPage page = this.showPage(id);
		if (page!=null && setFocus)
			page.setFocus();
		return page;
	}

	private void flipPages(HelpPartPage oldPage, HelpPartPage newPage) {
		if (oldPage!=null)
			oldPage.setVisible(false);
		mform.getForm().setText(newPage.getText());			
		newPage.setVisible(true);
		currentPage = newPage;		
		mform.getForm().getBody().layout(true);
		mform.reflow(true);
		if (newPage.getId().equals(IHelpUIConstants.HV_BROWSER_PAGE)==false) {
			if (!history.isBlocked()) {
				history.addEntry(new HistoryEntry(HistoryEntry.PAGE, newPage.getId()));
			}
			updateNavigation();
		}
	}
	public HelpPartPage getCurrentPage() {
		return currentPage;
	}

	void browserChanged(String url) {
		if (!history.isBlocked()) {
			history.addEntry(new HistoryEntry(HistoryEntry.URL, url));
		}
		updateNavigation();
	}
	
	private void updateNavigation() {
		backAction.setEnabled(history.hasPrev());
		nextAction.setEnabled(history.hasNext());
		history.setBlocked(false);
	}

	public boolean isMonitoringContextHelp() {
		return currentPage!=null && currentPage.getId().equals(HV_CONTEXT_HELP_PAGE);
	}

	public Control getControl() {
		return mform.getForm();
	}
	
	public ManagedForm getForm() {
		return mform;
	}
	public void reflow() {
		mform.getForm().getBody().layout();
		mform.reflow(true);
	}

	public void dispose() {
		for (int i=0; i<pages.size(); i++) {
			HelpPartPage page = (HelpPartPage)pages.get(i);
			page.dispose();
		}
		pages = null;
		if (mform != null) {
			mform.dispose();
			mform = null;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.intro.impl.parts.IStandbyContentPart#setFocus()
	 */
	public void setFocus() {
		if (currentPage!=null)
			currentPage.setFocus();
		else
			mform.setFocus();
	}

	public void update(IWorkbenchPart part, Control control) {
		mform.setInput(new ContextHelpProviderInput(null, control, part));
	}
	
	public void update(IContextProvider provider, IWorkbenchPart part, Control control) {
		mform.setInput(new ContextHelpProviderInput(provider, control, part));
	}

	private IHelpPart createPart(String id) {
		IHelpPart part = null;
		Composite parent = mform.getForm().getBody();
		
		part = findPart(id);
		if (part!=null)
			return part;

		if (id.equals(HV_SEARCH)) {
			part = new SearchPart(parent, mform.getToolkit());
		} else if (id.equals(HV_TOPIC_TREE)) {
			part = new AllTopicsPart(parent, mform.getToolkit());
		} else if (id.equals(HV_CONTEXT_HELP)) {
			part = new ContextHelpPart(parent, mform.getToolkit());
			((ContextHelpPart)part).setDefaultText(getDefaultContextHelpText());
		} else if (id.equals(HV_BROWSER)) {
			part = new BrowserPart(parent, mform.getToolkit());
		} else if (id.equals(HV_SEARCH_RESULT)) {
			part = new SearchResultsPart(parent, mform.getToolkit());
		} else if (id.equals(HV_SEE_ALSO)) {
			part = new SeeAlsoPart(parent, mform.getToolkit());
		} else if (id.equals(HV_FSEARCH)) {
			part = new FederatedSearchPart(parent, mform.getToolkit());
		}
		if (part != null) {
			part.init(this, id);
			part.initialize(mform);
			mform.addPart(part);
		}
		return part;
	}
	
	/**
	 * @return Returns the runnableContext.
	 */
	public IRunnableContext getRunnableContext() {
		return runnableContext;
	}
	public boolean isInWorkbenchWindow() {
		return runnableContext instanceof IWorkbenchWindow;
	}
	/**
	 * @return Returns the defaultContextHelpText.
	 */
	public String getDefaultContextHelpText() {
		return defaultContextHelpText;
	}
	/**
	 * @param defaultContextHelpText The defaultContextHelpText to set.
	 */
	public void setDefaultContextHelpText(String defaultContextHelpText) {
		this.defaultContextHelpText = defaultContextHelpText;
	}

	public void showURL(String url) {
		showURL(url, getShowDocumentsInPlace());
	}

	public void showURL(String url, boolean replace) {
		if (url==null) return;
		if (replace) {
			showPage(IHelpUIConstants.HV_BROWSER_PAGE);
			BrowserPart bpart = (BrowserPart)findPart(IHelpUIConstants.HV_BROWSER);
			if (bpart!=null) {
				bpart.showURL(toAbsoluteURL(url));
			}
		}
		else {
			//try {
				//URL fullURL = new URL(toAbsoluteURL(url));
				//WebBrowser.openURL(fullURL, 0, "org.eclipse.help");
				
			//}
			//catch (MalformedURLException e) {
			//}
			WorkbenchHelp.displayHelpResource(url);
		}
	}
	public IHelpPart findPart(String id) {
		if (mform==null) return null;
		IFormPart [] parts = (IFormPart[])mform.getParts();
		for (int i=0; i<parts.length; i++) {
			IHelpPart part = (IHelpPart)parts[i];
			if (part.getId().equals(id))
				return part;
		}
		return null;
	}
	private String toAbsoluteURL(String url) {
		if (url==null || url.indexOf("://")!= -1) //$NON-NLS-1$
			return url;
		BaseHelpSystem.ensureWebappRunning();
		String base = "http://" //$NON-NLS-1$
				+ WebappManager.getHost() + ":" //$NON-NLS-1$
				+ WebappManager.getPort() + "/help/nftopic"; //$NON-NLS-1$
		return base + url;
		//char sep = url.lastIndexOf('?')!= -1 ? '&':'?';
		//return base + url+sep+"noframes=true"; //$NON-NLS-1$
	}

	private void contextMenuAboutToShow(IMenuManager manager) {
		IFormPart [] parts = mform.getParts();
		boolean hasContext=false;
		Control focusControl = getControl().getDisplay().getFocusControl();
		for (int i=0; i<parts.length; i++) {
			IHelpPart part = (IHelpPart)parts[i];
			if (part.hasFocusControl(focusControl)) {
				hasContext = part.fillContextMenu(manager);
				break;
			}
		}
		if (hasContext)
			manager.add(new Separator());
		manager.add(backAction);
		manager.add(nextAction);
		manager.add(new Separator());
		manager.add(openInfoCenterAction);
	}
	boolean fillSelectionProviderMenu(ISelectionProvider provider, IMenuManager manager) {
		fillOpenActions(provider, manager);
		return true;
	}
	private void fillOpenActions(Object target, IMenuManager manager) {
		openAction.setTarget(target);
		openInHelpAction.setTarget(target);
		manager.add(openAction);
		manager.add(openInHelpAction);
	}
	boolean fillFormContextMenu(FormText text, IMenuManager manager) {
		fillOpenActions(text, manager);
		manager.add(new Separator());
		manager.add(copyAction);
		copyAction.setTarget(text);
		return true;
	}
	private String getHref(Object target) {
		if (target instanceof ISelectionProvider) {
			ISelectionProvider provider = (ISelectionProvider)target;
			IStructuredSelection ssel = (IStructuredSelection)provider.getSelection();
			Object obj = ssel.getFirstElement();
			if (obj instanceof ITopic) {
				ITopic topic = (ITopic)obj;
				return topic.getHref();
			}
		}
		else if (target instanceof FormText) {
			FormText text = (FormText)target;
			Object href = text.getSelectedLinkHref();
			if (href!=null)
				return href.toString();
		}
		return null;
	}

	private void doOpen(Object target) {
		String href = getHref(target);
		if (href!=null)
			showURL(href, getShowDocumentsInPlace());		
	}
	
	private void doOpen(Object target, boolean replace) {
		String href = getHref(target);
		if (href!=null)
			showURL(href, replace);
	}

	private void doOpenInHelp(Object target) {
		String href = getHref(target);
		if (href!=null)
			//WorkbenchHelp.displayHelpResource(href);
			showURL(href, false);
	}
	/**
	 * @return Returns the statusLineManager.
	 */
	public IStatusLineManager getStatusLineManager() {
		return statusLineManager;
	}
	/**
	 * @return Returns the showDocumentsInPlace.
	 */
	public boolean getShowDocumentsInPlace() {
		return showDocumentsInPlace;
	}
	/**
	 * @param showDocumentsInPlace The showDocumentsInPlace to set.
	 */
	public void setShowDocumentsInPlace(boolean showDocumentsInPlace) {
		this.showDocumentsInPlace = showDocumentsInPlace;
	}
	/**
	 * @return Returns the style.
	 */
	public int getStyle() {
		return style;
	}
	public int getNumberOfInPlaceHits() {
		return numberOfInPlaceHits;
	}
	public void setNumberOfInPlaceHits(int numberOfInPlaceHits) {
		this.numberOfInPlaceHits = numberOfInPlaceHits;
	}
}
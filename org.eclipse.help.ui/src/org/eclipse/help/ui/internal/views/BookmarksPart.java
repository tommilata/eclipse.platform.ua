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

import java.util.*;
import java.util.Observer;

import org.eclipse.help.IHelpResource;
import org.eclipse.help.internal.base.*;
import org.eclipse.help.ui.internal.*;
import org.eclipse.jface.action.*;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.forms.widgets.FormToolkit;

public class BookmarksPart extends HyperlinkTreePart implements IHelpPart,
		Observer {
	private Action deleteAction;

	class BookmarksProvider implements ITreeContentProvider {
		public Object[] getChildren(Object parentElement) {
			if (parentElement == BookmarksPart.this)
				return new Object[] { BaseHelpSystem.getBookmarkManager() };
			if (parentElement instanceof BookmarkManager)
				return ((BookmarkManager) parentElement).getBookmarks();
			return new Object[0];
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.jface.viewers.ITreeContentProvider#getParent(java.lang.Object)
		 */
		public Object getParent(Object element) {
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.jface.viewers.ITreeContentProvider#hasChildren(java.lang.Object)
		 */
		public boolean hasChildren(Object element) {
			return getChildren(element).length > 0;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
		 */
		public Object[] getElements(Object inputElement) {
			return getChildren(inputElement);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
		 */
		public void dispose() {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer,
		 *      java.lang.Object, java.lang.Object)
		 */
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}
	}

	class BookmarksLabelProvider extends LabelProvider {
		public String getText(Object obj) {
			if (obj instanceof BookmarkManager)
				return HelpUIResources.getString("BookmarksPart.savedTopics"); //$NON-NLS-1$
			if (obj instanceof IHelpResource)
				return ((IHelpResource) obj).getLabel();
			return super.getText(obj);
		}

		public Image getImage(Object obj) {
			if (obj instanceof BookmarkManager)
				return HelpUIResources
						.getImage(IHelpUIConstants.IMAGE_BOOKMARKS);
			if (obj instanceof IHelpResource)
				return HelpUIResources
						.getImage(IHelpUIConstants.IMAGE_BOOKMARK);
			return super.getImage(obj);
		}
	}

	/**
	 * @param parent
	 * @param toolkit
	 * @param style
	 */
	public BookmarksPart(Composite parent, final FormToolkit toolkit,
			IToolBarManager tbm) {
		super(parent, toolkit, tbm);
		BaseHelpSystem.getBookmarkManager().addObserver(this);
	}

	public void dispose() {
		BaseHelpSystem.getBookmarkManager().deleteObserver(this);
		super.dispose();
	}

	protected void configureTreeViewer() {
		treeViewer.setContentProvider(new BookmarksProvider());
		treeViewer.setLabelProvider(new BookmarksLabelProvider());
		treeViewer.setAutoExpandLevel(TreeViewer.ALL_LEVELS);
		deleteAction = new Action("") { //$NON-NLS-1$
			public void run() {
				Object obj = ((IStructuredSelection)treeViewer.getSelection()).getFirstElement();
				if (obj instanceof BookmarkManager.Bookmark) {
					BookmarkManager.Bookmark b = (BookmarkManager.Bookmark)obj;
					BaseHelpSystem.getBookmarkManager().removeBookmark(b);
				}
			}
		};
		deleteAction.setText(HelpUIResources.getString("BookmarksPart.delete")); //$NON-NLS-1$
		deleteAction.setEnabled(false);
	}
	
	protected void handleSelectionChanged(IStructuredSelection sel) {
		Object obj = sel.getFirstElement();
		deleteAction.setEnabled(obj!=null && obj instanceof BookmarkManager.Bookmark);
		super.handleSelectionChanged(sel);
	}

	public boolean fillContextMenu(IMenuManager manager) {
		boolean value = super.fillContextMenu(manager);
		ISelection selection = treeViewer.getSelection();
		boolean canDeleteAll=false;
		int count = BaseHelpSystem.getBookmarkManager().getBookmarks().length;
		canDeleteAll = count>0;

		if (canDelete((IStructuredSelection) selection)) {
			if (value)
				manager.add(new Separator());
			manager.add(deleteAction);
			value=true;
		}
		if (canDeleteAll) {
			Action action = new Action("") { //$NON-NLS-1$
				public void run() {
					BusyIndicator.showWhile(getControl().getDisplay(), new Runnable() {
						public void run() {
							BaseHelpSystem.getBookmarkManager().removeAllBookmarks();
						}
					});
				}
			};
			action.setText(HelpUIResources.getString("BookmarksPart.deleteAll")); //$NON-NLS-1$
			manager.add(action);
			value=true;
		}
		if (value==true)
			manager.add(new Separator());
		return value;
	}

	private boolean canDelete(IStructuredSelection ssel) {
		Object obj = ssel.getFirstElement();
		return obj instanceof BookmarkManager.Bookmark;
	}

	protected void doOpen(Object obj) {
		if (obj instanceof BookmarkManager) {
			treeViewer.setExpandedState(obj, !treeViewer.getExpandedState(obj));
		} else if (obj instanceof IHelpResource) {
			IHelpResource res = (IHelpResource) obj;
			if (res.getHref() != null)
				parent.showURL(res.getHref());
		}
	}
	
	public void update(final Observable o, final Object arg) {
		treeViewer.getControl().getDisplay().asyncExec(new Runnable() {
			public void run() {
				asyncUpdate(o, arg);
			}
		});
	}

	private void asyncUpdate(Observable o, Object arg) {
		BookmarkManager.BookmarkEvent event = (BookmarkManager.BookmarkEvent) arg;
		switch (event.getType()) {
		case BookmarkManager.ADD:
			treeViewer.add(BaseHelpSystem.getBookmarkManager(), event
					.getBookmark());
			break;
		case BookmarkManager.REMOVE:
			treeViewer.remove(event.getBookmark());
			break;
		case BookmarkManager.REMOVE_ALL:
		case BookmarkManager.WORLD_CHANGED:
			treeViewer.refresh();
			break;
		}
	}
	public IAction getGlobalAction(String id) {
		if (id.equals(ActionFactory.DELETE.getId()))
			return deleteAction;
		return super.getGlobalAction(id);
	}
}
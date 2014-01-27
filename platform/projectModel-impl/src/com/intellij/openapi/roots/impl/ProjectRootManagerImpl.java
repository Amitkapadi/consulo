/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectExtension;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.mustbe.consulo.roots.ContentFolderScopes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author max
 */
public class ProjectRootManagerImpl extends ProjectRootManagerEx implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectRootManagerImpl");

  protected final Project myProject;

  private long myModificationCount = 0;
  @NonNls private static final String ATTRIBUTE_VERSION = "version";

  private final OrderRootsCache myRootsCache;

  protected boolean myStartupActivityPerformed = false;

  protected class BatchSession {
    private int myBatchLevel = 0;
    private boolean myChanged = false;

    private final boolean myFileTypes;

    private BatchSession(final boolean fileTypes) {
      myFileTypes = fileTypes;
    }

    protected void levelUp() {
      if (myBatchLevel == 0) {
        myChanged = false;
      }
      myBatchLevel += 1;
    }

    protected void levelDown() {
      myBatchLevel -= 1;
      if (myChanged && myBatchLevel == 0) {
        try {
          fireChange();
        }
        finally {
          myChanged = false;
        }
      }
    }

    private boolean fireChange() {
      return fireRootsChanged(myFileTypes);
    }

    protected void beforeRootsChanged() {
      if (myBatchLevel == 0 || !myChanged) {
        if (fireBeforeRootsChanged(myFileTypes)) {
          myChanged = true;
        }
      }
    }

    protected void rootsChanged() {
      if (myBatchLevel == 0) {
        if (fireChange()) {
          myChanged = false;
        }
      }
    }
  }

  protected final BatchSession myRootsChanged = new BatchSession(false);
  protected final BatchSession myFileTypesChanged = new BatchSession(true);

  public static ProjectRootManagerImpl getInstanceImpl(Project project) {
    return (ProjectRootManagerImpl)getInstance(project);
  }

  public ProjectRootManagerImpl(Project project) {
    myProject = project;
    myRootsCache = new OrderRootsCache(project);
  }

  @Override
  @NotNull
  public ProjectFileIndex getFileIndex() {
    return ProjectFileIndex.SERVICE.getInstance(myProject);
  }

  @Override
  @NotNull
  public List<String> getContentRootUrls() {
    final List<String> result = new ArrayList<String>();
    for (Module module : getModuleManager().getModules()) {
      final String[] urls = ModuleRootManager.getInstance(module).getContentRootUrls();
      ContainerUtil.addAll(result, urls);
    }
    return result;
  }

  @Override
  @NotNull
  public VirtualFile[] getContentRoots() {
    final List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Module module : getModuleManager().getModules()) {
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      ContainerUtil.addAll(result, contentRoots);
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public VirtualFile[] getContentSourceRoots() {
    final List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Module module : getModuleManager().getModules()) {
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getContentFolderFiles(ContentFolderScopes.all(false));
      ContainerUtil.addAll(result, sourceRoots);
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @NotNull
  @Override
  public OrderEnumerator orderEntries() {
    return new ProjectOrderEnumerator(myProject, myRootsCache);
  }

  @NotNull
  @Override
  public OrderEnumerator orderEntries(@NotNull Collection<? extends Module> modules) {
    return new ModulesOrderEnumerator(myProject, modules);
  }

  @Override
  public VirtualFile[] getContentRootsFromAllModules() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    final Module[] modules = getModuleManager().getSortedModules();
    for (Module module : modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      ContainerUtil.addAll(result, files);
    }
    result.add(myProject.getBaseDir());
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "ProjectRootManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    for (ProjectExtension extension : Extensions.getExtensions(ProjectExtension.EP_NAME, myProject)) {
      extension.readExternal(element);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(ATTRIBUTE_VERSION, "2");
    for (ProjectExtension extension : Extensions.getExtensions(ProjectExtension.EP_NAME, myProject)) {
      extension.writeExternal(element);
    }
  }

  private boolean myMergedCallStarted = false;
  private boolean myMergedCallHasRootChange = false;
  private int myRootsChangesDepth = 0;

  @Override
  public void mergeRootsChangesDuring(@NotNull Runnable runnable) {
    if (getBatchSession(false).myBatchLevel == 0 && !myMergedCallStarted) {
      LOG.assertTrue(myRootsChangesDepth == 0,
                     "Merged rootsChanged not allowed inside rootsChanged, rootsChanged level == " + myRootsChangesDepth);
      myMergedCallStarted = true;
      myMergedCallHasRootChange = false;
      try {
        runnable.run();
      }
      finally {
        if (myMergedCallHasRootChange) {
          LOG.assertTrue(myRootsChangesDepth == 1, "myMergedCallDepth = " + myRootsChangesDepth);
          getBatchSession(false).rootsChanged();
        }
        myMergedCallStarted = false;
        myMergedCallHasRootChange = false;
      }
    }
    else {
      runnable.run();
    }
  }

  protected void clearScopesCaches() {
    clearScopesCachesForModules();
  }

  @Override
  public void clearScopesCachesForModules() {
    myRootsCache.clearCache();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      ((ModuleRootManagerImpl)ModuleRootManager.getInstance(module)).dropCaches();
    }
  }

  @Override
  public void makeRootsChange(@NotNull Runnable runnable, boolean filetypes, boolean fireEvents) {
    if (myProject.isDisposed()) return;
    BatchSession session = getBatchSession(filetypes);
    if (fireEvents) session.beforeRootsChanged();
    try {
      runnable.run();
    }
    finally {
      if (fireEvents) session.rootsChanged();
    }
  }

  protected BatchSession getBatchSession(final boolean filetypes) {
    return filetypes ? myFileTypesChanged : myRootsChanged;
  }

  protected boolean isFiringEvent = false;

  private boolean fireBeforeRootsChanged(boolean filetypes) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    LOG.assertTrue(!isFiringEvent, "Do not use API that changes roots from roots events. Try using invoke later or something else.");

    if (myMergedCallStarted) {
      LOG.assertTrue(!filetypes, "Filetypes change is not supported inside merged call");
    }

    if (myRootsChangesDepth++ == 0) {
      if (myMergedCallStarted) {
        myMergedCallHasRootChange = true;
        myRootsChangesDepth++; // blocks all firing until finishRootsChangedOnDemand
      }
      fireBeforeRootsChangeEvent(filetypes);
      return true;
    }

    return false;
  }

  protected void fireBeforeRootsChangeEvent(boolean filetypes) {
  }

  private boolean fireRootsChanged(boolean filetypes) {
    if (myProject.isDisposed()) return false;

    ApplicationManager.getApplication().assertWriteAccessAllowed();

    LOG.assertTrue(!isFiringEvent, "Do not use API that changes roots from roots events. Try using invoke later or something else.");

    if (myMergedCallStarted) {
      LOG.assertTrue(!filetypes, "Filetypes change is not supported inside merged call");
    }

    myRootsChangesDepth--;
    if (myRootsChangesDepth > 0) return false;

    clearScopesCaches();

    myModificationCount++;

    PsiManager psiManager = PsiManager.getInstance(myProject);
    psiManager.dropResolveCaches();
    ((PsiModificationTrackerImpl)psiManager.getModificationTracker()).incCounter();

    fireRootsChangedEvent(filetypes);

    doSynchronizeRoots();

    addRootsToWatch();

    return true;
  }

  protected void fireRootsChangedEvent(boolean filetypes) {
  }

  protected void addRootsToWatch() {
  }

  public Project getProject() {
    return myProject;
  }

  protected void doSynchronizeRoots() {
  }

  public static String extractLocalPath(final String url) {
    final String path = VfsUtilCore.urlToPath(url);
    final int jarSeparatorIndex = path.indexOf(ArchiveFileSystem.ARCHIVE_SEPARATOR);
    if (jarSeparatorIndex > 0) {
      return path.substring(0, jarSeparatorIndex);
    }
    return path;
  }

  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(myProject);
  }

  void addListenerForTable(LibraryTable.Listener libraryListener,
                           final LibraryTable libraryTable) {
    LibraryTableMultilistener multilistener = myLibraryTableMultilisteners.get(libraryTable);
    if (multilistener == null) {
      multilistener = new LibraryTableMultilistener(libraryTable);
    }
    multilistener.addListener(libraryListener);
  }

  void removeListenerForTable(LibraryTable.Listener libraryListener,
                              final LibraryTable libraryTable) {
    LibraryTableMultilistener multilistener = myLibraryTableMultilisteners.get(libraryTable);
    if (multilistener == null) {
      multilistener = new LibraryTableMultilistener(libraryTable);
    }
    multilistener.removeListener(libraryListener);
  }

  private final Map<LibraryTable, LibraryTableMultilistener> myLibraryTableMultilisteners
    = new HashMap<LibraryTable, LibraryTableMultilistener>();

  private class LibraryTableMultilistener implements LibraryTable.Listener {
    final List<LibraryTable.Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
    private final LibraryTable myLibraryTable;

    private LibraryTableMultilistener(LibraryTable libraryTable) {
      myLibraryTable = libraryTable;
      myLibraryTable.addListener(this);
      myLibraryTableMultilisteners.put(myLibraryTable, this);
    }

    private void addListener(LibraryTable.Listener listener) {
      myListeners.add(listener);
    }

    private void removeListener(LibraryTable.Listener listener) {
      myListeners.remove(listener);
      if (myListeners.isEmpty()) {
        myLibraryTable.removeListener(this);
        myLibraryTableMultilisteners.remove(myLibraryTable);
      }
    }

    @Override
    public void afterLibraryAdded(final Library newLibrary) {
      myModificationCount++;
      mergeRootsChangesDuring(new Runnable() {
        @Override
        public void run() {
          for (LibraryTable.Listener listener : myListeners) {
            listener.afterLibraryAdded(newLibrary);
          }
        }
      });
    }

    @Override
    public void afterLibraryRenamed(final Library library) {
      myModificationCount++;
      mergeRootsChangesDuring(new Runnable() {
        @Override
        public void run() {
          for (LibraryTable.Listener listener : myListeners) {
            listener.afterLibraryRenamed(library);
          }
        }
      });
    }

    @Override
    public void beforeLibraryRemoved(final Library library) {
      myModificationCount++;
      mergeRootsChangesDuring(new Runnable() {
        @Override
        public void run() {
          for (LibraryTable.Listener listener : myListeners) {
            listener.beforeLibraryRemoved(library);
          }
        }
      });
    }

    @Override
    public void afterLibraryRemoved(final Library library) {
      myModificationCount++;
      mergeRootsChangesDuring(new Runnable() {
        @Override
        public void run() {
          for (LibraryTable.Listener listener : myListeners) {
            listener.afterLibraryRemoved(library);
          }
        }
      });
    }
  }

  @Override
  public long getModificationCount() {
    return myModificationCount;
  }
}

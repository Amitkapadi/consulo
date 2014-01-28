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
package com.intellij.openapi.module;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.containers.HashSet;
import com.intellij.util.graph.Graph;
import org.consulo.module.extension.ModuleExtension;
import org.consulo.module.extension.ModuleExtensionWithSdk;
import org.consulo.util.pointers.NamedPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ModuleUtilCore {
  public static final Key<Module> KEY_MODULE = new Key<Module>("Module");

  public static boolean projectContainsFile(final Project project, VirtualFile file, boolean isLibraryElement) {
    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
    if (isLibraryElement) {
      List<OrderEntry> orders = projectRootManager.getFileIndex().getOrderEntriesForFile(file);
      for (OrderEntry orderEntry : orders) {
        if (orderEntry instanceof ModuleExtensionWithSdkOrderEntry || orderEntry instanceof SdkOrderEntry ||
            orderEntry instanceof LibraryOrderEntry) {
          return true;
        }
      }
      return false;
    }
    else {
      return projectRootManager.getFileIndex().isInContent(file);
    }
  }

  public static String getModuleNameInReadAction(@NotNull final Module module) {
    return new ReadAction<String>() {
      protected void run(final Result<String> result) throws Throwable {
        result.setResult(module.getName());
      }
    }.execute().getResultObject();
  }

  public static boolean isModuleDisposed(PsiElement element) {
    if (!element.isValid()) return true;
    final Project project = element.getProject();
    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final PsiFile file = element.getContainingFile();
    if (file == null) return true;
    VirtualFile vFile = file.getVirtualFile();
    final Module module = vFile == null ? null : projectFileIndex.getModuleForFile(vFile);
    // element may be in library
    return module == null ? !projectFileIndex.isInLibraryClasses(vFile) : module.isDisposed();
  }

  @Nullable
  public static Module findModuleForFile(@NotNull VirtualFile file, @NotNull Project project) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return fileIndex.getModuleForFile(file);
  }

  @Nullable
  public static Module findModuleForPsiElement(@NotNull PsiElement element) {
    if (!element.isValid()) return null;

    Project project = element.getProject();
    if (project.isDefault()) return null;
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    if (element instanceof PsiFileSystemItem && (!(element instanceof PsiFile) || element.getContext() == null)) {
      VirtualFile vFile = ((PsiFileSystemItem)element).getVirtualFile();
      if (vFile == null) {
        PsiFile containingFile = element.getContainingFile();
        vFile = containingFile == null ? null : containingFile.getOriginalFile().getVirtualFile();
        if (vFile == null) {
          return element.getUserData(KEY_MODULE);
        }
      }
      if (fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile)) {
        final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(vFile);
        if (orderEntries.isEmpty()) {
          return null;
        }
        if (orderEntries.size() == 1) {
          return orderEntries.get(0).getOwnerModule();
        }
        Set<Module> modules = new HashSet<Module>();
        for (OrderEntry orderEntry : orderEntries) {
          modules.add(orderEntry.getOwnerModule());
        }
        final Module[] candidates = modules.toArray(new Module[modules.size()]);
        Arrays.sort(candidates, ModuleManager.getInstance(project).moduleDependencyComparator());
        return candidates[0];
      }
      return fileIndex.getModuleForFile(vFile);
    }
    PsiFile containingFile = element.getContainingFile();
    if (containingFile != null) {
      PsiElement context;
      while ((context = containingFile.getContext()) != null) {
        final PsiFile file = context.getContainingFile();
        if (file == null) break;
        containingFile = file;
      }

      if (containingFile.getUserData(KEY_MODULE) != null) {
        return containingFile.getUserData(KEY_MODULE);
      }

      final PsiFile originalFile = containingFile.getOriginalFile();
      if (originalFile.getUserData(KEY_MODULE) != null) {
        return originalFile.getUserData(KEY_MODULE);
      }

      final VirtualFile virtualFile = originalFile.getVirtualFile();
      if (virtualFile != null) {
        return fileIndex.getModuleForFile(virtualFile);
      }
    }

    return element.getUserData(KEY_MODULE);
  }

  //ignores export flag
  public static void getDependencies(@NotNull Module module, Set<Module> modules) {
    if (modules.contains(module)) return;
    modules.add(module);
    Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies();
    for (Module dependency : dependencies) {
      getDependencies(dependency, modules);
    }
  }

  /**
   * collect transitive module dependants
   *
   * @param module to find dependencies on
   * @param result resulted set
   */
  public static void collectModulesDependsOn(@NotNull final Module module, final Set<Module> result) {
    if (result.contains(module)) return;
    result.add(module);
    final ModuleManager moduleManager = ModuleManager.getInstance(module.getProject());
    final List<Module> dependentModules = moduleManager.getModuleDependentModules(module);
    for (final Module dependentModule : dependentModules) {
      final OrderEntry[] orderEntries = ModuleRootManager.getInstance(dependentModule).getOrderEntries();
      for (OrderEntry o : orderEntries) {
        if (o instanceof ModuleOrderEntry) {
          final ModuleOrderEntry orderEntry = (ModuleOrderEntry)o;
          if (orderEntry.getModule() == module) {
            if (orderEntry.isExported()) {
              collectModulesDependsOn(dependentModule, result);
            }
            else {
              result.add(dependentModule);
            }
            break;
          }
        }
      }
    }
  }

  @NotNull
  public static List<Module> getAllDependentModules(@NotNull Module module) {
    final ArrayList<Module> list = new ArrayList<Module>();
    final Graph<Module> graph = ModuleManager.getInstance(module.getProject()).moduleGraph();
    for (Iterator<Module> i = graph.getOut(module); i.hasNext(); ) {
      list.add(i.next());
    }
    return list;
  }

  public static boolean visitMeAndDependentModules(@NotNull final Module module, final ModuleVisitor visitor) {
    if (!visitor.visit(module)) {
      return false;
    }
    final List<Module> list = getAllDependentModules(module);
    for (Module dependentModule : list) {
      if (!visitor.visit(dependentModule)) {
        return false;
      }
    }
    return true;
  }

  public static boolean moduleContainsFile(@NotNull final Module module, VirtualFile file, boolean isLibraryElement) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    if (isLibraryElement) {
      OrderEntry orderEntry = moduleRootManager.getFileIndex().getOrderEntryForFile(file);
      return orderEntry instanceof ModuleExtensionWithSdkOrderEntry || orderEntry instanceof SdkOrderEntry ||
             orderEntry instanceof LibraryOrderEntry;
    }
    else {
      return moduleRootManager.getFileIndex().isInContent(file);
    }
  }

  @Nullable
  public static <E extends ModuleExtension<E>> E getExtension(@NotNull Module module, @NotNull Class<E> extensionClass) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    return moduleRootManager.getExtension(extensionClass);
  }

  @Nullable
  public static ModuleExtension<?> getExtension(@NotNull Module module, @NotNull String key) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    return moduleRootManager.getExtension(key);
  }

  @Nullable
  public static <E extends ModuleExtension<E>> E getExtension(@NotNull PsiElement element, @NotNull Class<E> extensionClass) {
    Module moduleForPsiElement = findModuleForPsiElement(element);
    if (moduleForPsiElement == null) {
      return null;
    }
    return getExtension(moduleForPsiElement, extensionClass);
  }

  @Nullable
  public static <E extends ModuleExtension<E>> E getExtension(@NotNull Project project,
                                                              @NotNull VirtualFile virtualFile,
                                                              @NotNull Class<E> extensionClass) {
    Module moduleForFile = findModuleForFile(virtualFile, project);
    if (moduleForFile == null) {
      return null;
    }
    return getExtension(moduleForFile, extensionClass);
  }

  @Nullable
  public static <S extends Sdk, E extends ModuleExtensionWithSdk<E>> S getSdk(@NotNull Module module, @NotNull Class<E> extensionClass) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

    final E extension = moduleRootManager.getExtension(extensionClass);
    if (extension == null) {
      return null;
    }
    else {
      return (S)extension.getSdk();
    }
  }

  @Nullable
  public static <S extends Sdk, E extends ModuleExtensionWithSdk<E>> S getSdk(@NotNull PsiElement element,
                                                                              @NotNull Class<E> extensionClass) {
    Module moduleForPsiElement = findModuleForPsiElement(element);
    if (moduleForPsiElement == null) {
      return null;
    }
    return getSdk(moduleForPsiElement, extensionClass);
  }

  @Nullable
  public static <S extends Sdk, E extends ModuleExtensionWithSdk<E>> S getSdk(@NotNull Project project,
                                                                              @NotNull VirtualFile virtualFile,
                                                                              @NotNull Class<E> extensionClass) {
    Module moduleForPsiElement = findModuleForFile(virtualFile, project);
    if (moduleForPsiElement == null) {
      return null;
    }
    return getSdk(moduleForPsiElement, extensionClass);
  }

  @NotNull
  public static NamedPointer<Module> createPointer(@NotNull Module module) {
    ModulePointerManager manager = ServiceManager.getService(module.getProject(), ModulePointerManager.class);
    return manager.create(module);
  }

  @NotNull
  public static NamedPointer<Module> createPointer(@NotNull Project project, @NotNull String name) {
    ModulePointerManager manager = ServiceManager.getService(project, ModulePointerManager.class);
    return manager.create(name);
  }

  public interface ModuleVisitor {
    /**
     * @param module module to be visited.
     * @return false to stop visiting.
     */
    boolean visit(final Module module);
  }
}

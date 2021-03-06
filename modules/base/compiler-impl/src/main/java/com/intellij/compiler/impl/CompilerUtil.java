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

/**
 * created at Jan 3, 2002
 * @author Jeka
 */
package com.intellij.compiler.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import consulo.logging.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import consulo.roots.ContentFolderScopes;
import gnu.trove.THashMap;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileFilter;
import java.util.*;

public class CompilerUtil {
  private static final Logger LOG = Logger.getInstance(CompilerUtil.class);

  public static String quotePath(String path) {
    if(path != null && path.indexOf(' ') != -1) {
      path = path.replaceAll("\\\\", "\\\\\\\\");
      path = '"' + path + '"';
    }
    return path;
  }

  public static void collectFiles(Collection<File> container, File rootDir, FileFilter fileFilter) {
    final File[] files = rootDir.listFiles(fileFilter);
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (file.isDirectory()) {
        collectFiles(container, file, fileFilter);
      }
      else {
        container.add(file);
      }
    }
  }

  public static Map<Module, List<VirtualFile>> buildModuleToFilesMap(CompileContext context, VirtualFile[] files) {
    return buildModuleToFilesMap(context, Arrays.asList(files));
  }


  public static Map<Module, List<VirtualFile>> buildModuleToFilesMap(final CompileContext context, final List<VirtualFile> files) {
    //assertion: all files are different
    final Map<Module, List<VirtualFile>> map = new THashMap<Module, List<VirtualFile>>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (VirtualFile file : files) {
          final Module module = context.getModuleByFile(file);

          if (module == null) {
            continue; // looks like file invalidated
          }

          List<VirtualFile> moduleFiles = map.get(module);
          if (moduleFiles == null) {
            moduleFiles = new ArrayList<VirtualFile>();
            map.put(module, moduleFiles);
          }
          moduleFiles.add(file);
        }
      }
    });
    return map;
  }


  /**
   * must not be called inside ReadAction
   * @param files
   */
  public static void refreshIOFiles(@Nonnull final Collection<File> files) {
    if (!files.isEmpty()) {
      LocalFileSystem.getInstance().refreshIoFiles(files);
    }
  }

  public static void refreshIODirectories(@Nonnull final Collection<File> files) {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final List<VirtualFile> filesToRefresh = new ArrayList<VirtualFile>();
    for (File file : files) {
      final VirtualFile virtualFile = lfs.refreshAndFindFileByIoFile(file);
      if (virtualFile != null) {
        filesToRefresh.add(virtualFile);
      }
    }
    if (!filesToRefresh.isEmpty()) {
      RefreshQueue.getInstance().refresh(false, true, null, filesToRefresh);
    }
  }

  public static void refreshIOFile(final File file) {
    final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    if (vFile != null) {
      vFile.refresh(false, false);
    }
  }

  public static void addLocaleOptions(final List<String> commandLine, final boolean launcherUsed) {
    // need to specify default encoding so that javac outputs messages in 'correct' language
    //noinspection HardCodedStringLiteral
    commandLine.add((launcherUsed? "-J" : "") + "-D" + CharsetToolkit.FILE_ENCODING_PROPERTY + "=" + CharsetToolkit.getDefaultSystemCharset().name());
    // javac's VM should use the same default locale that IDEA uses in order for javac to print messages in 'correct' language
    //noinspection HardCodedStringLiteral
    final String lang = System.getProperty("user.language");
    if (lang != null) {
      //noinspection HardCodedStringLiteral
      commandLine.add((launcherUsed? "-J" : "") + "-Duser.language=" + lang);
    }
    //noinspection HardCodedStringLiteral
    final String country = System.getProperty("user.country");
    if (country != null) {
      //noinspection HardCodedStringLiteral
      commandLine.add((launcherUsed? "-J" : "") + "-Duser.country=" + country);
    }
    //noinspection HardCodedStringLiteral
    final String region = System.getProperty("user.region");
    if (region != null) {
      //noinspection HardCodedStringLiteral
      commandLine.add((launcherUsed? "-J" : "") + "-Duser.region=" + region);
    }
  }

  public static <T extends Throwable> void runInContext(CompileContext context, String title, ThrowableRunnable<T> action) throws T {
    if (title != null) {
      context.getProgressIndicator().pushState();
      context.getProgressIndicator().setText(title);
    }
    try {
      action.run();
    }
    finally {
      if (title != null) {
        context.getProgressIndicator().popState();
      }
    }
  }

  public static void logDuration(final String activityName, long duration) {
    LOG.info(activityName + " took " + duration + " ms: " + duration /60000 + " min " +(duration %60000)/1000 + "sec");
  }

  public static void clearOutputDirectories(final Collection<File> outputDirectories) {
    final long start = System.currentTimeMillis();
    // do not delete directories themselves, or we'll get rootsChanged() otherwise
    final Collection<File> filesToDelete = new ArrayList<File>(outputDirectories.size() * 2);
    for (File outputDirectory : outputDirectories) {
      File[] files = outputDirectory.listFiles();
      if (files != null) {
        ContainerUtil.addAll(filesToDelete, files);
      }
    }
    if (filesToDelete.size() > 0) {
      FileUtil.asyncDelete(filesToDelete);

      // ensure output directories exist
      for (final File file : outputDirectories) {
        file.mkdirs();
      }
      final long clearStop = System.currentTimeMillis();

      refreshIODirectories(outputDirectories);

      final long refreshStop = System.currentTimeMillis();

      logDuration("Clearing output dirs", clearStop - start);
      logDuration("Refreshing output directories", refreshStop - clearStop);
    }
  }

  public static void computeIntersectingPaths(final Project project,
                                              final Collection<VirtualFile> outputPaths,
                                              final Collection<VirtualFile> result) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      final VirtualFile[] sourceRoots = rootManager.getContentFolderFiles(ContentFolderScopes.productionAndTest());
      for (final VirtualFile outputPath : outputPaths) {
        for (VirtualFile sourceRoot : sourceRoots) {
          if (VfsUtilCore.isAncestor(outputPath, sourceRoot, true) || VfsUtilCore.isAncestor(sourceRoot, outputPath, false)) {
            result.add(outputPath);
          }
        }
      }
    }
  }

  public static boolean askUserToContinueWithNoClearing(Project project, Collection<VirtualFile> affectedOutputPaths) {
    final StringBuilder paths = new StringBuilder();
    for (final VirtualFile affectedOutputPath : affectedOutputPaths) {
      if (paths.length() > 0) {
        paths.append(",\n");
      }
      paths.append(affectedOutputPath.getPath().replace('/', File.separatorChar));
    }
    final int answer = Messages.showOkCancelDialog(project,
                                                   CompilerBundle.message("warning.sources.under.output.paths", paths.toString()),
                                                   CommonBundle.getErrorTitle(), Messages.getWarningIcon());
    if (answer == Messages.OK) { // ok
      return true;
    }
    else {
      return false;
    }
  }
}

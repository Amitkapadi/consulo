/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.application.options.colors;

import com.intellij.application.options.OptionsContainingConfigurable;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.settings.DiffOptionsPanel;
import com.intellij.openapi.diff.impl.settings.DiffPreviewPanel;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.colors.impl.ReadOnlyColorsScheme;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.*;
import com.intellij.openapi.options.colors.*;
import com.intellij.openapi.options.newEditor.OptionsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Weighted;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusFactory;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.DependencyValidationManagerImpl;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.ui.UIUtil;
import consulo.annotations.RequiredDispatchThread;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;

public class ColorAndFontOptions extends SearchableConfigurable.Parent.Abstract {
  /**
   * Shows a requested page to edit a color settings.
   * If current data context represents a setting dialog that can open a requested page,
   * it will be opened. Otherwise, the new dialog will be opened.
   * The simplest way to get a data context is
   * <pre>DataManager.getInstance().getDataContext(myComponent)</pre>
   * where is {@code myComponent} is a {@link JComponent} in a Swing hierarchy.
   * A specific color can be requested by the {@code search} text.
   *
   * @param context a data context to find {@link OptionsEditor} or a parent for dialog
   * @param search  a text to find on the found page
   * @param name    a name of a page to find via {@link #findSubConfigurable(String)}
   * @return {@code true} if a color was shown to edit, {@code false} if a requested page does not exist
   */
  public static boolean selectOrEditColor(@NotNull DataContext context, @Nullable String search, @NotNull String name) {
    return selectOrEdit(context, search, options -> options.findSubConfigurable(name));
  }

  /**
   * Shows a requested page to edit a color settings.
   * If current data context represents a setting dialog that can open a requested page,
   * it will be opened. Otherwise, the new dialog will be opened.
   * The simplest way to get a data context is
   * <pre>DataManager.getInstance().getDataContext(myComponent)</pre>
   * where is {@code myComponent} is a {@link JComponent} in a Swing hierarchy.
   * A specific color can be requested by the {@code search} text.
   *
   * @param context a data context to find {@link OptionsEditor} or a parent for dialog
   * @param search  a text to find on the found page
   * @param type    a type of a page to find via {@link #findSubConfigurable(Class)}
   * @return {@code true} if a color was shown to edit, {@code false} if a requested page does not exist
   */
  public static boolean selectOrEditColor(@NotNull DataContext context, @Nullable String search, @NotNull Class<?> type) {
    return selectOrEdit(context, search, options -> options.findSubConfigurable(type));
  }

  private static boolean selectOrEdit(DataContext context, String search, Function<ColorAndFontOptions, SearchableConfigurable> function) {
    return select(context, search, function) || edit(context, search, function);
  }

  private static boolean select(DataContext context, String search, Function<ColorAndFontOptions, SearchableConfigurable> function) {
    OptionsEditor settings = context.getData(OptionsEditor.KEY);
    if (settings == null) return false;

    ColorAndFontOptions options = settings.findConfigurable(ColorAndFontOptions.class);
    if (options == null) return false;

    SearchableConfigurable page = function.apply(options);
    if (page == null) return false;

    settings.select(page, search);
    return true;
  }

  private static boolean edit(DataContext context, String search, Function<ColorAndFontOptions, SearchableConfigurable> function) {
    ColorAndFontOptions options = new ColorAndFontOptions();
    SearchableConfigurable page = function.apply(options);

    Configurable[] configurables = options.getConfigurables();
    try {
      if (page != null) {
        Runnable runnable = search == null ? null : page.enableSearch(search);
        Window window = UIUtil.getWindow(context.getData(PlatformDataKeys.CONTEXT_COMPONENT));
        if (window != null) {
          ShowSettingsUtil.getInstance().editConfigurable(window, page, runnable);
        }
        else {
          ShowSettingsUtil.getInstance().editConfigurable(context.getData(CommonDataKeys.PROJECT), page, runnable);
        }
      }
    }
    finally {
      for (Configurable configurable : configurables) configurable.disposeUIResources();
      options.disposeUIResources();
    }
    return page != null;
  }

  public static final String ID = "reference.settingsdialog.IDE.editor.colors";

  private HashMap<String, MyColorScheme> mySchemes;
  private MyColorScheme mySelectedScheme;
  public static final String DIFF_GROUP = ApplicationBundle.message("title.diff");
  public static final String FILE_STATUS_GROUP = ApplicationBundle.message("title.file.status");
  public static final String SCOPES_GROUP = ApplicationBundle.message("title.scope.based");

  private boolean mySomeSchemesDeleted = false;
  private Map<ColorAndFontPanelFactory, InnerSearchableConfigurable> mySubPanelFactories;

  private SchemesPanel myRootSchemesPanel;

  private boolean myInitResetCompleted = false;
  private boolean myInitResetInvoked = false;

  private boolean myRevertChangesCompleted = false;

  private boolean myApplyCompleted = false;
  private boolean myDisposeCompleted = false;
  private final Disposable myDisposable = Disposer.newDisposable();
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.colors.ColorAndFontOptions");

  @Override
  public boolean isModified() {
    boolean listModified = isSchemeListModified();
    boolean schemeModified = isSomeSchemeModified();

    if (listModified || schemeModified) {
      myApplyCompleted = false;
    }

    return listModified;
  }

  private boolean isSchemeListModified() {
    if (mySomeSchemesDeleted) return true;

    if (!mySelectedScheme.getName().equals(EditorColorsManager.getInstance().getGlobalScheme().getName())) return true;

    for (MyColorScheme scheme : mySchemes.values()) {
      if (scheme.isNew()) return true;
    }

    return false;
  }

  private boolean isSomeSchemeModified() {
    for (MyColorScheme scheme : mySchemes.values()) {
      if (scheme.isModified()) return true;
    }
    return false;
  }

  public EditorColorsScheme selectScheme(@NotNull String name) {
    mySelectedScheme = getScheme(name);
    return mySelectedScheme;
  }

  private MyColorScheme getScheme(String name) {
    return mySchemes.get(name);
  }

  public EditorColorsScheme getSelectedScheme() {
    return mySelectedScheme;
  }

  public EditorColorsScheme getOriginalSelectedScheme() {
    return mySelectedScheme == null ? null : mySelectedScheme.getOriginalScheme();
  }

  public EditorSchemeAttributeDescriptor[] getCurrentDescriptions() {
    return mySelectedScheme.getDescriptors();
  }

  public static boolean isReadOnly(@NotNull final EditorColorsScheme scheme) {
    return ((MyColorScheme)scheme).isReadOnly();
  }

  @NotNull
  public String[] getSchemeNames() {
    List<MyColorScheme> schemes = new ArrayList<>(mySchemes.values());
    Collections.sort(schemes, new Comparator<MyColorScheme>() {
      @Override
      public int compare(@NotNull MyColorScheme o1, @NotNull MyColorScheme o2) {
        if (isReadOnly(o1) && !isReadOnly(o2)) return -1;
        if (!isReadOnly(o1) && isReadOnly(o2)) return 1;

        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });

    List<String> names = new ArrayList<>(schemes.size());
    for (MyColorScheme scheme : schemes) {
      names.add(scheme.getName());
    }

    return ArrayUtil.toStringArray(names);
  }

  @NotNull
  public Collection<EditorColorsScheme> getSchemes() {
    return new ArrayList<>(mySchemes.values());
  }

  public void saveSchemeAs(String name) {
    MyColorScheme scheme = mySelectedScheme;
    if (scheme == null) return;

    EditorColorsScheme clone = (EditorColorsScheme)scheme.getOriginalScheme().clone();

    scheme.apply(clone);

    clone.setName(name);
    MyColorScheme newScheme = new MyColorScheme(clone, EditorColorsManager.getInstance());
    initScheme(newScheme);

    newScheme.setIsNew();

    mySchemes.put(name, newScheme);
    selectScheme(newScheme.getName());
    resetSchemesCombo(null);
  }

  public void addImportedScheme(@NotNull final EditorColorsScheme imported) {
    MyColorScheme newScheme = new MyColorScheme(imported, EditorColorsManager.getInstance());
    initScheme(newScheme);

    mySchemes.put(imported.getName(), newScheme);
    selectScheme(newScheme.getName());
    resetSchemesCombo(null);
  }

  public void removeScheme(String name) {
    if (mySelectedScheme.getName().equals(name)) {
      //noinspection HardCodedStringLiteral
      selectScheme("Default");
    }

    boolean deletedNewlyCreated = false;

    MyColorScheme toDelete = mySchemes.get(name);

    if (toDelete != null) {
      deletedNewlyCreated = toDelete.isNew();
    }

    mySchemes.remove(name);
    resetSchemesCombo(null);
    mySomeSchemesDeleted = mySomeSchemesDeleted || !deletedNewlyCreated;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myApplyCompleted) {
      return;
    }
    try {
      EditorColorsManager myColorsManager = EditorColorsManager.getInstance();

      myColorsManager.removeAllSchemes();
      for (MyColorScheme scheme : mySchemes.values()) {
        if (!scheme.isDefault()) {
          scheme.apply();
          myColorsManager.addColorsScheme(scheme.getOriginalScheme());
        }
      }

      EditorColorsScheme originalScheme = mySelectedScheme.getOriginalScheme();
      myColorsManager.setGlobalScheme(originalScheme);
      applyChangesToEditors();

      reset();
    }
    finally {
      myApplyCompleted = true;
    }
  }

  private static void applyChangesToEditors() {
    EditorFactory.getInstance().refreshAllEditors();

    TodoConfiguration.getInstance().colorSettingsChanged();
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project openProject : openProjects) {
      FileStatusManager.getInstance(openProject).fileStatusesChanged();
      DaemonCodeAnalyzer.getInstance(openProject).restart();
      BookmarkManager.getInstance(openProject).colorsChanged();
    }
  }

  private boolean myIsReset = false;

  private void resetSchemesCombo(Object source) {
    myIsReset = true;
    try {
      myRootSchemesPanel.resetSchemesCombo(source);
      if (mySubPanelFactories != null) {
        for (NewColorAndFontPanel subPartialConfigurable : getPanels()) {
          subPartialConfigurable.reset(source);
        }
      }
    }
    finally {
      myIsReset = false;
    }
  }

  @Override
  public JComponent createComponent() {
    if (myRootSchemesPanel == null) {
      ensureSchemesPanel();
    }
    return myRootSchemesPanel;
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @NotNull
  @Override
  public Configurable[] buildConfigurables() {
    myDisposeCompleted = false;
    initAll();

    List<ColorAndFontPanelFactory> panelFactories = createPanelFactories();

    List<Configurable> result = new ArrayList<>();
    mySubPanelFactories = new LinkedHashMap<>(panelFactories.size());
    for (ColorAndFontPanelFactory panelFactory : panelFactories) {
      mySubPanelFactories.put(panelFactory, new InnerSearchableConfigurable(panelFactory));
    }

    result.addAll(new ArrayList<SearchableConfigurable>(mySubPanelFactories.values()));
    return result.toArray(new Configurable[result.size()]);
  }

  @NotNull
  private Set<NewColorAndFontPanel> getPanels() {
    Set<NewColorAndFontPanel> result = new HashSet<>();
    for (InnerSearchableConfigurable configurable : mySubPanelFactories.values()) {
      NewColorAndFontPanel panel = configurable.getSubPanelIfInitialized();
      if (panel != null) {
        result.add(panel);
      }
    }
    return result;
  }

  protected List<ColorAndFontPanelFactory> createPanelFactories() {
    List<ColorAndFontPanelFactory> result = new ArrayList<>();
    result.add(new FontConfigurableFactory());

    List<ColorAndFontPanelFactory> extensions = new ArrayList<>();
    extensions.add(new ConsoleFontConfigurableFactory());
    ColorSettingsPage[] pages = ColorSettingsPages.getInstance().getRegisteredPages();
    for (final ColorSettingsPage page : pages) {
      extensions.add(new ColorAndFontPanelFactoryEx() {
        @Override
        public double getWeight() {
          if (page instanceof Weighted) {
            return ((Weighted)page).getWeight();
          }
          return 0;
        }

        @Override
        @NotNull
        public NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
          final SimpleEditorPreview preview = new SimpleEditorPreview(options, page);
          return NewColorAndFontPanel.create(preview, page.getDisplayName(), options, null, page);
        }

        @Override
        @NotNull
        public String getPanelDisplayName() {
          return page.getDisplayName();
        }
      });
    }
    Collections.addAll(extensions, Extensions.getExtensions(ColorAndFontPanelFactory.EP_NAME));
    result.addAll(extensions);
    result.add(new DiffColorsPageFactory());
    result.add(new FileStatusColorsPageFactory());
    result.add(new ScopeColorsPageFactory());

    return result;
  }

  private static class FontConfigurableFactory implements ColorAndFontPanelFactory, Weighted {
    @Override
    @NotNull
    public NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
      FontEditorPreview previewPanel = new FontEditorPreview(options, true);
      return new NewColorAndFontPanel(new SchemesPanel(options), new FontOptions(options), previewPanel, "Font", null, null) {
        @Override
        public boolean containsFontOptions() {
          return true;
        }
      };
    }

    @Override
    @NotNull
    public String getPanelDisplayName() {
      return "Font";
    }

    @Override
    public double getWeight() {
      return Integer.MAX_VALUE - 1;
    }
  }

  private static class ConsoleFontConfigurableFactory implements ColorAndFontPanelFactoryEx {
    @Override
    @NotNull
    public NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
      FontEditorPreview previewPanel = new FontEditorPreview(options, false) {
        @Override
        protected EditorColorsScheme updateOptionsScheme(EditorColorsScheme selectedScheme) {
          return ConsoleViewUtil.updateConsoleColorScheme(selectedScheme);
        }
      };
      return new NewColorAndFontPanel(new SchemesPanel(options), new ConsoleFontOptions(options), previewPanel, "Font", null, null) {
        @Override
        public boolean containsFontOptions() {
          return true;
        }
      };
    }

    @Override
    @NotNull
    public String getPanelDisplayName() {
      return "Console Font";
    }

    @Override
    public double getWeight() {
      return Integer.MAX_VALUE - 1;
    }
  }

  private class DiffColorsPageFactory implements ColorAndFontPanelFactory, Weighted {
    @Override
    @NotNull
    public NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
      final DiffOptionsPanel optionsPanel = new DiffOptionsPanel(options);
      SchemesPanel schemesPanel = new SchemesPanel(options);
      PreviewPanel previewPanel;
      try {
        final DiffPreviewPanel diffPreviewPanel = new DiffPreviewPanel(myDisposable);
        diffPreviewPanel.setMergeRequest(null);
        schemesPanel.addListener(new ColorAndFontSettingsListener.Abstract() {
          @Override
          public void schemeChanged(final Object source) {
            diffPreviewPanel.setColorScheme(getSelectedScheme());
            optionsPanel.updateOptionsList();
            diffPreviewPanel.updateView();
          }
        });
        previewPanel = diffPreviewPanel;
      }
      catch (FilesTooBigForDiffException e) {
        LOG.info(e);
        previewPanel = new PreviewPanel.Empty();
      }

      return new NewColorAndFontPanel(schemesPanel, optionsPanel, previewPanel, DIFF_GROUP, null, null);
    }

    @Override
    @NotNull
    public String getPanelDisplayName() {
      return DIFF_GROUP;
    }

    @Override
    public double getWeight() {
      return Integer.MAX_VALUE - 1;
    }
  }

  private void initAll() {
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    EditorColorsScheme[] allSchemes = colorsManager.getAllSchemes();

    mySchemes = new HashMap<>();
    for (EditorColorsScheme allScheme : allSchemes) {
      MyColorScheme schemeDelegate = new MyColorScheme(allScheme, colorsManager);
      initScheme(schemeDelegate);
      mySchemes.put(schemeDelegate.getName(), schemeDelegate);
    }

    mySelectedScheme = mySchemes.get(EditorColorsManager.getInstance().getGlobalScheme().getName());
    assert mySelectedScheme != null : EditorColorsManager.getInstance().getGlobalScheme().getName() + "; myschemes=" + mySchemes;
  }

  private static void initScheme(@NotNull MyColorScheme scheme) {
    List<EditorSchemeAttributeDescriptor> descriptions = new ArrayList<>();
    initPluggedDescriptions(descriptions, scheme);
    initDiffDescriptors(descriptions, scheme);
    initFileStatusDescriptors(descriptions, scheme);
    initScopesDescriptors(descriptions, scheme);

    scheme.setDescriptors(descriptions.toArray(new EditorSchemeAttributeDescriptor[descriptions.size()]));
  }

  private static void initPluggedDescriptions(@NotNull List<EditorSchemeAttributeDescriptor> descriptions, @NotNull MyColorScheme scheme) {
    ColorSettingsPage[] pages = ColorSettingsPages.getInstance().getRegisteredPages();
    for (ColorSettingsPage page : pages) {
      initDescriptions(page, descriptions, scheme);
    }
    for (ColorAndFontDescriptorsProvider provider : Extensions.getExtensions(ColorAndFontDescriptorsProvider.EP_NAME)) {
      initDescriptions(provider, descriptions, scheme);
    }
  }

  private static void initDescriptions(@NotNull ColorAndFontDescriptorsProvider provider, @NotNull List<EditorSchemeAttributeDescriptor> descriptions, @NotNull MyColorScheme scheme) {
    String group = provider.getDisplayName();
    List<AttributesDescriptor> attributeDescriptors = ColorSettingsUtil.getAllAttributeDescriptors(provider);
    for (AttributesDescriptor descriptor : attributeDescriptors) {
      addSchemedDescription(descriptions, descriptor.getDisplayName(), group, descriptor.getKey(), scheme, null, null);
    }

    ColorDescriptor[] colorDescriptors = provider.getColorDescriptors();
    for (ColorDescriptor descriptor : colorDescriptors) {
      ColorKey back = descriptor.getKind() == ColorDescriptor.Kind.BACKGROUND ? descriptor.getKey() : null;
      ColorKey fore = descriptor.getKind() == ColorDescriptor.Kind.FOREGROUND ? descriptor.getKey() : null;
      addEditorSettingDescription(descriptions, descriptor.getDisplayName(), group, back, fore, scheme);
    }
  }

  private static void initDiffDescriptors(@NotNull List<EditorSchemeAttributeDescriptor> descriptions, @NotNull MyColorScheme scheme) {
    DiffOptionsPanel.addSchemeDescriptions(descriptions, scheme);
  }

  private static void initFileStatusDescriptors(@NotNull List<EditorSchemeAttributeDescriptor> descriptions, MyColorScheme scheme) {

    FileStatus[] statuses = FileStatusFactory.getInstance().getAllFileStatuses();

    for (FileStatus fileStatus : statuses) {
      addEditorSettingDescription(descriptions, fileStatus.getText(), FILE_STATUS_GROUP, null, fileStatus.getColorKey(), scheme);

    }
  }

  private static void initScopesDescriptors(@NotNull List<EditorSchemeAttributeDescriptor> descriptions, @NotNull MyColorScheme scheme) {
    Set<Pair<NamedScope, NamedScopesHolder>> namedScopes = new THashSet<>(new TObjectHashingStrategy<Pair<NamedScope, NamedScopesHolder>>() {
      @Override
      public int computeHashCode(@NotNull final Pair<NamedScope, NamedScopesHolder> object) {
        return object.getFirst().getName().hashCode();
      }

      @Override
      public boolean equals(@NotNull final Pair<NamedScope, NamedScopesHolder> o1, @NotNull final Pair<NamedScope, NamedScopesHolder> o2) {
        return o1.getFirst().getName().equals(o2.getFirst().getName());
      }
    });
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      DependencyValidationManagerImpl validationManager = (DependencyValidationManagerImpl)DependencyValidationManager.getInstance(project);
      List<Pair<NamedScope, NamedScopesHolder>> cachedScopes = validationManager.getScopeBasedHighlightingCachedScopes();
      namedScopes.addAll(cachedScopes);
    }

    List<Pair<NamedScope, NamedScopesHolder>> list = new ArrayList<>(namedScopes);

    Collections.sort(list, new Comparator<Pair<NamedScope, NamedScopesHolder>>() {
      @Override
      public int compare(@NotNull final Pair<NamedScope, NamedScopesHolder> o1, @NotNull final Pair<NamedScope, NamedScopesHolder> o2) {
        return o1.getFirst().getName().compareToIgnoreCase(o2.getFirst().getName());
      }
    });
    for (Pair<NamedScope, NamedScopesHolder> pair : list) {
      NamedScope namedScope = pair.getFirst();
      String name = namedScope.getName();
      TextAttributesKey textAttributesKey = ScopeAttributesUtil.getScopeTextAttributeKey(name);
      if (scheme.getAttributes(textAttributesKey) == null) {
        scheme.setAttributes(textAttributesKey, new TextAttributes());
      }
      NamedScopesHolder holder = pair.getSecond();

      PackageSet value = namedScope.getValue();
      String toolTip = holder.getDisplayName() + (value == null ? "" : ": " + value.getText());
      addSchemedDescription(descriptions, name, SCOPES_GROUP, textAttributesKey, scheme, holder.getIcon(), toolTip);
    }
  }

  @Nullable
  private static String calcType(@Nullable ColorKey backgroundKey, @Nullable ColorKey foregroundKey) {
    if (foregroundKey != null) {
      return foregroundKey.getExternalName();
    }
    else if (backgroundKey != null) {
      return backgroundKey.getExternalName();
    }
    return null;
  }

  private static void addEditorSettingDescription(@NotNull List<EditorSchemeAttributeDescriptor> list,
                                                  String name,
                                                  String group,
                                                  @Nullable ColorKey backgroundKey,
                                                  @Nullable ColorKey foregroundKey,
                                                  @NotNull EditorColorsScheme scheme) {
    list.add(new EditorSettingColorDescription(name, group, backgroundKey, foregroundKey, calcType(backgroundKey, foregroundKey), scheme));
  }

  private static void addSchemedDescription(@NotNull List<EditorSchemeAttributeDescriptor> list,
                                            String name,
                                            String group,
                                            @NotNull TextAttributesKey key,
                                            @NotNull MyColorScheme scheme,
                                            Icon icon,
                                            String toolTip) {
    list.add(new SchemeTextAttributesDescription(name, group, key, scheme, icon, toolTip));
  }

  @Override
  public String getDisplayName() {
    return ApplicationBundle.message("title.colors.and.fonts");
  }

  private void revertChanges() {
    if (isSchemeListModified() || isSomeSchemeModified()) {
      myRevertChangesCompleted = false;
    }

    if (!myRevertChangesCompleted) {
      ensureSchemesPanel();


      try {
        resetImpl();
      }
      finally {
        myRevertChangesCompleted = true;
      }
    }

  }

  private void resetImpl() {
    mySomeSchemesDeleted = false;
    initAll();
    resetSchemesCombo(null);
  }

  @Override
  public synchronized void reset() {
    if (!myInitResetInvoked) {
      try {
        super.reset();
        if (!myInitResetCompleted) {
          ensureSchemesPanel();

          try {
            resetImpl();
          }
          finally {
            myInitResetCompleted = true;
          }
        }
      }
      finally {
        myInitResetInvoked = true;
      }

    }
    else {
      revertChanges();
    }

  }

  public synchronized void resetFromChild() {
    if (!myInitResetCompleted) {
      ensureSchemesPanel();


      try {
        resetImpl();
      }
      finally {
        myInitResetCompleted = true;
      }
    }

  }

  private void ensureSchemesPanel() {
    if (myRootSchemesPanel == null) {
      myRootSchemesPanel = new SchemesPanel(this);

      myRootSchemesPanel.addListener(new ColorAndFontSettingsListener.Abstract() {
        @Override
        public void schemeChanged(final Object source) {
          if (!myIsReset) {
            resetSchemesCombo(source);
          }
        }
      });

    }
  }

  @Override
  public void disposeUIResources() {
    try {
      if (!myDisposeCompleted) {
        try {
          super.disposeUIResources();
          Disposer.dispose(myDisposable);
        }
        finally {
          myDisposeCompleted = true;
        }
      }
    }
    finally {
      mySubPanelFactories = null;

      myInitResetCompleted = false;
      myInitResetInvoked = false;
      myRevertChangesCompleted = false;

      myApplyCompleted = false;
      myRootSchemesPanel = null;
    }
  }

  public boolean currentSchemeIsReadOnly() {
    return isReadOnly(mySelectedScheme);
  }

  public boolean currentSchemeIsShared() {
    return false;
  }


  private static class SchemeTextAttributesDescription extends TextAttributesDescription {
    @NotNull
    private final TextAttributes myAttributesToApply;
    @NotNull
    private final TextAttributesKey key;
    private TextAttributes myFallbackAttributes;
    private Pair<ColorSettingsPage, AttributesDescriptor> myBaseAttributeDescriptor;
    private boolean myIsInheritedInitial = false;

    private SchemeTextAttributesDescription(String name, String group, @NotNull TextAttributesKey key, @NotNull MyColorScheme scheme, Icon icon, String toolTip) {
      super(name, group, getInitialAttributes(scheme, key).clone(), key, scheme, icon, toolTip);
      this.key = key;
      myAttributesToApply = getInitialAttributes(scheme, key);
      TextAttributesKey fallbackKey = key.getFallbackAttributeKey();
      if (fallbackKey != null) {
        myFallbackAttributes = scheme.getAttributes(fallbackKey);
        myBaseAttributeDescriptor = ColorSettingsPages.getInstance().getAttributeDescriptor(fallbackKey);
        if (myBaseAttributeDescriptor == null) {
          myBaseAttributeDescriptor = new Pair<>(null, new AttributesDescriptor(fallbackKey.getExternalName(), fallbackKey));
        }
      }
      myIsInheritedInitial = isInherited(scheme);
      setInherited(myIsInheritedInitial);
      initCheckedStatus();
    }


    @NotNull
    private static TextAttributes getInitialAttributes(@NotNull MyColorScheme scheme, @NotNull TextAttributesKey key) {
      TextAttributes attributes = scheme.getAttributes(key);
      return attributes != null ? attributes : new TextAttributes();
    }

    private boolean isInherited(@NotNull MyColorScheme scheme) {
      TextAttributes attributes = scheme.getAttributes(key);
      TextAttributesKey fallbackKey = key.getFallbackAttributeKey();
      if (fallbackKey != null && !scheme.containsKey(key)) {
        TextAttributes fallbackAttributes = scheme.getAttributes(fallbackKey);
        if (attributes != null && attributes == fallbackAttributes) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void apply(EditorColorsScheme scheme) {
      if (scheme == null) scheme = getScheme();
      scheme.setAttributes(key, isInherited() ? new TextAttributes() : getTextAttributes());
    }

    @Override
    public boolean isModified() {
      return !Comparing.equal(myAttributesToApply, getTextAttributes()) || myIsInheritedInitial != isInherited();
    }

    @Override
    public boolean isErrorStripeEnabled() {
      return true;
    }

    @Nullable
    @Override
    public TextAttributes getBaseAttributes() {
      return myFallbackAttributes;
    }

    @Nullable
    @Override
    public Pair<ColorSettingsPage, AttributesDescriptor> getBaseAttributeDescriptor() {
      return myBaseAttributeDescriptor;
    }

    @Override
    public void setInherited(boolean isInherited) {
      super.setInherited(isInherited);
    }
  }

  private static class GetSetColor {
    private final ColorKey myKey;
    private final EditorColorsScheme myScheme;
    private boolean isModified = false;
    private Color myColor;

    private GetSetColor(ColorKey key, EditorColorsScheme scheme) {
      myKey = key;
      myScheme = scheme;
      myColor = myScheme.getColor(myKey);
    }

    public Color getColor() {
      return myColor;
    }

    public void setColor(Color col) {
      if (getColor() == null || !getColor().equals(col)) {
        isModified = true;
        myColor = col;
      }
    }

    public void apply(EditorColorsScheme scheme) {
      if (scheme == null) scheme = myScheme;
      scheme.setColor(myKey, myColor);
    }

    public boolean isModified() {
      return isModified;
    }
  }

  private static class EditorSettingColorDescription extends ColorAndFontDescription {
    private GetSetColor myGetSetForeground;
    private GetSetColor myGetSetBackground;

    private EditorSettingColorDescription(String name, String group, ColorKey backgroundKey, ColorKey foregroundKey, String type, EditorColorsScheme scheme) {
      super(name, group, type, scheme, null, null);
      if (backgroundKey != null) {
        myGetSetBackground = new GetSetColor(backgroundKey, scheme);
      }
      if (foregroundKey != null) {
        myGetSetForeground = new GetSetColor(foregroundKey, scheme);
      }
      initCheckedStatus();
    }

    @Override
    public int getFontType() {
      return 0;
    }

    @Override
    public void setFontType(int type) {
    }

    @Override
    public Color getExternalEffectColor() {
      return null;
    }

    @Override
    public void setExternalEffectColor(Color color) {
    }

    @Override
    public void setExternalEffectType(EffectType type) {
    }

    @NotNull
    @Override
    public EffectType getExternalEffectType() {
      return EffectType.LINE_UNDERSCORE;
    }

    @Override
    public Color getExternalForeground() {
      if (myGetSetForeground == null) {
        return null;
      }
      return myGetSetForeground.getColor();
    }

    @Override
    public void setExternalForeground(Color col) {
      if (myGetSetForeground == null) {
        return;
      }
      myGetSetForeground.setColor(col);
    }

    @Override
    public Color getExternalBackground() {
      if (myGetSetBackground == null) {
        return null;
      }
      return myGetSetBackground.getColor();
    }

    @Override
    public void setExternalBackground(Color col) {
      if (myGetSetBackground == null) {
        return;
      }
      myGetSetBackground.setColor(col);
    }

    @Override
    public Color getExternalErrorStripe() {
      return null;
    }

    @Override
    public void setExternalErrorStripe(Color col) {
    }

    @Override
    public boolean isFontEnabled() {
      return false;
    }

    @Override
    public boolean isForegroundEnabled() {
      return myGetSetForeground != null;
    }

    @Override
    public boolean isBackgroundEnabled() {
      return myGetSetBackground != null;
    }

    @Override
    public boolean isEffectsColorEnabled() {
      return false;
    }

    @Override
    public boolean isModified() {
      return myGetSetBackground != null && myGetSetBackground.isModified() || myGetSetForeground != null && myGetSetForeground.isModified();
    }

    @Override
    public void apply(EditorColorsScheme scheme) {
      if (myGetSetBackground != null) {
        myGetSetBackground.apply(scheme);
      }
      if (myGetSetForeground != null) {
        myGetSetForeground.apply(scheme);
      }
    }
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return ID;
  }

  private static class MyColorScheme extends EditorColorsSchemeImpl {

    private EditorSchemeAttributeDescriptor[] myDescriptors;
    private String myName;
    private boolean myIsNew = false;

    private MyColorScheme(@NotNull EditorColorsScheme parentScheme, @NotNull EditorColorsManager manager) {
      super(parentScheme, manager);

      parentScheme.getFontPreferences().copyTo(getFontPreferences());
      setLineSpacing(parentScheme.getLineSpacing());

      parentScheme.getConsoleFontPreferences().copyTo(getConsoleFontPreferences());
      setConsoleLineSpacing(parentScheme.getConsoleLineSpacing());

      setQuickDocFontSize(parentScheme.getQuickDocFontSize());
      myName = parentScheme.getName();
      if (parentScheme instanceof ExternalizableScheme) {
        getExternalInfo().copy(((ExternalizableScheme)parentScheme).getExternalInfo());
      }
      initFonts();
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Override
    public void setName(@NotNull String name) {
      myName = name;
    }

    public void setDescriptors(EditorSchemeAttributeDescriptor[] descriptors) {
      myDescriptors = descriptors;
    }

    public EditorSchemeAttributeDescriptor[] getDescriptors() {
      return myDescriptors;
    }

    public boolean isDefault() {
      return myParentScheme instanceof DefaultColorsScheme;
    }

    public boolean isReadOnly() {
      return myParentScheme instanceof ReadOnlyColorsScheme;
    }

    public boolean isModified() {
      if (isFontModified() || isConsoleFontModified()) return true;

      for (EditorSchemeAttributeDescriptor descriptor : myDescriptors) {
        if (descriptor.isModified()) {
          return true;
        }
      }

      return false;
    }

    private boolean isFontModified() {
      if (!getFontPreferences().equals(myParentScheme.getFontPreferences())) return true;
      if (getLineSpacing() != myParentScheme.getLineSpacing()) return true;
      return getQuickDocFontSize() != myParentScheme.getQuickDocFontSize();
    }

    private boolean isConsoleFontModified() {
      if (!getConsoleFontPreferences().equals(myParentScheme.getConsoleFontPreferences())) return true;
      return getConsoleLineSpacing() != myParentScheme.getConsoleLineSpacing();
    }

    public void apply() {
      apply(myParentScheme);
    }

    public void apply(@NotNull EditorColorsScheme scheme) {
      scheme.setFontPreferences(getFontPreferences());
      scheme.setLineSpacing(myLineSpacing);
      scheme.setQuickDocFontSize(getQuickDocFontSize());
      scheme.setConsoleFontPreferences(getConsoleFontPreferences());
      scheme.setConsoleLineSpacing(getConsoleLineSpacing());

      for (EditorSchemeAttributeDescriptor descriptor : myDescriptors) {
        descriptor.apply(scheme);
      }
    }

    @Override
    public EditorColorsScheme clone() {
      return null;
    }

    public EditorColorsScheme getOriginalScheme() {
      return myParentScheme;
    }

    public void setIsNew() {
      myIsNew = true;
    }

    public boolean isNew() {
      return myIsNew;
    }

    @NotNull
    @Override
    public String toString() {
      return "temporary scheme for " + myName;
    }
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  public SearchableConfigurable findSubConfigurable(@NotNull Class pageClass) {
    if (mySubPanelFactories == null) {
      buildConfigurables();
    }
    for (Map.Entry<ColorAndFontPanelFactory, InnerSearchableConfigurable> entry : mySubPanelFactories.entrySet()) {
      if (pageClass.isInstance(entry.getValue().createPanel().getSettingsPage())) {
        return entry.getValue();
      }
    }
    return null;
  }

  @Nullable
  public SearchableConfigurable findSubConfigurable(String pageName) {
    if (mySubPanelFactories == null) {
      buildConfigurables();
    }
    for (InnerSearchableConfigurable configurable : mySubPanelFactories.values()) {
      if (configurable.getDisplayName().equals(pageName)) {
        return configurable;
      }
    }
    return null;
  }

  @Nullable
  public NewColorAndFontPanel findPage(String pageName) {
    InnerSearchableConfigurable child = (InnerSearchableConfigurable)findSubConfigurable(pageName);
    return child == null ? null : child.createPanel();
  }

  private class InnerSearchableConfigurable implements SearchableConfigurable, OptionsContainingConfigurable, NoScroll, Weighted {
    private NewColorAndFontPanel mySubPanel;
    private boolean mySubInitInvoked = false;
    @NotNull
    private final ColorAndFontPanelFactory myFactory;

    private InnerSearchableConfigurable(@NotNull ColorAndFontPanelFactory factory) {
      myFactory = factory;
    }

    @NotNull
    @Override
    @Nls
    public String getDisplayName() {
      return myFactory.getPanelDisplayName();
    }

    public NewColorAndFontPanel getSubPanelIfInitialized() {
      return mySubPanel;
    }

    private NewColorAndFontPanel createPanel() {
      if (mySubPanel == null) {
        mySubPanel = myFactory.createPanel(ColorAndFontOptions.this);
        mySubPanel.reset(this);
        mySubPanel.addSchemesListener(new ColorAndFontSettingsListener.Abstract() {
          @Override
          public void schemeChanged(final Object source) {
            if (!myIsReset) {
              resetSchemesCombo(source);
            }
          }
        });

        mySubPanel.addDescriptionListener(new ColorAndFontSettingsListener.Abstract() {
          @Override
          public void fontChanged() {
            for (NewColorAndFontPanel panel : getPanels()) {
              panel.updatePreview();
            }
          }
        });
      }
      return mySubPanel;
    }

    @Override
    public String getHelpTopic() {
      return null;
    }

    @Override
    public double getWeight() {
      if (myFactory instanceof Weighted) {
        return ((Weighted)myFactory).getWeight();
      }
      return 0;
    }

    @RequiredDispatchThread
    @Override
    public JComponent createComponent() {
      return createPanel().getPanel();
    }

    @RequiredDispatchThread
    @Override
    public boolean isModified() {
      createPanel();
      for (MyColorScheme scheme : mySchemes.values()) {
        if (mySubPanel.containsFontOptions()) {
          if (scheme.isFontModified() || scheme.isConsoleFontModified()) {
            myRevertChangesCompleted = false;
            return true;
          }
        }
        else {
          for (EditorSchemeAttributeDescriptor descriptor : scheme.getDescriptors()) {
            if (mySubPanel.contains(descriptor) && descriptor.isModified()) {
              myRevertChangesCompleted = false;
              return true;
            }
          }
        }

      }

      return false;

    }

    @RequiredDispatchThread
    @Override
    public void apply() throws ConfigurationException {
      ColorAndFontOptions.this.apply();
    }

    @RequiredDispatchThread
    @Override
    public void reset() {
      if (!mySubInitInvoked) {
        if (!myInitResetCompleted) {
          resetFromChild();
        }
        mySubInitInvoked = true;
      }
      else {
        revertChanges();
      }
    }

    @RequiredDispatchThread
    @Override
    public void disposeUIResources() {
      if (mySubPanel != null) {
        mySubPanel.disposeUIResources();
        mySubPanel = null;
      }
    }

    @Override
    @NotNull
    public String getId() {
      return ColorAndFontOptions.this.getId() + "." + getDisplayName();
    }

    @Override
    public Runnable enableSearch(final String option) {
      return createPanel().showOption(option);
    }

    @NotNull
    @Override
    public Set<String> processListOptions() {
      return createPanel().processListOptions();
    }

    @NotNull
    @NonNls
    @Override
    public String toString() {
      return "Color And Fonts for " + getDisplayName();
    }
  }
}

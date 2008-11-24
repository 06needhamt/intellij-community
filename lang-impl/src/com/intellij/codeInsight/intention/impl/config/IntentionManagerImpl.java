package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionBean;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.actions.RunInspectionIntention;
import com.intellij.codeInspection.ex.DisableInspectionToolAction;
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction;
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsInSuppressedPlaceIntention;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 *  @author dsl
 */
public class IntentionManagerImpl extends IntentionManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl");

  private final List<IntentionAction> myActions = new ArrayList<IntentionAction>();
  private final IntentionManagerSettings mySettings;

  public IntentionManagerImpl(IntentionManagerSettings intentionManagerSettings) {
    mySettings = intentionManagerSettings;

    addAction(new EditInspectionToolsSettingsInSuppressedPlaceIntention());

    final ExtensionPoint<IntentionActionBean> point = Extensions.getArea(null).getExtensionPoint(EP_INTENTION_ACTIONS);

    point.addExtensionPointListener(new ExtensionPointListener<IntentionActionBean>() {
      public void extensionAdded(final IntentionActionBean extension, @Nullable final PluginDescriptor pluginDescriptor) {
        registerIntentionFromBean(extension);
      }

      public void extensionRemoved(final IntentionActionBean extension, @Nullable final PluginDescriptor pluginDescriptor) {
      }
    });
  }

  private void registerIntentionFromBean(final IntentionActionBean extension) {
    final String descriptionDirectoryName = extension.getDescriptionDirectoryName();
    final String[] categories = extension.getCategories();
    final IntentionAction instance = createIntentionActionWrapper(extension, categories);
    if (categories == null) {
      addAction(instance);
    }
    else {
      if (descriptionDirectoryName != null) {
        addAction(instance);
        mySettings.registerIntentionMetaData(instance, categories, descriptionDirectoryName, extension.getMetadataClassLoader());
      }
      else {
        registerIntentionAndMetaData(instance, categories);
      }
    }
  }

  private static IntentionAction createIntentionActionWrapper(final IntentionActionBean intentionActionBean, final String[] categories) {
    return new IntentionActionWrapper(intentionActionBean,categories);
  }

  public void registerIntentionAndMetaData(IntentionAction action, String... category) {
    registerIntentionAndMetaData(action, category, getDescriptionDirectoryName(action));
  }

  @NotNull
  private static String getDescriptionDirectoryName(final IntentionAction action) {
    if (action instanceof IntentionActionWrapper) {
      final IntentionActionWrapper wrapper = (IntentionActionWrapper)action;
      return getDescriptionDirectoryName(wrapper.getImplementationClassName());
    }
    else {
      return getDescriptionDirectoryName(action.getClass().getName());
    }
  }

  private static String getDescriptionDirectoryName(final String fqn) {
    return fqn.substring(fqn.lastIndexOf('.') + 1).replaceAll("\\$", "");
  }

  public void registerIntentionAndMetaData(@NotNull IntentionAction action, @NotNull String[] category, @NotNull @NonNls String descriptionDirectoryName) {
    addAction(action);
    mySettings.registerIntentionMetaData(action, category, descriptionDirectoryName);
  }

  public void registerIntentionAndMetaData(final IntentionAction action,
                                           final String[] category,
                                           final String description,
                                           final String exampleFileExtension,
                                           final String[] exampleTextBefore,
                                           final String[] exampleTextAfter) {
    addAction(action);

    IntentionActionMetaData metaData = new IntentionActionMetaData(action, category,
                                                                   new PlainTextDescriptor(description, "description.html"),
                                                                   mapToDescriptors(exampleTextBefore, "before." + exampleFileExtension),
                                                                   mapToDescriptors(exampleTextAfter, "after." + exampleFileExtension));
    mySettings.registerMetaData(metaData);
  }

  private static TextDescriptor[] mapToDescriptors(String[] texts, String fileName) {
    TextDescriptor[] result = new TextDescriptor[texts.length];
    for (int i = 0; i < texts.length; i++) {
      result [i] = new PlainTextDescriptor(texts [i], fileName);
    }
    return result;
  }

  public List<IntentionAction> getStandardIntentionOptions(final HighlightDisplayKey displayKey, final PsiElement context) {
    List<IntentionAction> options = new ArrayList<IntentionAction>(9);
    options.add(new EditInspectionToolsSettingsAction(displayKey));
    options.add(new RunInspectionIntention(displayKey));
    options.add(new DisableInspectionToolAction(displayKey));
    return options;
  }

  public LocalQuickFix convertToFix(final IntentionAction action) {
    if (action instanceof LocalQuickFix) {
      return (LocalQuickFix)action;
    }
    return new LocalQuickFix() {
      @NotNull
      public String getName() {
        return action.getText();
      }

      @NotNull
      public String getFamilyName() {
        return action.getFamilyName();
      }

      public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
        final PsiFile psiFile = descriptor.getPsiElement().getContainingFile();
        try {
          action.invoke(project, new LazyEditor(psiFile), psiFile);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };
  }

  public void addAction(IntentionAction action) {
    myActions.add(action);
  }

  public IntentionAction[] getIntentionActions() {
    return myActions.toArray(new IntentionAction[myActions.size()]);
  }

}

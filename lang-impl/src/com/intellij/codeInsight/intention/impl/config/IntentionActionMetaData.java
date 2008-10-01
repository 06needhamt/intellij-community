/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;



public final class IntentionActionMetaData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionActionMetaData");
  @NotNull private final IntentionAction myAction;
  private final ClassLoader myIntentionLoader;
  @NotNull private final String myDescriptionDirectoryName;
  @NotNull public final String[] myCategory;

  private URL[] myExampleUsagesBefore = null;
  private URL[] myExampleUsagesAfter = null;
  private URL myDescription = null;
  private URL myDirURL = null;

  @NonNls private static final String BEFORE_TEMPLATE_PREFIX = "before";
  @NonNls private static final String AFTER_TEMPLATE_PREFIX = "after";
  @NonNls static final String EXAMPLE_USAGE_URL_SUFFIX = ".template";
  @NonNls private static final String DESCRIPTION_FILE_NAME = "description.html";
  @NonNls private static final String INTENTION_DESCRIPTION_FOLDER = "intentionDescriptions";

  public IntentionActionMetaData(@NotNull IntentionAction action,
                                 @Nullable ClassLoader loader,
                                 @NotNull String[] category,
                                 @NotNull String descriptionDirectoryName) {
    myAction = action;
    myIntentionLoader = loader;
    myCategory = category;
    myDescriptionDirectoryName = descriptionDirectoryName;
  }

  public String toString() {
    return getFamily();
  }

  @Nullable
  public URL[] getExampleUsagesBefore() {
    if(myExampleUsagesBefore == null){
      try {
        myExampleUsagesBefore = retrieveURLs(getDirURL(), BEFORE_TEMPLATE_PREFIX, EXAMPLE_USAGE_URL_SUFFIX);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
    }
    return myExampleUsagesBefore;
  }

  @Nullable
  public URL[] getExampleUsagesAfter() {
      if(myExampleUsagesAfter == null){
      try {
        myExampleUsagesAfter = retrieveURLs(getDirURL(), AFTER_TEMPLATE_PREFIX, EXAMPLE_USAGE_URL_SUFFIX);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
    }
    return myExampleUsagesAfter;
  }

  @Nullable
  public URL getDescription() {
    if(myDescription == null){
      try {
        final URL dirURL = getDirURL();
        if (dirURL == null) return null;
        myDescription = new URL(dirURL.toExternalForm() + "/" + DESCRIPTION_FILE_NAME);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
    }
    return myDescription;
  }

  @Nullable
  private static URL[] retrieveURLs(@NotNull URL descriptionDirectory, @NotNull String prefix, @NotNull String suffix) throws MalformedURLException {
    List<URL> urls = new ArrayList<URL>();
    final FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (FileType fileType : fileTypes) {
      final String[] extensions = FileTypeManager.getInstance().getAssociatedExtensions(fileType);
      for (String extension : extensions) {
        for (int i = 0; ; i++) {
          URL url = new URL(descriptionDirectory.toExternalForm() + "/" +
                            prefix + "." + extension + (i == 0 ? "" : Integer.toString(i)) +
                            suffix);
          try {
            InputStream inputStream = url.openStream();
            inputStream.close();
            urls.add(url);
          }
          catch (IOException ioe) {
            break;
          }
        }
      }
    }
    return urls.isEmpty() ? null : urls.toArray(new URL[urls.size()]);
  }

  private static URL getIntentionDescriptionDirURL(ClassLoader aClassLoader, String intentionFolderName) {
    final URL pageURL = aClassLoader.getResource(INTENTION_DESCRIPTION_FOLDER + "/" + intentionFolderName);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Path:"+"intentionDescriptions/" + intentionFolderName);
      LOG.debug("URL:"+pageURL);
    }
    return pageURL;
  }

  public URL getDirURL() {
    if (myDirURL == null) {
      myDirURL = getIntentionDescriptionDirURL(myIntentionLoader, myDescriptionDirectoryName);
    }
    if (myDirURL == null) { //plugin compatibility
      myDirURL = getIntentionDescriptionDirURL(myIntentionLoader, getFamily());
    }
    LOG.assertTrue(myDirURL != null, "Intention Description Dir URL is null: " +
                                     getFamily() +"; "+myDescriptionDirectoryName + ", " + myIntentionLoader);
    return myDirURL;
  }

  @Nullable public PluginId getPluginId() {
    if (myIntentionLoader instanceof PluginClassLoader) {
      return ((PluginClassLoader)myIntentionLoader).getPluginId();
    }
    return null;
  }

  @NotNull
  public String getFamily() {
    return myAction.getFamilyName();
  }
}

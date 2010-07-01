package com.jetbrains.python.codeInsight;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author yole
 */
@State(
  name="PyCodeInsightSettings",
  storages = {
    @Storage(
      id="PyCodeInsightSettings",
      file="$APP_CONFIG$/other.xml"
    )}
)
public class PyCodeInsightSettings implements PersistentStateComponent<PyCodeInsightSettings> {
  public static PyCodeInsightSettings getInstance() {
    return ServiceManager.getService(PyCodeInsightSettings.class);
  }

  public boolean PREFER_FROM_IMPORT = true;
  public boolean SHOW_IMPORT_POPUP = true;
  public boolean HIGHLIGHT_UNUSED_IMPORTS = true;

  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION = false;
  public boolean RENAME_SEARCH_NON_CODE_FOR_FUNCTION = false;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = false;
  public boolean RENAME_SEARCH_NON_CODE_FOR_CLASS = false;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = false;
  public boolean RENAME_SEARCH_NON_CODE_FOR_VARIABLE = false;

  public PyCodeInsightSettings getState() {
    return this;
  }

  public void loadState(PyCodeInsightSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}

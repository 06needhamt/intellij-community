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

  public PyCodeInsightSettings getState() {
    return this;
  }

  public void loadState(PyCodeInsightSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}

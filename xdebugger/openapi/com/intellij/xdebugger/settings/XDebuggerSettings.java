package com.intellij.xdebugger.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.xdebugger.XDebuggerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * Implement this class to provide settings page for debugger. Settings page will be placed under 'Debugger' node in the 'Settings' dialog.
 * An implementation should be registered in plugin.xml:
 * <p>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;xdebugger.settings implementation="qualified-class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * 
 * @author nik
 */
public abstract class XDebuggerSettings<T> implements PersistentStateComponent<T> {
  public static final ExtensionPointName<XDebuggerSettings> EXTENSION_POINT = ExtensionPointName.create("com.intellij.xdebugger.settings");
  private final String myId;

  protected XDebuggerSettings(final @NotNull @NonNls String id) {
    myId = id;
  }

  protected static <S extends XDebuggerSettings<?>> S getInstance(Class<S> aClass) {
    return XDebuggerUtil.getInstance().getDebuggerSettings(aClass);
  }

  public final String getId() {
    return myId;
  }

  @NotNull
  public abstract Configurable createConfigurable();
}

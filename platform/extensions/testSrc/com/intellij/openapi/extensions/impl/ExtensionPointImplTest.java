// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Test;
import org.picocontainer.PicoContainer;
import org.picocontainer.defaults.DefaultPicoContainer;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author AKireyev
 */
public class ExtensionPointImplTest {
  private Disposable disposable = Disposer.newDisposable();

  @After
  public void tearDown() {
    if (disposable != null) {
      Disposer.dispose(disposable);
    }
  }

  @Test
  public void testCreate() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    assertThat(extensionPoint.getName()).isEqualTo(ExtensionsImplTest.EXTENSION_POINT_NAME_1);
    assertThat(extensionPoint.getClassName()).isEqualTo(Integer.class.getName());
  }

  @Test
  public void testUnregisterObject() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(123), disposable);
    assertThat(extensionPoint.getExtensionList()).hasSize(1);

    Disposer.dispose(disposable);
    disposable = null;

    assertThat(extensionPoint.getExtensionList()).isEmpty();
  }

  @Test
  public void testRegisterObject() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(123), disposable);
    Object[] extensions = extensionPoint.getExtensions();
    assertThat(extensions).describedAs("One extension").hasSize(1);
    assertThat(extensions).isInstanceOf(Integer[].class);
    assertThat(extensions[0]).isEqualTo(new Integer(123));
  }

  @Test
  public void testRegistrationOrder() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(123), disposable);
    extensionPoint.registerExtension(new Integer(321), LoadingOrder.FIRST, null);
    Object[] extensions = extensionPoint.getExtensions();
    assertThat(extensions).hasSize(2);
    assertThat(extensions[0]).isEqualTo(new Integer(321));
  }

  @Test
  public void testListener() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    final boolean[] added = new boolean[1];
    final boolean[] removed = new boolean[1];
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<Integer>() {
      @Override
      public void extensionAdded(@NotNull Integer extension, final PluginDescriptor pluginDescriptor) {
        added[0] = true;
      }

      @Override
      public void extensionRemoved(@NotNull Integer extension, final PluginDescriptor pluginDescriptor) {
        removed[0] = true;
      }
    }, true, null);
    assertThat(added[0]).isFalse();
    assertThat(removed[0]).isFalse();
    extensionPoint.registerExtension(new Integer(123), disposable);
    assertThat(added[0]).isTrue();
    assertThat(removed[0]).isFalse();
    added[0] = false;

    Disposer.dispose(disposable);
    disposable = null;

    assertThat(added[0]).isFalse();
    assertThat(removed[0]).isTrue();
  }

  @Test
  public void testLateListener() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    final boolean[] added = new boolean[1];
    extensionPoint.registerExtension(new Integer(123), disposable);
    assertThat(added[0]).isFalse();
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<Integer>() {
      @Override
      public void extensionAdded(@NotNull Integer extension, @Nullable PluginDescriptor pluginDescriptor) {
        added[0] = true;
      }
    }, true, null);
    assertThat(added[0]).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIncompatibleExtension() {
    ExtensionPoint extensionPoint = buildExtensionPoint(Integer.class);

    try {
      extensionPoint.registerExtension(new Double(0), disposable);
      fail("must throw");
    }
    catch (RuntimeException ignored) {
    }

    assertThat(extensionPoint.getExtensionList()).isEmpty();

    extensionPoint.registerExtension(new Integer(0), disposable);
    assertThat(extensionPoint.getExtensionList()).hasSize(1);
  }

  @Test
  public void testIncompatibleAdapter() {
    ExtensionPointImpl<Integer> extensionPoint = buildExtensionPoint(Integer.class);

    extensionPoint.registerExtensionAdapter(stringAdapter());

    try {
      assertThat(extensionPoint.getExtensionList()).isEmpty();
      fail("must throw");
    }
    catch (AssertionError ignored) {
    }
  }

  @Test
  public void testCompatibleAdapter() {
    ExtensionPointImpl<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(new Integer(0), disposable);
    assertThat(extensionPoint.getExtensions()).hasSize(1);
  }

  @Test
  public void testCancelledRegistration() {
    ExtensionPoint<String> extensionPoint = buildExtensionPoint(String.class);
    MyShootingComponentAdapter adapter = stringAdapter();

    extensionPoint.registerExtension("first", disposable);
    assertThat(extensionPoint.getExtensionList()).hasSize(1);

    // registers a wrapping adapter
    extensionPoint.registerExtension("second", LoadingOrder.FIRST, null);
    ((ExtensionPointImpl)extensionPoint).registerExtensionAdapter(adapter);
    adapter.setFire(true);
    try {
      extensionPoint.getExtensionList();
      fail("PCE expected");
    }
    catch (ProcessCanceledException ignored) {
    }

    adapter.setFire(false);
    List<String> extensions = extensionPoint.getExtensionList();
    assertThat(extensionPoint.getExtensionList()).hasSize(3);

    assertThat(extensions.get(0)).isEqualTo("second");
    assertThat(extensions.get(1)).isIn("", "first");
    assertThat(extensions.get(2)).isIn("", "first");
    assertThat(extensions.get(2)).isNotEqualTo(extensions.get(1));
  }

  @Test
  public void testListenerNotifications() {
    ExtensionPoint<String> extensionPoint = buildExtensionPoint(String.class);
    final List<String> extensions = ContainerUtil.newArrayList();
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<String>() {
      @Override
      public void extensionAdded(@NotNull String extension, @Nullable PluginDescriptor pluginDescriptor) {
        extensions.add(extension);
      }
    }, true, null);
    MyShootingComponentAdapter adapter = stringAdapter();

    extensionPoint.registerExtension("first", disposable);
    assertThat(extensions).contains("first");

    extensionPoint.registerExtension("second", LoadingOrder.FIRST, null);
    ((ExtensionPointImpl)extensionPoint).registerExtensionAdapter(adapter);
    adapter.setFire(true);
    try {
      extensionPoint.getExtensions();
      fail("PCE expected");
    }
    catch (ProcessCanceledException ignored) { }
    assertThat(extensions).contains("first", "second");

    adapter.setFire(false);
    extensionPoint.getExtensions();
    assertThat(extensions).contains("first", "second", "");
  }

  @Test
  public void clientsCannotModifyCachedExtensions() {
    ExtensionPoint<Integer> extensionPoint = buildExtensionPoint(Integer.class);
    extensionPoint.registerExtension(4, disposable);
    extensionPoint.registerExtension(2, disposable);

    Integer[] extensions = extensionPoint.getExtensions();
    assertThat(extensions).containsExactly(4, 2);
    Arrays.sort(extensions);
    assertThat(extensions).containsExactly(2, 4);

    assertThat(extensionPoint.getExtensions()).containsExactly(4, 2);
  }

  @NotNull
  private static <T> ExtensionPointImpl<T> buildExtensionPoint(@NotNull Class<T> aClass) {
    return new InterfaceExtensionPoint<>(ExtensionsImplTest.EXTENSION_POINT_NAME_1, aClass, buildExtensionArea());
  }

  @NotNull
  private static ExtensionsAreaImpl buildExtensionArea() {
    return new ExtensionsAreaImpl(null, null, new DefaultPicoContainer());
  }

  private static MyShootingComponentAdapter stringAdapter() {
    return new MyShootingComponentAdapter(String.class.getName());
  }

  private static class MyShootingComponentAdapter extends ExtensionComponentAdapter {
    private boolean myFire;

    MyShootingComponentAdapter(@NotNull String implementationClass) {
      super(implementationClass, new DefaultPluginDescriptor("test"), null, LoadingOrder.ANY);
    }

    public void setFire(boolean fire) {
      myFire = fire;
    }

    @NotNull
    @Override
    public Object createInstance(@Nullable PicoContainer container) {
      if (myFire) {
        throw new ProcessCanceledException();
      }
      else {
        return super.createInstance(container);
      }
    }
  }
}

/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.testFramework;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * @author peter
 */
public abstract class UsefulTestCase extends TestCase {
  protected final Disposable myTestRootDisposable = new Disposable() {
    public void dispose() {
    }
  };

  protected void tearDown() throws Exception {
    if (ApplicationManager.getApplication() != null) {
      assertTrue("Code insight settings damaged", areSettingsEqual(new CodeInsightSettings(EditorActionManager.getInstance()), CodeInsightSettings.getInstance()));
    }
    Disposer.dispose(myTestRootDisposable);
    super.tearDown();
  }

  protected Disposable getTestRootDisposable() {
    return myTestRootDisposable;
  }

  @NonNls
  public static String toString(Collection collection) {
    if (collection.isEmpty()) {
      return "<empty>";
    }

    final StringBuilder builder = new StringBuilder();
    for (final Object o : collection) {
      if (o instanceof THashSet) {
        builder.append(new TreeSet((Collection)o));
      } else {
        builder.append(o);
      }
      builder.append("\n");
    }
    return builder.toString();
  }

  public static <T> void assertOrderedEquals(T[] actual, T... expected) {
    assertOrderedEquals(Arrays.asList(actual), expected);
  }

  public static <T> void assertOrderedEquals(Collection<T> actual, T... expected) {
    assertNotNull(actual);
    assertNotNull(expected);
    assertOrderedEquals(actual, Arrays.asList(expected));
  }

  public static <T> void assertOrderedEquals(final Collection<? extends T> actual, final Collection<? extends T> expected) {
    if (!new ArrayList<T>(actual).equals(new ArrayList<T>(expected))) {
      assertEquals(toString(expected), toString(actual));
      fail();
    }
  }

  public static <T> void assertOrderedCollection(T[] collection, Consumer<T>... checkers) {
    assertNotNull(collection);
    assertOrderedCollection(Arrays.asList(collection), checkers);
  }

  public static <T> void assertSameElements(T[] collection, T... expected) {
    assertSameElements(Arrays.asList(collection), expected);
  }
  
  public static <T> void assertSameElements(Collection<? extends T> collection, T... expected) {
    if (collection.size() != expected.length || !new HashSet<T>(Arrays.asList(expected)).equals(new HashSet<T>(collection))) {
      assertEquals(toString(expected, "\n"), toString(collection, "\n"));
      assertEquals(new HashSet<T>(Arrays.asList(expected)), new HashSet<T>(collection));
    }

  }

  public static String toString(Object[] collection, String separator) {
    return toString(Arrays.asList(collection), separator);
  }

  public static String toString(Collection collection, String separator) {
    List<String> list = ContainerUtil.map2List(collection, new Function() {
      public Object fun(final Object o) {
        return String.valueOf(o);
      }
    });
    Collections.sort(list);
    StringBuilder builder = new StringBuilder();
    boolean flag = false;
    for (final String o : list) {
      if (flag) {
        builder.append(separator);
      }
      builder.append(o);
      flag = true;
    }
    return builder.toString();
  }
  
  public static <T> void assertOrderedCollection(Collection<? extends T> collection, Consumer<T>... checkers) {
    assertNotNull(collection);
    if (collection.size() != checkers.length) {
      fail(toString(collection));
    }
    int i = 0;
    for (final T actual : collection) {
      try {
        checkers[i].consume(actual);
      }
      catch (AssertionFailedError e) {
        System.out.println(i + ": " + actual);
        throw e;
      }
      i++;
    }
  }

  public static <T> void assertUnorderedCollection(T[] collection, Consumer<T>... checkers) {
    assertUnorderedCollection(Arrays.asList(collection), checkers);
  }

  public static <T> void assertUnorderedCollection(Collection<? extends T> collection, Consumer<T>... checkers) {
    assertNotNull(collection);
    if (collection.size() != checkers.length) {
      fail(toString(collection));
    }
    Set<Consumer<T>> checkerSet = new HashSet<Consumer<T>>(Arrays.asList(checkers));
    int i = 0;
    Throwable lastError = null;
    for (final T actual : collection) {
      boolean flag = true;
      for (final Consumer<T> condition : checkerSet) {
        Throwable error = accepts(condition, actual);
        if (error == null) {
          checkerSet.remove(condition);
          flag = false;
          break;
        }
        else {
          lastError = error;
        }
      }
      if (flag) {
        lastError.printStackTrace();
        fail("Incorrect element(" + i + "): " + actual);
      }
      i++;
    }
  }

  private static <T> Throwable accepts(final Consumer<T> condition, final T actual) {
    try {
      condition.consume(actual);
      return null;
    }
    catch (Throwable e) {
      return e;
    }
  }

  public static <T> T assertInstanceOf(Object o, Class<T> aClass) {
    assertNotNull(o);
    assertTrue(o.getClass().getName(), aClass.isInstance(o));
    return (T)o;
  }

  public static <T> T assertOneElement(Collection<T> collection) {
    assertNotNull(collection);
    assertEquals(1, collection.size());
    return collection.iterator().next();
  }

  public static <T> T assertOneElement(T[] ts) {
    assertNotNull(ts);
    assertEquals(1, ts.length);
    return ts[0];
  }

  public static void printThreadDump() {
    final Map<Thread,StackTraceElement[]> traces = Thread.getAllStackTraces();
    for (final Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
      System.out.println("\n" + entry.getKey().getName() + "\n");
      final StackTraceElement[] value = entry.getValue();
      for (final StackTraceElement stackTraceElement : value) {
        System.out.println(stackTraceElement);
      }
    }
  }

  public static void assertEmpty(final Object[] array) {
    assertOrderedEquals(array);
  }
  
  public static void assertEmpty(final Collection collection) {
    assertOrderedEquals(collection);
  }

  protected <T extends Disposable> T disposeOnTearDown(final T disposable) {
    Disposer.register(myTestRootDisposable, disposable);
    return disposable;
  }

  public static void assertSameLines(String expected, String actual) {
    String expectedText = StringUtil.convertLineSeparators(expected.trim());
    String actualText = StringUtil.convertLineSeparators(actual.trim());
    assertEquals(expectedText, actualText);
  }

  protected String getTestName(boolean lowercaseFirstLetter) {
    String name = getName();
    assertTrue(name.startsWith("test"));
    name = name.substring("test".length());
    if (lowercaseFirstLetter) {
      name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    return name;
  }

  protected static void assertSameLinesWithFile(final String filePath, final String actualText) {
    String fileText;
    try {
      final FileReader reader = new FileReader(filePath);
      fileText = FileUtil.loadTextAndClose(reader);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    assertSameLines(fileText, actualText);
  }

  public static void clearFields(final Object test) throws IllegalAccessException {
    Class aClass = test.getClass();
    while (aClass != null) {
      clearDeclaredFields(test, aClass);
      aClass = aClass.getSuperclass();
    }
  }

  public static void clearDeclaredFields(Object test, Class aClass) throws IllegalAccessException {
    if (aClass == null) return;
    for (final Field field : aClass.getDeclaredFields()) {
      @NonNls final String name = field.getDeclaringClass().getName();
      if (!name.startsWith("junit.framework.") && !name.startsWith("com.intellij.testFramework.")) {
        final int modifiers = field.getModifiers();
        if ((modifiers & Modifier.FINAL) == 0 && (modifiers & Modifier.STATIC) == 0 && !field.getType().isPrimitive()) {
          field.setAccessible(true);
          field.set(test, null);
        }
      }
    }
  }

  private static boolean areSettingsEqual(CodeInsightSettings oldCodeInsightSettings, CodeInsightSettings settings) throws WriteExternalException {
    if (oldCodeInsightSettings == null || settings == null) return true;
    Element newS = new Element("temp");
    settings.writeExternal(newS);

    Element oldS = new Element("temp");
    oldCodeInsightSettings.writeExternal(oldS);

    return JDOMUtil.areElementsEqual(newS, oldS);
  }
}

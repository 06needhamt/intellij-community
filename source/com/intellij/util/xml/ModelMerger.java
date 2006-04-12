/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xml.impl.AdvancedProxy;
import com.intellij.util.xml.impl.DomImplUtil;
import com.intellij.util.Processor;
import com.intellij.util.CommonProcessors;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author peter
 */
public class ModelMerger {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.ModelMerger");

  public static class ImplementationProcessor<T> implements Processor<T> {
    private final Processor<T> myProcessor;

    public ImplementationProcessor(Processor<T> processor) {
      myProcessor = processor;
    }

    public boolean process(final T t) {
      if (t instanceof MergedObject) {
        for (T impl : ((MergedObject<T>)t).getImplementations()) {
          if (!process(impl)) return false;
        }
      }
      else {
        if (!myProcessor.process(t)) {
          return false;
        }
      }
      return true;
    }
  }

  private ModelMerger() {
  }

  public interface MergedObject<V> {
    List<V> getImplementations();
  }


  public static <T> T mergeModels(final Class<? extends T> aClass, final T... implementations) {
    final MergingInvocationHandler<T> handler = new MergingInvocationHandler<T>(implementations);
    return mergeModels(handler, aClass, implementations);
  }

  public static <T> T mergeModels(final Class<? extends T> aClass, final Collection<? extends T> implementations) {
    return (T) mergeModels(aClass, implementations.toArray());
  }


  public static <T>T mergeModels(final MergingInvocationHandler<T> handler,
                                  final Class<? extends T> aClass,
                                  final T... implementations) {
    final Set<Class> commonClasses = getCommonClasses(implementations);
    commonClasses.add(MergedObject.class);
    commonClasses.remove(aClass);
    return AdvancedProxy.createProxy(handler, aClass,
                                     commonClasses.toArray(new Class[commonClasses.size()]));
  }

  @Nullable
  public static <T, V> V getImplementation(final T element, final Class<V> clazz) {
    if (element == null) return null;
    CommonProcessors.FindFirstProcessor<T> processor = new CommonProcessors.FindFirstProcessor<T>() {
      public boolean process(final T t) {
        return !clazz.isAssignableFrom(t.getClass()) || super.process(t);
      }
    };
    new ImplementationProcessor<T>(processor).process(element);
    return (V)processor.getFoundValue();
  }

  @NotNull
  public static <T> List<T> getFilteredImplementations(final T element) {
    final CommonProcessors.CollectProcessor<T> processor = new CommonProcessors.CollectProcessor<T>(new ArrayList<T>());
    new ImplementationProcessor<T>(processor).process(element);
    return (List<T>)processor.getResults();
  }

  @NotNull
  public static <T> List<T> getImplementations(T element) {
    if (element instanceof MergedObject) {
      final MergedObject<T> mergedObject = (MergedObject<T>)element;
      return mergedObject.getImplementations();
    }
    else if (element != null) {
      return Collections.singletonList(element);
    }
    else {
      return Collections.emptyList();
    }
  }

  private static void addAllInterfaces(Class aClass, List<Class> list) {
    final Class[] interfaces = aClass.getInterfaces();
    list.addAll(Arrays.asList(interfaces));
    for (Class anInterface : interfaces) {
      addAllInterfaces(anInterface, list);
    }
  }

  public static Set<Class> getCommonClasses(final Object... implementations) {
    final HashSet<Class> set = new HashSet<Class>();
    final ArrayList<Class> list = new ArrayList<Class>();
    addAllInterfaces(implementations[0].getClass(), list);
    set.addAll(list);
    for (int i = 1; i < implementations.length; i++) {
      final ArrayList<Class> list1 = new ArrayList<Class>();
      addAllInterfaces(implementations[i].getClass(), list1);
      set.retainAll(list1);
    }
    return set;
  }


  public static class MergingInvocationHandler<T> implements InvocationHandler {
    private static final Map<Class<? extends Object>,Method> ourPrimaryKeyMethods = new HashMap<Class<? extends Object>, Method>();
    private T[] myImplementations;
    private Set<JavaMethodSignature> signaturesNotToMerge = Collections.emptySet();

    public MergingInvocationHandler(final T... implementations) {
      setImplementations(implementations);
    }

    public MergingInvocationHandler() {
    }

    protected final void setImplementations(final T[] implementations) {
      myImplementations = implementations;
    }

    protected Object getPrimaryKey(Object implementation) throws IllegalAccessException, InvocationTargetException {
      if (implementation instanceof GenericValue) return ((GenericValue)implementation).getValue();
      final Method method = getPrimaryKeyMethod(implementation.getClass());
      if (method == null) return null;

      final Object o = method.invoke(implementation);
      return GenericValue.class.isAssignableFrom(method.getReturnType()) ? ((GenericValue)o).getValue() : o;
    }

    @Nullable
    private Method getPrimaryKeyMethod(final Class<? extends Object> aClass) {
      Method method = ourPrimaryKeyMethods.get(aClass);
      if (method == null) {
        if (ourPrimaryKeyMethods.containsKey(aClass)) return null;

        for (final Method method1 : aClass.getMethods()) {
          if ((method = findPrimaryKeyAnnotatedMethod(method1, aClass)) != null) {
            break;
          }
        }
        ourPrimaryKeyMethods.put(aClass, method);
      }
      return method;
    }

    private Method findPrimaryKeyAnnotatedMethod(final Method method, final Class aClass) {
      return method.getReturnType() != void.class
             && method.getParameterTypes().length == 0?
             JavaMethodSignature.getSignature(method).findAnnotatedMethod(PrimaryKey.class, aClass): null;
    }


    public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
      if (Object.class.equals(method.getDeclaringClass())) {
        @NonNls String methodName = method.getName();
        if ("toString".equals(methodName)) {
          return "Merger: " + Arrays.asList(myImplementations);
        }
        if ("hashCode".equals(methodName)) {
          return Arrays.hashCode(myImplementations);
        }
        if ("equals".equals(methodName)) {
          return args[0] != null && ((MergedObject)args[0]).getImplementations().equals(Arrays.asList(myImplementations));
        }
        return null;
      }

      try {
        if (MergedObject.class.equals(method.getDeclaringClass())) {
          @NonNls String methodName = method.getName();
          if ("getImplementations".equals(methodName)) {
            return Arrays.asList(myImplementations);
          }
          else assert false;
        }
        final Class returnType = method.getReturnType();
        if (signaturesNotToMerge.size() > 0) {
          final JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
          if (signaturesNotToMerge.contains(signature)) {
            for (final T t : myImplementations) {
              final Object o = method.invoke(t, args);
              if (o != null) return o;
            }
            return null;
          }
        }
        if (Collection.class.isAssignableFrom(returnType)) {
          return getMergedImplementations(method, args,
                                          DomUtil.getRawType(DomImplUtil.extractCollectionElementType(method.getGenericReturnType())));
        }

        if (GenericValue.class.isAssignableFrom(returnType)) {
          return new MergedGenericValue(method, args);
        }

        if (void.class == returnType) {
          for (final T t : myImplementations) {
            method.invoke(t, args);
          }
          return null;
        }

        List<Object> results = getMergedImplementations(method, args, method.getReturnType());

        return results.isEmpty() ? null : results.get(0);
      }
      catch (InvocationTargetException ex) {
        throw ex.getTargetException();
      }
    }

    private List<Object> getMergedImplementations(final Method method, final Object[] args, final Class returnType)
      throws IllegalAccessException, InvocationTargetException {

      final List<Object> results = new ArrayList<Object>();

      if (returnType.isInterface() /*&& !GenericValue.class.isAssignableFrom(returnType)*/) {
        final List<Object> orderedPrimaryKeys = new ArrayList<Object>();
        final Map<Object, List<Object>> map = new HashMap<Object, List<Object>>();
        for (final T t : myImplementations) {
          final Object o = method.invoke(t, args);
          if (o instanceof Collection) {
            for (final Object o1 : (Collection)o) {
              addToMaps(o1, orderedPrimaryKeys, map, results, false);
            }
          }
          else if (o != null) {
            addToMaps(o, orderedPrimaryKeys, map, results, true);
          }

        }

        for (final Object primaryKey : orderedPrimaryKeys) {
          results.add(mergeImplementations(returnType, map.get(primaryKey).toArray()));
        }
      }
      else {
        HashSet<Object> map = new HashSet<Object>();
        for (final T t : myImplementations) {
          final Object o = method.invoke(t, args);
          if (o instanceof Collection) {
            map.addAll((Collection<Object>)o);
          }
          else if (o != null) {
            map.add(o);
            break;
          }
        }
        results.addAll(map);
      }
      return results;
    }

    protected Object mergeImplementations(final Class returnType, final Object... implementations) {
      if (implementations.length == 1) {
        return implementations[0];
      }
      return mergeModels(returnType, implementations);
    }

    private boolean addToMaps(final Object o,
                              final List<Object> orderedPrimaryKeys,
                              final Map<Object, List<Object>> map,
                              final List<Object> results,
                              final boolean mergeIfPKNull) throws IllegalAccessException, InvocationTargetException {
      final Object primaryKey = getPrimaryKey(o);
      if (primaryKey != null || mergeIfPKNull) {
        List<Object> list;
        if (!map.containsKey(primaryKey)) {
          orderedPrimaryKeys.add(primaryKey);
          list = new ArrayList<Object>();
          map.put(primaryKey, list);
        }
        else {
          list = map.get(primaryKey);
        }
        list.add(o);
        return false;
      }

      results.add(o);
      return true;
    }

    public class MergedGenericValue extends ReadOnlyGenericValue implements MergedObject<GenericValue> {
      private final Method myMethod;
      private final Object[] myArgs;

      public MergedGenericValue(final Method method, final Object[] args) {
        myMethod = method;
        myArgs = args;
      }

      public <V extends GenericValue> V findImplementation(Class<V> clazz) {
        for (final T t : myImplementations) {
          try {
            GenericValue genericValue = (GenericValue)myMethod.invoke(t, myArgs);
            if (genericValue!=null && clazz.isAssignableFrom(genericValue.getClass())) {
              return (V)genericValue;
            }
          }
          catch (IllegalAccessException e) {
            LOG.error(e);
          }
          catch (InvocationTargetException e) {
            LOG.error(e);
          }
        }
        return null;
      }

      public List<GenericValue> getImplementations() {
        ArrayList<GenericValue> result = new ArrayList<GenericValue>(myImplementations.length);
        for (final T t : myImplementations) {
          try {
            GenericValue genericValue = (GenericValue)myMethod.invoke(t, myArgs);
            result.add(genericValue);
          }
          catch (IllegalAccessException e) {
            LOG.error(e);
          }
          catch (InvocationTargetException e) {
            LOG.error(e);
          }
        }
        return result;
      }

      private GenericValue findGenericValue() {
        for (final T t : myImplementations) {
          try {
            GenericValue genericValue = (GenericValue)myMethod.invoke(t, myArgs);
            if (genericValue != null) {
              final Object value = genericValue.getValue();
              if (value != null) {
                return genericValue;
              }
            }
          }
          catch (IllegalAccessException e) {
            LOG.error(e);
          }
          catch (InvocationTargetException e) {
            final Throwable throwable = e.getTargetException();
            if (throwable instanceof RuntimeException) {
              throw (RuntimeException)throwable;
            }
            else if (throwable instanceof Error) {
              throw (Error)throwable;
            }
            LOG.error(throwable);
          }
        }
        return null;
      }

      public Object getValue() {
        final GenericValue genericValue = findGenericValue();
        return genericValue != null ? genericValue.getValue() : null;
      }

      public String getStringValue() {
        final GenericValue genericValue = findGenericValue();
        return genericValue != null ? genericValue.getStringValue() : super.getStringValue();
      }
    }
  }
}

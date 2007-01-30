package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.ArrayList;

public class ReflectionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ReflectionUtil");

  private ReflectionUtil() {
  }

  @Nullable
  public static Method getMethod(Class aClass, @NonNls String name, Class... paramTypes) {
    try {
      return aClass.getMethod(name, paramTypes);
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
      return null;
    }
  }

  @Nullable
  public static Type resolveVariable(TypeVariable variable, final Class classType) {
    final Class aClass = getRawType(classType);
    int index = ContainerUtil.findByEquals(ReflectionCache.getTypeParameters(aClass), variable);
    if (index >= 0) {
      return variable;
    }

    final Class[] classes = ReflectionCache.getInterfaces(aClass);
    final Type[] genericInterfaces = ReflectionCache.getGenericInterfaces(aClass);
    for (int i = 0; i < classes.length; i++) {
      Class anInterface = classes[i];
      final Type resolved = resolveVariable(variable, anInterface);
      if (resolved instanceof Class || resolved instanceof ParameterizedType) {
        return resolved;
      }
      if (resolved instanceof TypeVariable) {
        final TypeVariable typeVariable = (TypeVariable)resolved;
        index = ContainerUtil.findByEquals(ReflectionCache.getTypeParameters(anInterface), typeVariable);
        if (index < 0) {
          LOG.assertTrue(false, "Cannot resolve type variable:\n" +
                              "typeVariable = " + typeVariable + "\n" +
                              "genericDeclaration = " + declarationToString(typeVariable.getGenericDeclaration()) + "\n" +
                              "searching in " + declarationToString(anInterface));
        }
        final Type type = genericInterfaces[i];
        if (type instanceof Class) {
          return Object.class;
        }
        if (type instanceof ParameterizedType) {
          return getActualTypeArguments(((ParameterizedType)type))[index];
        }
        throw new AssertionError("Invalid type: " + type);
      }
    }
    return null;
  }

  public static String declarationToString(final GenericDeclaration anInterface) {
    return anInterface.toString() + Arrays.asList(anInterface.getTypeParameters()) + " loaded by " + ((Class)anInterface).getClassLoader();
  }

  public static Class<?> getRawType(Type type) {
    if (type instanceof Class) {
      return (Class)type;
    }
    if (type instanceof ParameterizedType) {
      return getRawType(((ParameterizedType)type).getRawType());
    }
    if (type instanceof GenericArrayType) {
      //todo[peter] don't create new instance each time
      return Array.newInstance(getRawType(((GenericArrayType)type).getGenericComponentType()), 0).getClass();
    }
    assert false : type;
    return null;
  }

  public static Type[] getActualTypeArguments(final ParameterizedType parameterizedType) {
    return ReflectionCache.getActualTypeArguments(parameterizedType);
  }

  @Nullable
  public static Class<?> substituteGenericType(final Type genericType, final Type classType) {
    if (genericType instanceof TypeVariable) {
      final Class<?> aClass = getRawType(classType);
      final Type type = resolveVariable((TypeVariable)genericType, aClass);
      if (type instanceof Class) {
        return (Class)type;
      }
      if (type instanceof ParameterizedType) {
        return (Class<?>)((ParameterizedType)type).getRawType();
      }
      if (type instanceof TypeVariable && classType instanceof ParameterizedType) {
        final int index = ContainerUtil.findByEquals(ReflectionCache.getTypeParameters(aClass), type);
        if (index >= 0) {
          return getRawType(getActualTypeArguments(((ParameterizedType)classType))[index]);
        }
      }
    } else {
      return getRawType(genericType);
    }
    return null;
  }

  public static ArrayList<Field> collectFields(Class clazz) {
    ArrayList<Field> result = new ArrayList<Field>();
    collectFields(clazz, result);
    return result;
  }

  public static Field findField(Class clazz, Class type, String name) throws NoSuchFieldException {
    final ArrayList<Field> fields = collectFields(clazz);
    for (Field each : fields) {
      if (name.equals(each.getName()) && each.getType().equals(type)) return each;
    }

    throw new NoSuchFieldException("Class: " + clazz + " name: " + name + " type: " + type);
  }

  private static void collectFields(final Class clazz, final ArrayList<Field> result) {
    final Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      result.add(field);
    }
    final Class superClass = clazz.getSuperclass();
    if (superClass != null) {
      collectFields(superClass, result);
    }
    final Class[] interfaces = clazz.getInterfaces();
    for (Class each : interfaces) {
      collectFields(each, result);
    }
  }

  public static void resetField(Class clazz, Class type, String name) throws NoSuchFieldException, IllegalAccessException {
    resetField(null, findField(clazz, type, name));
  }
  public static void resetField(Object object, Class type, String name) throws NoSuchFieldException, IllegalAccessException {
    resetField(object, findField(object.getClass(), type, name));
  }

  private static void resetField(@Nullable final Object object, final Field field) throws IllegalAccessException {
    field.setAccessible(true);
    Class<?> type = field.getType();
    if (type.isPrimitive()) {
      if (boolean.class.equals(type)) {
        field.set(object, Boolean.FALSE);
      } else if (int.class.equals(type)){
        field.set(object, new Integer(0));
      } else if (double.class.equals(type)) {
        field.set(object, new Double(0));
      } else if (float.class.equals(type)) {
        field.set(object, new Float(0));
      }
    } else {
      field.set(object, null);
    }
  }

}

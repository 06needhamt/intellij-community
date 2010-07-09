package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.NullableFunction;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyQualifiedExpression;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyUnionType implements PyType {
  private final List<PyType> myMembers;

  public PyUnionType(Collection<PyType> members) {
    myMembers = new ArrayList<PyType>(members);
  }

  @Nullable
  public List<? extends PsiElement> resolveMember(String name, AccessDirection direction) {
    SmartList<PsiElement> ret = new SmartList<PsiElement>();
    boolean all_nulls = true;
    for (PyType member : myMembers) {
      if (member != null) {
        List<? extends PsiElement> result = member.resolveMember(name, direction);
        if (result != null) {
          all_nulls = false;
          ret.addAll(result);
        }
      }
    }
    return all_nulls ? null : ret;
  }

  public Object[] getCompletionVariants(PyQualifiedExpression referenceExpression, ProcessingContext context) {
    Set<Object> variants = new HashSet<Object>();
    for (PyType member : myMembers) {
      if (member != null) {
        Collections.addAll(variants, member.getCompletionVariants(referenceExpression, context));
      }
    }
    return variants.toArray(new Object[variants.size()]);
  }

  public String getName() {
    return "one of (" + StringUtil.join(myMembers, new NullableFunction<PyType, String>() {
      public String fun(PyType pyType) {
        return pyType == null ? "unknown" : pyType.getName();
      }
    }, ", ") + ")";
  }

  @Nullable
  public static PyType union(PyType type1, PyType type2, boolean allowNulls) {
    if (allowNulls) {
      if (type1 == null) {
        return type2;
      }
      if (type2 == null) {
        return type1;
      }
    }
    return union(type1, type2);
  }

  @Nullable
  public static PyType union(PyType type1, PyType type2) {
    if (type1 instanceof PyTupleType && type2 instanceof PyTupleType) {
      final PyTupleType tupleType1 = (PyTupleType)type1;
      final PyTupleType tupleType2 = (PyTupleType)type2;
      if (tupleType1.getElementCount() == tupleType2.getElementCount()) {
        int count = tupleType1.getElementCount();
        PyType[] members = new PyType[count];
        for (int i = 0; i < count; i++) {
          members[i] = union(tupleType1.getElementType(i), tupleType2.getElementType(i));
        }
        return new PyTupleType(tupleType1, members);
      }
    }
    Set<PyType> members = new HashSet<PyType>();
    if (type1 instanceof PyUnionType) {
      members.addAll(((PyUnionType)type1).myMembers);
    }
    else {
      members.add(type1);
    }
    if (type2 instanceof PyUnionType) {
      members.addAll(((PyUnionType)type2).myMembers);
    }
    else {
      members.add(type2);
    }
    if (members.size() == 1) {
      return members.iterator().next();
    }
    return new PyUnionType(members);
  }

  public boolean isWeak() {
    for (PyType member : myMembers) {
      if (member == null) {
        return true;
      }
    }
    return false;
  }
}

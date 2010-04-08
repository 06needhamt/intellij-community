package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Ref resolution routines.
 * User: yole
 * Date: 14.06.2005
 */
public class PyResolveUtil {

  private PyResolveUtil() {
  }


  /**
   * Tries to find nearest parent that conceals names defined inside it. Such elements are 'class' and 'def':
   * anything defined within it does not seep to the namespace below them, but is concealed within.
   * @param elt starting point of search.
   * @return 'class' or 'def' element, or null if not found.
   */
  @Nullable
  public static PsiElement getConcealingParent(PsiElement elt) {
    if (elt == null) {
      return null;
    }
    PsiElement parent = elt.getParent();
    while(parent != null) {
      if (parent instanceof PyClass || parent instanceof PyFunction) {
        return parent;
      }
      if (parent instanceof PsiFile) {
        break;
      }
      parent = parent.getParent();
    }
    return null;
  }

  /**
   * Returns closest previous node of given class, as input file would have it.
   * @param elt node from which to look for a previous atatement.
   * @param classes which class of the previous nodes to find.
   * @return previous statement, or null.
   */
  @Nullable
  public static PsiElement getPrevNodeOf(PsiElement elt, Condition<PsiElement> condition) {
    PsiElement seeker = elt;
    while (seeker != null) {
      PsiElement feeler = seeker.getPrevSibling();
      if ((feeler instanceof PyFunction || feeler instanceof PyClass) && condition.value(feeler)) {
        return feeler;
      }
      if (feeler != null) {
        seeker = PsiTreeUtil.getDeepestLast(feeler);
      }
      else { // we were the first subnode
        // find something above the parent node we've not exhausted yet
        seeker = seeker.getParent();
        if (seeker instanceof PsiFile) return null; // all file nodes have been looked up, in vain
      }
      // ??? if (seeker instanceof NameDefiner) return seeker;
      if (condition.value(seeker)) return seeker;
    }
    // here elt is null or a PsiFile is not up in the parent chain.
    return null;
  }

  @Nullable
  public static PsiElement getPrevNodeOf(PsiElement elt, PsiScopeProcessor proc) {
    if (elt instanceof PsiFile) return null;  // no sense to get the previous node of a file
    if (proc instanceof PyClassScopeProcessor) {
      return getPrevNodeOf(elt, ((PyClassScopeProcessor)proc).getTargetCondition());
    }
    else return getPrevNodeOf(elt, IS_NAME_DEFINER);
  }

  public static final Condition<PsiElement> IS_NAME_DEFINER = new Condition<PsiElement>() {
    public boolean value(PsiElement psiElement) {
      return psiElement instanceof NameDefiner;
    }
  };

  /**
   * Crawls up the PSI tree, checking nodes as if crawling backwards through source lexemes.
   * @param processor a visitor that says when the crawl is done and collects info.
   * @param elt element from which we start (not checked by processor); if null, the search immediately returns null.
   * @param roof if not null, search continues only below the roof and including it.
   * @param fromunder if true, begin search not above elt, but from a [possibly imaginary] node right below elt; so elt gets analyzed, too.
   * @return first element that the processor accepted.
   */
  @Nullable
  public static PsiElement treeCrawlUp(PsiScopeProcessor processor, boolean fromunder, PsiElement elt, PsiElement roof) {
    if (elt == null) return null; // can't find anyway.
    PsiElement seeker = elt;
    PsiElement cap = getConcealingParent(elt);
    final boolean is_outside_param_list = PsiTreeUtil.getParentOfType(elt, PyParameterList.class) == null;
    do {
      ProgressManager.checkCanceled();
      if (!seeker.isValid()) return null; 
      if (fromunder) {
        fromunder = false; // only honour fromunder once per call
        seeker = getPrevNodeOf(PsiTreeUtil.getDeepestLast(seeker), processor);
      }
      else { // main case
        seeker = getPrevNodeOf(seeker, processor);
      }
      // aren't we in the same defining assignment, global, etc?
      if ((seeker instanceof NameDefiner) && ((NameDefiner)seeker).mustResolveOutside() && PsiTreeUtil.isAncestor(seeker, elt, true)) {
        seeker = getPrevNodeOf(seeker, processor);
      }
      // maybe we're under a cap?
      while (true) {
        PsiElement local_cap = getConcealingParent(seeker);
        if (local_cap == null) break; // seeker is in global context
        if (local_cap == cap) break; // seeker is in the same context as elt
        if ((cap != null) && PsiTreeUtil.isAncestor(local_cap, cap, true)) break; // seeker is in a context above elt's
        if (
            (local_cap != elt) && // elt isn't the cap of seeker itself
            ((cap == null) || !PsiTreeUtil.isAncestor(local_cap, cap, true)) // elt's cap is not under local cap
        ) { // only look at local cap and above
          if (local_cap instanceof NameDefiner) seeker = local_cap;
          else seeker = getPrevNodeOf(local_cap, processor);
        }
        else break; // seeker is contextually under elt already
      }
      // are we still under the roof?
      if ((roof != null) && (seeker != null) && ! PsiTreeUtil.isAncestor(roof, seeker, false)) return null;
      // maybe we're capped by a class? param lists are not capped though syntactically inside the function.  
      if (is_outside_param_list && refersFromMethodToClass(cap, seeker)) continue;
      // check what we got
      if (seeker != null) {
        if (!processor.execute(seeker, ResolveState.initial())) {
          if (processor instanceof ResolveProcessor) {
            return ((ResolveProcessor)processor).getResult();
          }
          else return seeker; // can't point to exact element, but somewhere here
        }
      }
    } while (seeker != null);
    return null;
  }

  /**
   * Assumes that the start element is inside a function definition. Runs processor through the outer contexts of that function,
   * from closest to outermost. Does not scan anything within the function.
   * Makes sense for finding names that are not visible at definition time but are visible at call time.
   * @param start element to start from; if it is not within a function, nothing happens.
   * @param processor should be ready to see duplicate names defined differently in nested contexts.
   * @return the element for which processor's {@code execute()} returned true, or null.
   */
  @Nullable
  public static PsiElement scanOuterContext(@NotNull PsiScopeProcessor processor, @NotNull PsiElement start) {
    // if we're under a cap, an external object that we want to use might be also defined below us.
    // look through all contexts, closest first.
    PsiElement ret = null;
    PsiElement our_cap = PsiTreeUtil.getParentOfType(start, PyFunction.class);
    if (our_cap != null) {
      PsiElement cap = our_cap;
      while (true) {
        cap = PsiTreeUtil.getParentOfType(cap, PyFunction.class);
        if (cap == null) cap = start.getContainingFile();
        ret = treeCrawlUp(processor, true, cap);
        if ((ret != null) && !PsiTreeUtil.isAncestor(our_cap, ret, true)) { // found something and it is below our cap
          // maybe we're in a method, and what we found is in its class context?
          if (! refersFromMethodToClass(our_cap, ret)) {
            break; // not in method -> must be all right
          }
        }
        if (cap instanceof PsiFile) break; // file level, can't try more
      }
    }
    return ret;
  }


  /**
   * @param inner an element presumably inside a method within a class, or a method itself.
   * @param outer an element presumably in the class context.
   * @return true if an outer element is in a class context, while the inner is a method or function inside it.
   * @see PyResolveUtil#getConcealingParent(com.intellij.psi.PsiElement)
   */
  protected static boolean refersFromMethodToClass(final PsiElement inner, final PsiElement outer) {
    return (
      (getConcealingParent(outer) instanceof PyClass) && // outer is in a class context
      (PsiTreeUtil.getParentOfType(inner, PyFunction.class, false) != null) // inner is a function or method within the class
    );
  }

  /**
   * Crawls up the PSI tree, checking nodes as if crawling backwards through source lexemes.
   * @param processor a visitor that says when the crawl is done and collects info.
   * @param fromunder if true, search not above elt, but from a [possibly imaginary] node right below elt; so elt gets analyzed, too.
   * @param elt element from which we start (not checked by processor); if null, the search immediately fails.
   * @return first element that the processor accepted.
   */
  @Nullable
  public static PsiElement treeCrawlUp(PsiScopeProcessor processor, boolean fromunder, PsiElement elt) {
    return treeCrawlUp(processor, fromunder, elt, null);
  }

  /**
   * Returns treeCrawlUp(processor, elt, false). A convenience method.
   * @see PyResolveUtil#treeCrawlUp(com.intellij.psi.scope.PsiScopeProcessor,boolean,com.intellij.psi.PsiElement)
   */
  @Nullable
  public static PsiElement treeCrawlUp(PsiScopeProcessor processor, PsiElement elt) {
    return treeCrawlUp(processor, false, elt);
  }


  /**
   * Tries to match two [qualified] reference expression paths by names; target must be a 'sublist' of source to match.
   * E.g., 'a.b.c.d' and 'a.b.c' would match, while 'a.b.c' and 'a.b.c.d' would not. Eqaully, 'a.b.c' and 'a.b.d' would not match.
   * If either source or target is null, false is returned.
   * @see #unwindQualifiers(PyQualifiedExpression) .
   * @param source_path expression path to match (the longer list of qualifiers).
   * @param target_path expression path to match against (hopeful sublist of qualifiers of source).
   * @return true if source matches target.
   */
  public static <S extends PyExpression, T extends PyExpression> boolean pathsMatch(List<S> source_path, List<T> target_path) {
    // turn qualifiers into lists
    if ((source_path == null) || (target_path == null)) return false;
    // compare until target is exhausted
    Iterator<S> source_iter = source_path.iterator();
    for (final T target_elt : target_path) {
      if (source_iter.hasNext()) {
        S source_elt = source_iter.next();
        if (!target_elt.getText().equals(source_elt.getText())) return false;
      }
      else return false; // source exhausted before target
    }
    return true;
  }

  /**
   * Unwinds a [multi-level] qualified expression into a path, as seen in source text, i.e. outermost qualifier first.
   * If any qualifier happens to be not a PyQualifiedExpression, or expr is null, null is returned.
   * @param expr an experssion to unwind.
   * @return path as a list of ref expressions, or null.
   */
  @Nullable
  public static <T extends PyQualifiedExpression> List<T> unwindQualifiers(final T expr) {
    final List<T> path = new LinkedList<T>();
    PyExpression maybe_step;
    T step = expr;
    try {
      while (step != null) {
        path.add(0, step);
        maybe_step = step.getQualifier();
        step = (T)maybe_step;
      }
    }
    catch (ClassCastException e) {
      return null;
    }
    return path;
  }

  public static String toPath(PyQualifiedExpression expr, String separator) {
    if (expr == null) return "";
    List<PyQualifiedExpression> path = unwindQualifiers(expr);
    if (path != null) {
      StringBuilder buf = new StringBuilder();
      boolean is_not_first = false;
      for (PyQualifiedExpression ex : path) {
        if (is_not_first) buf.append(separator);
        else is_not_first = true;
        buf.append(ex.getName());
      }
      return buf.toString();
    }
    else {
      String s = expr.getName();
      return s != null? s : "";
    }
  }

  /**
   * Accepts only targets that are not the given object.
   */
  public static class FilterNotInstance implements Condition<PsiElement> {
    Object instance;

    public FilterNotInstance(Object instance) {
      this.instance = instance;
    }

    public boolean value(final PsiElement target) {
      return (instance != target);
    }

  }

  /**
   * Accepts only names not contained in a given collection.
   */
  public static class FilterNameNotIn implements Condition<PsiElement> {
    private final Collection<String> myNames;

    public FilterNameNotIn(Collection<String> names) {
      myNames = names;
    }

    public boolean value(PsiElement target) {
      if (target instanceof PsiNamedElement) {
        return !myNames.contains(((PsiNamedElement)target).getName());
      }
      else if (target instanceof PyReferenceExpression) {
        return !myNames.contains(((PyReferenceExpression)target).getReferencedName());
      }
      else if (target instanceof NameDefiner) {
        NameDefiner definer = (NameDefiner)target;
        for (PyElement expr : definer.iterateNames()) {
          if (expr != null) {
            String referencedName = expr.getName();
            if (myNames.contains(referencedName)) return false;
          }
        }
      }
      return true; // nothing failed us
    }
  }

}

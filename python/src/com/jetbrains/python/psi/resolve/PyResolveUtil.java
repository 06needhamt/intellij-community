package com.jetbrains.python.psi.resolve;

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Ref resolution routines.
 * User: dcheryasov
 * Date: 14.06.2005
 */
public class PyResolveUtil {

  private PyResolveUtil() {
  }


  /**
   * Returns closest previous node of given class, as input file would have it.
   *
   * @param elt       node from which to look for a previous statement.
   * @param condition determines where a node is considered found.
   * @return previous statement, or null.
   */
  @Nullable
  public static PsiElement getPrevNodeOf(PsiElement elt, TokenSet elementTypes) {
    ASTNode seeker = elt.getNode();
    while (seeker != null) {
      ASTNode feeler = seeker.getTreePrev();
      if (feeler != null &&
          (feeler.getElementType() == PyElementTypes.FUNCTION_DECLARATION ||
           feeler.getElementType() == PyElementTypes.CLASS_DECLARATION) &&
          elementTypes.contains(feeler.getElementType())) {
        return feeler.getPsi();
      }
      if (feeler != null) {
        seeker = TreeUtil.getLastChild(feeler);
      }
      else { // we were the first subnode
        // find something above the parent node we've not exhausted yet
        seeker = seeker.getTreeParent();
        if (seeker instanceof FileASTNode) return null; // all file nodes have been looked up, in vain
      }
      if (seeker != null && elementTypes.contains(seeker.getElementType())) {
        return seeker.getPsi();
      }
    }
    // here elt is null or a PsiFile is not up in the parent chain.
    return null;
  }

  @Nullable
  public static PsiElement getPrevNodeOf(PsiElement elt, PsiScopeProcessor proc) {
    if (elt instanceof PsiFile) return null;  // no sense to get the previous node of a file
    if (proc instanceof PyClassScopeProcessor) {
      return getPrevNodeOf(elt, ((PyClassScopeProcessor)proc).getTargetTokenSet());
    }
    else {
      return getPrevNodeOf(elt, PythonDialectsTokenSetProvider.INSTANCE.getNameDefinerTokens());
    }
  }

  /**
   * Crawls up the PSI tree, checking nodes as if crawling backwards through source lexemes.
   *
   * @param processor a visitor that says when the crawl is done and collects info.
   * @param elt       element from which we start (not checked by processor); if null, the search immediately returns null.
   * @param roof      if not null, search continues only below the roof and including it.
   * @param fromunder if true, begin search not above elt, but from a [possibly imaginary] node right below elt; so elt gets analyzed, too.
   * @return first element that the processor accepted.
   */
  @Nullable
  public static PsiElement treeCrawlUp(PsiScopeProcessor processor, boolean fromunder, PsiElement elt, PsiElement roof) {
    if (elt == null || !elt.isValid()) return null; // can't find anyway.
    PsiElement seeker = elt;
    PsiElement cap = PyUtil.getConcealingParent(elt);
    PyFunction capFunction = cap != null ? PsiTreeUtil.getParentOfType(cap, PyFunction.class, false) : null;
    final boolean is_outside_param_list = PsiTreeUtil.getParentOfType(elt, PyParameterList.class) == null;
    do {
      ProgressManager.checkCanceled();
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
        PsiElement local_cap = PyUtil.getConcealingParent(seeker);
        if (local_cap == null) break; // seeker is in global context
        if (local_cap == cap) break; // seeker is in the same context as elt
        if ((cap != null) && PsiTreeUtil.isAncestor(local_cap, cap, true)) break; // seeker is in a context above elt's
        if (
          (local_cap != elt) && // elt isn't the cap of seeker itself
          ((cap == null) || !PsiTreeUtil.isAncestor(local_cap, cap, true)) // elt's cap is not under local cap
          ) { // only look at local cap and above
          if (local_cap instanceof NameDefiner) {
            seeker = local_cap;
          }
          else {
            seeker = getPrevNodeOf(local_cap, processor);
          }
        }
        else {
          break;
        } // seeker is contextually under elt already
      }
      // are we still under the roof?
      if ((roof != null) && (seeker != null) && !PsiTreeUtil.isAncestor(roof, seeker, false)) return null;
      // maybe we're capped by a class? param lists are not capped though syntactically inside the function.
      if (is_outside_param_list && refersFromMethodToClass(capFunction, seeker)) continue;
      // names defined in a comprehension element are only visible inside it or the list comp expressions directly above it
      if (seeker instanceof PyComprehensionElement && !PsiTreeUtil.isAncestor(seeker, elt, false)) {
        continue;
      }
      // check what we got
      if (seeker != null) {
        if (!processor.execute(seeker, ResolveState.initial())) {
          if (processor instanceof ResolveProcessor) {
            return ((ResolveProcessor)processor).getResult();
          }
          else {
            return seeker;
          } // can't point to exact element, but somewhere here
        }
      }
    }
    while (seeker != null);
    if (processor instanceof ResolveProcessor) {
      return ((ResolveProcessor)processor).getResult();
    }
    return null;
  }

  /**
   * Assumes that the start element is inside a function definition. Runs processor through the outer contexts of that function,
   * from closest to outermost. Does not scan anything within the function.
   * Makes sense for finding names that are not visible at definition time but are visible at call time.
   *
   * @param start     element to start from; if it is not within a function, nothing happens.
   * @param processor should be ready to see duplicate names defined differently in nested contexts.
   * @return the element for which processor's {@code execute()} returned true, or null.
   */
  @Nullable
  public static PsiElement scanOuterContext(@NotNull PsiScopeProcessor processor, @NotNull PsiElement start) {
    // if we're under a cap, an external object that we want to use might be also defined below us.
    // look through all contexts, closest first.
    PsiElement ret = null;
    PsiElement our_cap = PsiTreeUtil.getParentOfType(start, Callable.class);
    if (our_cap != null) {
      PyFunction innerFunction = PsiTreeUtil.getParentOfType(our_cap, PyFunction.class);
      PsiElement cap = our_cap;
      while (true) {
        cap = PsiTreeUtil.getParentOfType(cap, PyFunction.class);
        if (cap == null) cap = start.getContainingFile();
        ret = treeCrawlUp(processor, true, cap);
        if ((ret != null) && !PsiTreeUtil.isAncestor(our_cap, ret, true)) { // found something and it is below our cap
          // maybe we're in a method, and what we found is in its class context?
          if (!refersFromMethodToClass(innerFunction, ret)) {
            break; // not in method -> must be all right
          }
        }
        if (cap instanceof PsiFile) break; // file level, can't try more
      }
    }
    return ret;
  }


  /**
   * @param innerFunction a method, presumably inside the class
   * @param outer an element presumably in the class context.
   * @return true if an outer element is in a class context, while the inner is a method or function inside it.
   * @see com.jetbrains.python.psi.PyUtil#getConcealingParent(com.intellij.psi.PsiElement)
   */
  protected static boolean refersFromMethodToClass(final PyFunction innerFunction, final PsiElement outer) {
    if (innerFunction == null) {
      return false;
    }
    PsiElement outerClass = PyUtil.getConcealingParent(outer);
    if (outerClass instanceof PyClass &&   // outer is in a class context
       innerFunction.getContainingClass() == outerClass) {   // inner is a function or method within the class
      return true;
    }
    return false;
  }

  /**
   * Crawls up the PSI tree, checking nodes as if crawling backwards through source lexemes.
   *
   * @param processor a visitor that says when the crawl is done and collects info.
   * @param fromunder if true, search not above elt, but from a [possibly imaginary] node right below elt; so elt gets analyzed, too.
   * @param elt       element from which we start (not checked by processor); if null, the search immediately fails.
   * @return first element that the processor accepted.
   */
  @Nullable
  public static PsiElement treeCrawlUp(PsiScopeProcessor processor, boolean fromunder, PsiElement elt) {
    return treeCrawlUp(processor, fromunder, elt, null);
  }

  /**
   * Returns treeCrawlUp(processor, elt, false). A convenience method.
   *
   * @see PyResolveUtil#treeCrawlUp(com.intellij.psi.scope.PsiScopeProcessor, boolean, com.intellij.psi.PsiElement)
   */
  @Nullable
  public static PsiElement treeCrawlUp(PsiScopeProcessor processor, PsiElement elt) {
    return treeCrawlUp(processor, false, elt);
  }


  public static boolean pathsMatchStr(List<String> source_path, List<String> target_path) {
    // turn qualifiers into lists
    if ((source_path == null) || (target_path == null)) return false;
    // compare until target is exhausted
    Iterator<String> source_iter = source_path.iterator();
    for (final String target_elt : target_path) {
      if (source_iter.hasNext()) {
        String source_elt = source_iter.next();
        if (!target_elt.equals(source_elt)) return false;
      }
      else {
        return false;
      } // source exhausted before target
    }
    return true;
  }

  /**
   * Unwinds a multi-level qualified expression into a path, as seen in source text, i.e. outermost qualifier first.
   *
   * @param expr an expression to unwind.
   * @return path as a list of ref expressions.
   */
  @NotNull
  public static List<PyExpression> unwindQualifiers(final PyQualifiedExpression expr) {
    final List<PyExpression> path = new LinkedList<PyExpression>();
    PyQualifiedExpression e = expr;
    while (e != null) {
      path.add(0, e);
      final PyExpression q = e.getQualifier();
      e = q instanceof PyQualifiedExpression ? (PyQualifiedExpression)q : null;
    }
    return path;
  }

  public static List<String> unwindQualifiersAsStrList(final PyQualifiedExpression expr) {
    final List<String> path = new LinkedList<String>();
    PyQualifiedExpression e = expr;
    while (e != null) {
      path.add(0, e.getText());
      final PyExpression q = e.getQualifier();
      e = q instanceof PyQualifiedExpression ? (PyQualifiedExpression)q : null;
    }
    return path;
  }

  public static String toPath(PyQualifiedExpression expr) {
    if (expr == null) return "";
    List<PyExpression> path = unwindQualifiers(expr);
    final PyQualifiedName qName = PyQualifiedName.fromReferenceChain(path);
    if (qName != null) {
      return qName.toString();
    }
    String name = expr.getName();
    if (name != null) {
      return name;
    }
    return "";
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

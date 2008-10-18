/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.impl.PyScopeProcessor;
import com.jetbrains.python.psi.impl.ResolveImportUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Ref resolution routines.
 * User: yole
 * Date: 14.06.2005
 */
public class PyResolveUtil {

  private PyResolveUtil() {
  }


  /**
   * Tries to find nearest parent that conceals nams definded inside it. Such elements are 'class' and 'def':
   * anything defined within it does not seep to the namespace below them, but is concealed within.
   * @param elt starting point of search.
   * @return 'class' or 'def' element, or null if not found.
   */
  @Nullable
  public static PsiElement getConcealingParent(PsiElement elt) {
    return PsiTreeUtil.getParentOfType(elt, PyClass.class, PyFunction.class);
  }

  protected static PsiElement getInnermostChildOf(PsiElement elt) {
    PsiElement feeler = elt;
    PsiElement seeker;
    seeker = feeler;
    // find innermost last child of the subtree we're in
    while (feeler != null) {
      seeker = feeler;
      feeler = feeler.getLastChild();
    }
    return seeker;
  }

  /**
   * Returns closest previous node of given class, as input file would have it.
   * @param elt node from which to look for a previous atatement.
   * @param cls class of the previous node to find.
   * @return previous statement, or null.
   */
  @Nullable
  public static <T> T getPrevNodeOf(PsiElement elt, Class<T> cls) {
    PsiElement seeker = elt;
    while (seeker != null) {
      PsiElement feeler = seeker.getPrevSibling();
      if (feeler != null) {
        seeker = getInnermostChildOf(feeler);
      }
      else { // we were the first subnode
        // find something above the parent node we've not exhausted yet
        seeker = seeker.getParent();
        if (seeker instanceof PyFile) return null; // all file nodes have been looked up, in vain
      }
      if (cls.isInstance(seeker)) return (T)seeker;
    }
    // here elt is null or a PsiFile is not up in the parent chain.
    return null;
  }

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
    do {
      if (fromunder) {
        fromunder = false; // only honour fromunder once per call
        seeker = getPrevNodeOf(getInnermostChildOf(seeker), NameDefiner.class);
      }
      else { // main case
        seeker = getPrevNodeOf(seeker, NameDefiner.class);
      }
      // aren't we in the same defining assignment, global, etc?
      if ((seeker != null) && ((NameDefiner)seeker).mustResolveOutside() && PsiTreeUtil.isAncestor(seeker, elt, true)
      ) {
        seeker = getPrevNodeOf(seeker, NameDefiner.class);
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
          else seeker = getPrevNodeOf(local_cap, NameDefiner.class);
        }
        else break; // seeker is contextually under elt already
      }
      // are we still under the roof?
      if ((roof != null) && (seeker != null) && ! PsiTreeUtil.isAncestor(roof, seeker, false)) return null;
      // maybe we're capped by a class?
      if (refersFromMethodToClass(cap, seeker)) continue;
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

  @Nullable
  public static PsiElement resolveOffContext(@NotNull PyReferenceExpression refex) {
    // if we're under a cap, an external object that we want to use might be also defined below us.
    // look through all contexts, closest first.
    PsiElement ret = null;
    PsiElement our_cap = getConcealingParent(refex);
    ResolveProcessor proc = new ResolveProcessor(refex.getReferencedName()); // processor reusable till first hit
    if (our_cap != null) {
      PsiElement cap = our_cap;
      while (true) {
        cap = getConcealingParent(cap);
        if (cap == null) cap = refex.getContainingFile();
        ret = treeCrawlUp(proc, true, cap);
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
   * @see com.jetbrains.python.psi.PyResolveUtil#getConcealingParent(com.intellij.psi.PsiElement)
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

  public static class CollectProcessor<T extends PsiElement> implements PsiScopeProcessor {

    Class<T>[] my_collectables;
    List<T> my_result;

    public CollectProcessor(Class<T>... collectables) {
      my_collectables = collectables;
      my_result = new ArrayList<T>();
    }

    public boolean execute(final PsiElement element, final ResolveState state) {
      for (Class<T> cls : my_collectables) {
        if (cls.isInstance(element)) {
          my_result.add((T)element);
        }
      }
      return false;
    }

    public <T> T getHint(final Class<T> hintClass) {
      return null;
    }

    public void handleEvent(final Event event, final Object associated) {
    }

    public List<T> getResult() {
      return my_result;
    }
  }

  public static class ResolveProcessor implements PyScopeProcessor {
    private String myName;
    private PsiElement myResult = null;
    private Set<String> mySeen;

    public ResolveProcessor(final String name) {
      myName = name;
      mySeen = new HashSet<String>();
    }

    public PsiElement getResult() {
      return myResult;
    }
    
    static String _nvl(Object s) {
      if (s != null) return "'" + s.toString() + "'";
      else return "null";  // TODO: move to PyNames
    }
    
    public Set<String> getSeen() {
      return mySeen;
    }
    
    public String toString() {
      return _nvl(myName) + ", " + _nvl(myResult);
    }
    
    public boolean execute(PsiElement element, ResolveState substitutor) {
      if (element instanceof PyFile) {
        final VirtualFile file = ((PyFile)element).getVirtualFile();
        if (file != null) {
          if (myName.equals(file.getNameWithoutExtension())) {
            myResult = element;
            return false;
          }
          else if (ResolveImportUtil.INIT_PY.equals(file.getName())) {
            VirtualFile dir = file.getParent();
            if ((dir != null) && myName.equals(dir.getName())) {
              myResult = element;
              return false;
            }
          }
        }
      }
      else if (element instanceof PsiNamedElement) {
        if (myName.equals(((PsiNamedElement)element).getName())) {
          myResult = element;
          return false;
        }
      }
      else if (element instanceof PyReferenceExpression) {
        PyReferenceExpression expr = (PyReferenceExpression)element;
        String referencedName = expr.getReferencedName();
        if (referencedName != null && referencedName.equals(myName)) {
          myResult = element;
          return false;
        }
      }
      else if (element instanceof NameDefiner) {
        final NameDefiner definer = (NameDefiner)element;
        PsiElement by_name = definer.getElementNamed(myName);
        if (by_name != null) {
          myResult = by_name;
          return false;
        }
      }

      return true;
    }

    public boolean execute(final PsiElement element, final String asName) {
      if (asName.equals(myName)) {
        myResult = element;
        return false;
      }
      return true;
    }

    @Nullable
    public <T> T getHint(Class<T> hintClass) {
      return null;
    }

    public void handleEvent(Event event, Object associated) {
    }


  }

  public static class MultiResolveProcessor implements PsiScopeProcessor {
    private String _name;
    private List<ResolveResult> _results = new ArrayList<ResolveResult>();

    public MultiResolveProcessor(String name) {
      _name = name;
    }

    public ResolveResult[] getResults() {
      return _results.toArray(new ResolveResult[_results.size()]);
    }

    public boolean execute(PsiElement element, ResolveState substitutor) {
      if (element instanceof PsiNamedElement) {
        if (_name.equals(((PsiNamedElement)element).getName())) {
          _results.add(new PsiElementResolveResult(element));
        }
      }
      else if (element instanceof PyReferenceExpression) {
        PyReferenceExpression expr = (PyReferenceExpression)element;
        String referencedName = expr.getReferencedName();
        if (referencedName != null && referencedName.equals(_name)) {
          _results.add(new PsiElementResolveResult(element));
        }
      }

      return true;
    }

    public <T> T getHint(Class<T> hintClass) {
      return null;
    }

    public void handleEvent(Event event, Object associated) {
    }
  }

  public static class VariantsProcessor implements PsiScopeProcessor {
    private Map<String, LookupElement> myVariants = new HashMap<String, LookupElement>();

    protected String my_notice;

    public VariantsProcessor() {
      // empty
    }

    public VariantsProcessor(final Filter filter) {
      my_filter = filter;
    }

    protected Filter my_filter;

    public void setNotice(@Nullable String notice) {
      my_notice = notice;
    }

    protected void setupItem(LookupItem item) {
      if (my_notice != null) {
        setItemNotice(item, my_notice);
      }
    }

    protected void setItemNotice(final LookupItem item, String notice) {
      item.setAttribute(item.TAIL_TEXT_ATTR, notice);
      item.setAttribute(item.TAIL_TEXT_SMALL_ATTR, "");
    }

    public LookupElement[] getResult() {
      final Collection<LookupElement> variants = myVariants.values();
      return variants.toArray(new LookupElement[variants.size()]);
    }

    public List<LookupElement> getResultList() {
      return new ArrayList<LookupElement>(myVariants.values());
    }

    public boolean execute(PsiElement element, ResolveState substitutor) {
      if (my_filter != null && !my_filter.accept(element)) return true; // skip whatever the filter rejects
      // TODO: refactor to look saner; much code duplication
      if (element instanceof PsiNamedElement) {
        final PsiNamedElement psiNamedElement = (PsiNamedElement)element;
        final String name = psiNamedElement.getName();
        if (!myVariants.containsKey(name)) {
          final LookupItem lookup_item = (LookupItem)LookupElementFactory.getInstance().createLookupElement(psiNamedElement);
          setupItem(lookup_item);
          myVariants.put(name, lookup_item);
        }
      }
      else if (element instanceof PyReferenceExpression) {
        PyReferenceExpression expr = (PyReferenceExpression)element;
        String referencedName = expr.getReferencedName();
        if (referencedName != null && !myVariants.containsKey(referencedName)) {
          final LookupItem lookup_item = (LookupItem)LookupElementFactory.getInstance().createLookupElement(referencedName);
          setupItem(lookup_item);
          myVariants.put(referencedName, lookup_item);
        }
      }
      else if (element instanceof NameDefiner) {
        final NameDefiner definer = (NameDefiner)element;
        for (PyElement expr: definer.iterateNames()) {
          if (expr != null) { // NOTE: maybe rather have SingleIterables skip nulls outright?
            String referencedName = expr.getName();
            if (referencedName != null && !myVariants.containsKey(referencedName)) {
              final LookupItem lookup_item = (LookupItem)LookupElementFactory.getInstance().createLookupElement(referencedName);
              setupItem(lookup_item);
              if (definer instanceof PyImportElement) { // set notice to imported module name if needed 
                PsiElement maybe_from_import = definer.getParent();
                if (maybe_from_import instanceof PyFromImportStatement) {
                  final PyFromImportStatement from_import = (PyFromImportStatement)maybe_from_import;
                  PyReferenceExpression src = from_import.getImportSource();
                  if (src != null) {
                    setItemNotice(lookup_item, " | " + src.getName());
                  }
                }
              }
              myVariants.put(referencedName, lookup_item);
            }
          }
        }
      }

      return true;
    }

    @Nullable
    public <T> T getHint(Class<T> hintClass) {
      return null;
    }

    public void handleEvent(Event event, Object associated) {
    }
  }

  /**
   * A simple interface allowing to filter processor results.
   */
  public interface Filter {
    /**
     * @param target the object a processor is currently looking at.
     * @return true if the object is acceptable as a processor result.
     */
    boolean accept(Object target);
  }

  public static class FilterNotInstance implements Filter {
    Object instance;

    public FilterNotInstance(Object instance) {
      this.instance = instance;
    }

    public boolean accept(final Object target) {
      return (instance != target);
    }
  }

  /**
   * Collects all assignments in context above given element, if they match given naming pattern.
   * Used to track creation of attributes by assignment (e.g in constructor).
   */
  public static class AssignmentCollectProcessor<T extends PyExpression> implements PsiScopeProcessor {

    List<T> my_qualifier;
    List<PyExpression> my_result;
    Set<String> my_seen_names;

    /**
     * Creates an instance to collect assignments of attributes to the object identified by 'qualifier'.
     * E.g. if qualifier = {"foo", "bar"} then assignments like "foo.bar.baz = ..." will be considered.
     * The collection continues up to the point of latest redefinition of the object identified by 'qualifier',
     * that is, up to the point of something like "foo.bar = ..." or "foo = ...".
     * @param qualifier qualifying names, outermost first; must not be empty.
     */
    public AssignmentCollectProcessor(@NotNull List<T> qualifier) {
      assert qualifier.size() > 0;
      my_qualifier = qualifier;
      my_result = new ArrayList<PyExpression>();
      my_seen_names = new HashSet<String>();
    }

    public boolean execute(final PsiElement element, final ResolveState state) {
      if (element instanceof PyAssignmentStatement) {
        final PyAssignmentStatement assignment = (PyAssignmentStatement)element;
        for (PyExpression ex : assignment.getTargets()) {
          if (ex instanceof PyTargetExpression) {
            final PyTargetExpression target = (PyTargetExpression)ex;
            List<PyTargetExpression> quals = unwindQualifiers(target);
            if (quals != null) {
              if  (quals.size() == my_qualifier.size()+1 && pathsMatch(quals, my_qualifier)) {
                // a new attribute follows last qualifier; collect it.
                PyTargetExpression last_elt = quals.get(quals.size() - 1); // last item is the outermost, new, attribute.
                String last_elt_name = last_elt.getName();
                if (!my_seen_names.contains(last_elt_name)) { // no dupes, only remember the latest
                  my_result.add(last_elt);
                  my_seen_names.add(last_elt_name);
                }
              }
              else if (quals.size() < my_qualifier.size()+1 && pathsMatch(my_qualifier, quals)) {
                // qualifier(s) get redefined; collect no more.
                return false;
              }
            }
          }

        }
      }
      return true; // nothing interesting found, continue
    }

    /**
     * @return a collection of exressions (parts of assignment expressions) where new attributes were defined. E.g. for "a.b.c = 1",
     * the expression for 'c' is in the result. 
     */
    @NotNull
    public Collection<PyExpression> getResult() {
      return my_result;
    }

    public <T> T getHint(final Class<T> hintClass) {
      return null;
    }

    public void handleEvent(final Event event, final Object associated) {
      // empty
    }
  }
}

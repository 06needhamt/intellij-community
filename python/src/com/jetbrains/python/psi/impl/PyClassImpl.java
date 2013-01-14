package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.*;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.*;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.stubs.PropertyStubStorage;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author yole
 */
public class PyClassImpl extends PyPresentableElementImpl<PyClassStub> implements PyClass {
  public static final PyClass[] EMPTY_ARRAY = new PyClassImpl[0];

  private List<PyTargetExpression> myInstanceAttributes;
  private final NotNullLazyValue<CachedValue<Boolean>> myNewStyle = new NotNullLazyValue<CachedValue<Boolean>>() {
    @NotNull
    @Override
    protected CachedValue<Boolean> compute() {
      return CachedValuesManager.getManager(getProject()).createCachedValue(new NewStyleCachedValueProvider(), false);
    }
  };

  private volatile Map<String, Property> myPropertyCache;

  @Override
  public PyType getType(@NotNull TypeEvalContext context) {
    return new PyClassTypeImpl(this, true);
  }

  private class NewStyleCachedValueProvider implements CachedValueProvider<Boolean> {
    @Override
    public Result<Boolean> compute() {
      return new Result<Boolean>(calculateNewStyleClass(), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }
  }

  public PyClassImpl(@NotNull ASTNode astNode) {
    super(astNode);
  }

  public PyClassImpl(@NotNull final PyClassStub stub) {
    this(stub, PyElementTypes.CLASS_DECLARATION);
  }

  public PyClassImpl(@NotNull final PyClassStub stub, @NotNull IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final ASTNode nameElement = PyElementGenerator.getInstance(getProject()).createNameIdentifier(name);
    final ASTNode node = getNameNode();
    if (node != null) {
      getNode().replaceChild(node, nameElement);
    }
    return this;
  }

  @Nullable
  @Override
  public String getName() {
    final PyClassStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    else {
      ASTNode node = getNameNode();
      return node != null ? node.getText() : null;
    }
  }

  public PsiElement getNameIdentifier() {
    final ASTNode nameNode = getNameNode();
    return nameNode != null ? nameNode.getPsi() : null;
  }

  public ASTNode getNameNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  public Icon getIcon(int flags) {
    return PlatformIcons.CLASS_ICON;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyClass(this);
  }

  @NotNull
  public PyStatementList getStatementList() {
    final PyStatementList statementList = childToPsi(PyElementTypes.STATEMENT_LIST);
    assert statementList != null : "Statement list missing for class " + getText();
    return statementList;
  }

  @Override
  public PyArgumentList getSuperClassExpressionList() {
    final PyArgumentList argList = PsiTreeUtil.getChildOfType(this, PyArgumentList.class);
    if (argList != null && argList.getFirstChild() != null) {
      return argList;
    }
    return null;
  }

  @NotNull
  public PyExpression[] getSuperClassExpressions() {
    final PyArgumentList argList = getSuperClassExpressionList();
    if (argList != null) {
      return argList.getArguments();
    }
    return PyExpression.EMPTY_ARRAY;
  }

  @NotNull
  public PsiElement[] getSuperClassElements() {
    final PyExpression[] superExpressions = getSuperClassExpressions();
    List<PsiElement> superClasses = new ArrayList<PsiElement>();
    for (PyExpression expr : superExpressions) {
      expr = unfoldClass(expr);
      if (expr instanceof PyReferenceExpression) {
        final PsiPolyVariantReference ref = ((PyReferenceExpression)expr).getReference(PyResolveContext.noProperties());
        final PsiElement result = ref.resolve();
        if (result != null) {
          superClasses.add(result);
          continue;
        }
      }
      superClasses.add(null);
    }
    return PsiUtilCore.toPsiElementArray(superClasses);
  }

  public static PyExpression unfoldClass(PyExpression expression) {
    if (expression instanceof PyCallExpression) {
      PyCallExpression call = (PyCallExpression)expression;
      //noinspection ConstantConditions
      if (call.getCallee() != null && "with_metaclass".equals(call.getCallee().getName()) && call.getArguments().length > 1) {
        expression = call.getArguments()[1];
      }
    }
    return expression;
  }

  public Iterable<PyClassRef> iterateAncestors() {
    // The implementation is manifestly lazy wrt psi scanning and uses stack rather sparingly.
    // It must be more efficient on deep and wide hierarchies, but it was more fun than efficiency that produced it.
    return new AncestorsIterable(this);
  }

  @Override
  public Iterable<PyClass> iterateAncestorClasses() {
    return new AncestorClassesIterable(this);
  }

  public boolean isSubclass(PyClass parent) {
    if (this == parent) {
      return true;
    }
    for (PyClass superclass : iterateAncestorClasses()) {
      if (parent == superclass) return true;
    }
    return false;
  }

  @Override
  public boolean isSubclass(@NotNull String superClassQName) {
    if (superClassQName.equals(getQualifiedName())) {
      return true;
    }
    for (PyClassRef superclass : iterateAncestors()) {
      if (superClassQName.equals(superclass.getQualifiedName())) return true;
    }
    return false;
  }

  public PyDecoratorList getDecoratorList() {
    return childToPsi(PyElementTypes.DECORATOR_LIST);
  }

  @Nullable
  public String getQualifiedName() {
    String name = getName();
    final PyClassStub stub = getStub();
    PsiElement ancestor = stub != null ? stub.getParentStub().getPsi() : getParent();
    while (!(ancestor instanceof PsiFile)) {
      if (ancestor == null) return name;    // can this happen?
      if (ancestor instanceof PyClass) {
        name = ((PyClass)ancestor).getName() + "." + name;
      }
      ancestor = stub != null ? ((StubBasedPsiElement)ancestor).getStub().getParentStub().getPsi() : ancestor.getParent();
    }

    PsiFile psiFile = ((PsiFile)ancestor).getOriginalFile();
    final PyFile builtins = PyBuiltinCache.getInstance(this).getBuiltinsFile();
    if (!psiFile.equals(builtins)) {
      VirtualFile vFile = psiFile.getVirtualFile();
      if (vFile != null) {
        final String packageName = QualifiedNameFinder.findShortestImportableName(this, vFile);
        return packageName + "." + name;
      }
    }
    return name;
  }

  @Override
  public List<String> getSlots() {
    List<String> slots = getOwnSlots();
    if (slots != null) {
      return slots;
    }
    for (PyClass cls : iterateAncestorClasses()) {
      slots = ((PyClassImpl)cls).getOwnSlots();
      if (slots != null) {
        return slots;
      }
    }
    return null;
  }

  @Nullable
  public List<String> getOwnSlots() {
    final PyClassStub stub = getStub();
    if (stub != null) {
      return stub.getSlots();
    }
    return PyFileImpl.getStringListFromTargetExpression(PyNames.SLOTS, getClassAttributes());
  }

  protected List<PyClassRef> getSuperClassesList() {
    if (PyNames.FAKE_OLD_BASE.equals(getName())) {
      return Collections.emptyList();
    }

    List<PyClassRef> result = resolveSuperClassesFromStub();
    if (result == null) {
      result = new ArrayList<PyClassRef>();
      PsiElement[] superClassElements = getSuperClassElements();
      for (PsiElement element : superClassElements) {
        result.add(new PyClassRef(element));
      }
    }

    if (result.size() == 0 && isValid() && !PyBuiltinCache.getInstance(this).hasInBuiltins(this)) {
      String implicitSuperclassName = LanguageLevel.forElement(this).isPy3K() ? PyNames.OBJECT : PyNames.FAKE_OLD_BASE;
      PyClass implicitSuperclass = PyBuiltinCache.getInstance(this).getClass(implicitSuperclassName);
      if (implicitSuperclass != null) {
        result.add(new PyClassRef(implicitSuperclass));
      }
    }

    return result;
  }

  @Nullable
  private List<PyClassRef> resolveSuperClassesFromStub() {
    final PyClassStub stub = getStub();
    if (stub == null) {
      return null;
    }
    // stub-based resolve currently works correctly only with classes in file level
    final PsiElement parent = stub.getParentStub().getPsi();
    if (!(parent instanceof PyFile)) {
      // TODO[yole] handle this case
      return null;
    }

    List<PyClassRef> result = new ArrayList<PyClassRef>();
    for (PyQualifiedName qualifiedName : stub.getSuperClasses()) {
      result.add(classRefFromQName((NameDefiner)parent, qualifiedName));
    }
    return result;
  }

  private static PyClassRef classRefFromQName(NameDefiner parent, PyQualifiedName qualifiedName) {
    if (qualifiedName == null) {
      return new PyClassRef((String)null);
    }
    NameDefiner currentParent = parent;
    for (String component : qualifiedName.getComponents()) {
      PsiElement element = currentParent.getElementNamed(component);
      element = PyUtil.turnDirIntoInit(element);
      if (element instanceof PyImportElement) {
        element = ((PyImportElement)element).resolve();
      }
      if (!(element instanceof NameDefiner)) {
        currentParent = null;
        break;
      }
      currentParent = (NameDefiner)element;
    }

    if (currentParent != null) {
      return new PyClassRef(currentParent);
    }
    if (qualifiedName.getComponentCount() == 1) {
      final PyClass builtinClass = PyBuiltinCache.getInstance(parent).getClass(qualifiedName.getComponents().get(0));
      if (builtinClass != null) {
        return new PyClassRef(builtinClass);
      }
    }
    return new PyClassRef(qualifiedName.toString());
  }

  @NotNull
  public PyClass[] getSuperClasses() {
    final PyClassStub stub = getStub();
    if (stub != null) {
      final List<PyClassRef> pyClasses = resolveSuperClassesFromStub();
      if (pyClasses == null) {
        return EMPTY_ARRAY;
      }
      List<PyClass> result = new ArrayList<PyClass>();
      for (PyClassRef clsRef : pyClasses) {
        PyClass pyClass = clsRef.getPyClass();
        if (pyClass != null) {
          result.add(pyClass);
        }
      }
      return result.toArray(new PyClass[result.size()]);
    }

    PsiElement[] superClassElements = getSuperClassElements();
    if (superClassElements.length > 0) {
      List<PyClass> result = new ArrayList<PyClass>();
      for (PsiElement element : superClassElements) {
        if (element instanceof PyClass) {
          result.add((PyClass)element);
        }
      }
      return result.toArray(new PyClass[result.size()]);
    }
    return EMPTY_ARRAY;
  }


  public
  @NotNull
  List<PyClass> getMRO() {
    // see http://www.python.org/download/releases/2.3/mro/ for a muddy explanation.
    // see http://hackage.haskell.org/packages/archive/MetaObject/latest/doc/html/src/MO-Util-C3.html#linearize for code to port from.
    return mroLinearize(this, Collections.<PyClass>emptyList());
  }

  private static List<PyClass> mroMerge(List<List<PyClass>> sequences) {
    List<PyClass> result = new LinkedList<PyClass>(); // need to insert to 0th position on linearize
    while (true) {
      // filter blank sequences
      List<List<PyClass>> nonBlankSequences = new ArrayList<List<PyClass>>(sequences.size());
      for (List<PyClass> item : sequences) {
        if (item.size() > 0) nonBlankSequences.add(item);
      }
      if (nonBlankSequences.isEmpty()) return result;
      // find a clean head
      PyClass head = null; // to keep compiler happy; really head is assigned in the loop at least once.
      for (List<PyClass> seq : nonBlankSequences) {
        head = seq.get(0);
        boolean head_in_tails = false;
        for (List<PyClass> tail_seq : nonBlankSequences) {
          if (tail_seq.indexOf(head) > 0) { // -1 is not found, 0 is head, >0 is tail.
            head_in_tails = true;
            break;
          }
        }
        if (!head_in_tails) {
          break;
        }
        else {
          head = null; // as a signal
        }
      }
      assert head != null : "Inconsistent hierarchy!"; // TODO: better diagnostics?
      // our head is clean;
      result.add(head);
      // remove it from heads of other sequences
      for (List<PyClass> seq : nonBlankSequences) {
        if (seq.get(0) == head) seq.remove(0);
      }
    } // we either return inside the loop or die by assertion
  }

  private static List<PyClass> mroLinearize(PyClass cls, List<PyClass> seen) {
    assert (seen.indexOf(cls) < 0) : "Circular import structure on " + PyUtil.nvl(cls);
    PyClass[] bases = cls.getSuperClasses();
    List<List<PyClass>> lins = new ArrayList<List<PyClass>>(bases.length * 2);
    ArrayList<PyClass> new_seen = new ArrayList<PyClass>(seen.size() + 1);
    new_seen.add(cls);
    for (PyClass base : bases) {
      List<PyClass> lin = mroLinearize(base, new_seen);
      if (!lin.isEmpty()) lins.add(lin);
    }
    for (PyClass base : bases) {
      lins.add(new SmartList<PyClass>(base));
    }
    List<PyClass> result = mroMerge(lins);
    result.add(0, cls);
    return result;
  }

  @NotNull
  public PyFunction[] getMethods() {
    return getClassChildren(PythonDialectsTokenSetProvider.INSTANCE.getFunctionDeclarationTokens(), PyFunction.ARRAY_FACTORY);
  }

  @Override
  public PyClass[] getNestedClasses() {
    return getClassChildren(TokenSet.create(PyElementTypes.CLASS_DECLARATION), PyClass.ARRAY_FACTORY);
  }

  protected <T extends PsiElement> T[] getClassChildren(TokenSet elementTypes, ArrayFactory<T> factory) {
    // TODO: gather all top-level functions, maybe within control statements
    final PyClassStub classStub = getStub();
    if (classStub != null) {
      return classStub.getChildrenByType(elementTypes, factory);
    }
    List<T> result = new ArrayList<T>();
    final PyStatementList statementList = getStatementList();
    for (PsiElement element : statementList.getChildren()) {
      if (elementTypes.contains(element.getNode().getElementType())) {
        //noinspection unchecked
        result.add((T)element);
      }
    }
    return result.toArray(factory.create(result.size()));
  }

  private static class NameFinder<T extends PyElement> implements Processor<T> {
    private T myResult;
    private final String[] myNames;

    public NameFinder(String... names) {
      myNames = names;
      myResult = null;
    }

    public T getResult() {
      return myResult;
    }

    public boolean process(T target) {
      final String targetName = target.getName();
      for (String name : myNames) {
        if (name.equals(targetName)) {
          myResult = target;
          return false;
        }
      }
      return true;
    }
  }

  public PyFunction findMethodByName(@Nullable final String name, boolean inherited) {
    if (name == null) return null;
    NameFinder<PyFunction> proc = new NameFinder<PyFunction>(name);
    visitMethods(proc, inherited);
    return proc.getResult();
  }

  @Nullable
  @Override
  public PyClass findNestedClass(String name, boolean inherited) {
    if (name == null) return null;
    NameFinder<PyClass> proc = new NameFinder<PyClass>(name);
    visitNestedClasses(proc, inherited);
    return proc.getResult();
  }

  @Nullable
  public PyFunction findInitOrNew(boolean inherited) {
    NameFinder<PyFunction> proc;
    if (isNewStyleClass()) {
      proc = new NameFinder<PyFunction>(PyNames.INIT, PyNames.NEW);
    }
    else {
      proc = new NameFinder<PyFunction>(PyNames.INIT);
    }
    visitMethods(proc, inherited, true);
    return proc.getResult();
  }

  private final static Maybe<Callable> UNKNOWN_CALL = new Maybe<Callable>(); // denotes _not_ a PyFunction, actually
  private final static Maybe<Callable> NONE = new Maybe<Callable>(null); // denotes an explicit None

  /**
   * @param name            name of the property
   * @param property_filter returns true if the property is acceptable
   * @param advanced        is @foo.setter syntax allowed
   * @return the first property that both filters accepted.
   */
  @Nullable
  private Property processPropertiesInClass(@Nullable String name, @Nullable Processor<Property> property_filter, boolean advanced) {
    // NOTE: fast enough to be rerun every time
    Property prop = processDecoratedProperties(name, property_filter, advanced);
    if (prop != null) return prop;
    if (getStub() != null) {
      prop = processStubProperties(name, property_filter);
      if (prop != null) return prop;
    }
    else {
      // name = property(...) assignments from PSI
      for (PyTargetExpression target : getClassAttributes()) {
        if (name == null || name.equals(target.getName())) {
          prop = PropertyImpl.fromTarget(target);
          if (prop != null) {
            if (property_filter == null || property_filter.process(prop)) return prop;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private Property processDecoratedProperties(@Nullable String name, @Nullable Processor<Property> filter, boolean useAdvancedSyntax) {
    // look at @property decorators
    Map<String, List<PyFunction>> grouped = new HashMap<String, List<PyFunction>>();
    // group suitable same-named methods, each group defines a property
    for (PyFunction method : getMethods()) {
      final String methodName = method.getName();
      if (name == null || name.equals(methodName)) {
        List<PyFunction> bucket = grouped.get(methodName);
        if (bucket == null) {
          bucket = new SmartList<PyFunction>();
          grouped.put(methodName, bucket);
        }
        bucket.add(method);
      }
    }
    for (Map.Entry<String, List<PyFunction>> entry : grouped.entrySet()) {
      Maybe<Callable> getter = NONE;
      Maybe<Callable> setter = NONE;
      Maybe<Callable> deleter = NONE;
      String doc = null;
      final String decoratorName = entry.getKey();
      for (PyFunction method : entry.getValue()) {
        final PyDecoratorList decoratorList = method.getDecoratorList();
        if (decoratorList != null) {
          for (PyDecorator deco : decoratorList.getDecorators()) {
            final PyQualifiedName qname = deco.getQualifiedName();
            if (qname != null) {
              if (qname.matches(PyNames.PROPERTY)) {
                getter = new Maybe<Callable>(method);
              }
              else if (useAdvancedSyntax && qname.matches(decoratorName, PyNames.SETTER)) {
                setter = new Maybe<Callable>(method);
              }
              else if (useAdvancedSyntax && qname.matches(decoratorName, PyNames.DELETER)) {
                deleter = new Maybe<Callable>(method);
              }
            }
          }
        }
        if (getter != NONE && setter != NONE && deleter != NONE) break; // can't improve
      }
      if (getter != NONE || setter != NONE || deleter != NONE) {
        final PropertyImpl prop = new PropertyImpl(decoratorName, getter, setter, deleter, doc, null);
        if (filter == null || filter.process(prop)) return prop;
      }
    }
    return null;
  }

  private Maybe<Callable> fromPacked(Maybe<String> maybeName) {
    if (maybeName.isDefined()) {
      final String value = maybeName.value();
      if (value == null || PyNames.NONE.equals(value)) {
        return NONE;
      }
      PyFunction method = findMethodByName(value, true);
      if (method != null) return new Maybe<Callable>(method);
    }
    return UNKNOWN_CALL;
  }

  @Nullable
  private Property processStubProperties(@Nullable String name, @Nullable Processor<Property> propertyProcessor) {
    final PyClassStub stub = getStub();
    if (stub != null) {
      for (StubElement subStub : stub.getChildrenStubs()) {
        if (subStub.getStubType() == PyElementTypes.TARGET_EXPRESSION) {
          final PyTargetExpressionStub targetStub = (PyTargetExpressionStub)subStub;
          PropertyStubStorage prop = targetStub.getCustomStub(PropertyStubStorage.class);
          if (prop != null && (name == null || name.equals(targetStub.getName()))) {
            Maybe<Callable> getter = fromPacked(prop.getGetter());
            Maybe<Callable> setter = fromPacked(prop.getSetter());
            Maybe<Callable> deleter = fromPacked(prop.getDeleter());
            String doc = prop.getDoc();
            if (getter != NONE || setter != NONE || deleter != NONE) {
              final PropertyImpl property = new PropertyImpl(targetStub.getName(), getter, setter, deleter, doc, targetStub.getPsi());
              if (propertyProcessor == null || propertyProcessor.process(property)) return property;
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Property findProperty(@NotNull final String name) {
    Property property = findLocalProperty(name);
    if (property != null) {
      return property;
    }
    if (findMethodByName(name, false) != null || findClassAttribute(name, false) != null) {
      return null;
    }
    for (PyClass aClass : iterateAncestorClasses()) {
      final Property ancestorProperty = ((PyClassImpl)aClass).findLocalProperty(name);
      if (ancestorProperty != null) {
        return ancestorProperty;
      }
    }
    return null;
  }

  @Override
  public Property findPropertyByFunction(PyFunction function) {
    if (myPropertyCache == null) {
      myPropertyCache = initializePropertyCache();
    }
    for (Property property : myPropertyCache.values()) {
      if (property.getGetter().valueOrNull() == function ||
          property.getSetter().valueOrNull() == function ||
          property.getDeleter().valueOrNull() == function) {
        return property;
      }
    }
    return null;
  }

  private Property findLocalProperty(String name) {
    if (myPropertyCache == null) {
      myPropertyCache = initializePropertyCache();
    }
    return myPropertyCache.get(name);
  }

  private Map<String, Property> initializePropertyCache() {
    final Map<String, Property> result = new HashMap<String, Property>();
    processProperties(null, new Processor<Property>() {
      @Override
      public boolean process(Property property) {
        result.put(property.getName(), property);
        return false;
      }
    }, false);
    return result;
  }

  @Nullable
  @Override
  public Property scanProperties(@Nullable Processor<Property> filter, boolean inherited) {
    return processProperties(null, filter, inherited);
  }

  @Nullable
  private Property processProperties(@Nullable String name, @Nullable Processor<Property> filter, boolean inherited) {
    if (!isValid()) {
      return null;
    }
    LanguageLevel level = LanguageLevel.getDefault();
    // EA-32381: A tree-based instance may not have a parent element somehow, so getContainingFile() may be not appropriate
    final PsiFile file = getParentByStub() != null ? getContainingFile() : null;
    if (file != null) {
      final VirtualFile vfile = file.getVirtualFile();
      if (vfile != null) {
        level = LanguageLevel.forFile(vfile);
      }
    }
    final boolean useAdvancedSyntax = level.isAtLeast(LanguageLevel.PYTHON26);
    final Property local = processPropertiesInClass(name, filter, useAdvancedSyntax);
    if (local != null) {
      return local;
    }
    if (inherited) {
      if (name != null && (findMethodByName(name, false) != null || findClassAttribute(name, false) != null)) {
        return null;
      }
      for (PyClass cls : iterateAncestorClasses()) {
        final Property property = ((PyClassImpl)cls).processPropertiesInClass(name, filter, useAdvancedSyntax);
        if (property != null) {
          return property;
        }
      }
    }
    return null;
  }

  private static class PropertyImpl extends PropertyBunch<Callable> implements Property {
    private final String myName;

    private PropertyImpl(String name,
                         Maybe<Callable> getter,
                         Maybe<Callable> setter,
                         Maybe<Callable> deleter,
                         String doc,
                         PyTargetExpression site) {
      myName = name;
      myDeleter = deleter;
      myGetter = getter;
      mySetter = setter;
      myDoc = doc;
      mySite = site;
    }

    public String getName() {
      return myName;
    }

    public PyTargetExpression getDefinitionSite() {
      return mySite;
    }

    @NotNull
    @Override
    public Maybe<Callable> getByDirection(@NotNull AccessDirection direction) {
      switch (direction) {
        case READ:
          return myGetter;
        case WRITE:
          return mySetter;
        case DELETE:
          return myDeleter;
      }
      throw new IllegalArgumentException("Unknown direction " + PyUtil.nvl(direction));
    }

    @NotNull
    @Override
    protected Maybe<Callable> translate(@Nullable PyExpression expr) {
      if (expr == null) {
        return NONE;
      }
      if (PyNames.NONE.equals(expr.getName())) return NONE; // short-circuit a common case
      if (expr instanceof Callable) {
        return new Maybe<Callable>((Callable)expr);
      }
      final PsiReference ref = expr.getReference();
      if (ref != null) {
        PsiElement something = ref.resolve();
        if (something instanceof Callable) {
          return new Maybe<Callable>((Callable)something);
        }
      }
      return NONE;
    }

    public String toString() {
      return "property(" + myGetter + ", " + mySetter + ", " + myDeleter + ", " + myDoc + ")";
    }

    @Nullable
    public static PropertyImpl fromTarget(PyTargetExpression target) {
      PyExpression expr = target.findAssignedValue();
      final PropertyImpl prop = new PropertyImpl(target.getName(), null, null, null, null, target);
      final boolean success = fillFromCall(expr, prop);
      return success ? prop : null;
    }
  }

  public boolean visitMethods(Processor<PyFunction> processor, boolean inherited) {
    return visitMethods(processor, inherited, false);
  }

  public boolean visitMethods(Processor<PyFunction> processor,
                              boolean inherited,
                              boolean skipClassObj) {
    PyFunction[] methods = getMethods();
    if (!ContainerUtil.process(methods, processor)) return false;
    if (inherited) {
      for (PyClass ancestor : iterateAncestorClasses()) {
        if (skipClassObj && PyNames.FAKE_OLD_BASE.equals(ancestor.getName())) {
          continue;
        }
        if (!ancestor.visitMethods(processor, false)) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean visitNestedClasses(Processor<PyClass> processor, boolean inherited) {
    PyClass[] nestedClasses = getNestedClasses();
    if (!ContainerUtil.process(nestedClasses, processor)) return false;
    if (inherited) {
      for (PyClass ancestor : iterateAncestorClasses()) {
        if (!((PyClassImpl)ancestor).visitNestedClasses(processor, false)) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean visitClassAttributes(Processor<PyTargetExpression> processor, boolean inherited) {
    List<PyTargetExpression> methods = getClassAttributes();
    if (!ContainerUtil.process(methods, processor)) return false;
    if (inherited) {
      for (PyClass ancestor : iterateAncestorClasses()) {
        if (!ancestor.visitClassAttributes(processor, false)) {
          return false;
        }
      }
    }
    return true;
    // NOTE: sorry, not enough metaprogramming to generalize visitMethods and visitClassAttributes
  }

  public List<PyTargetExpression> getClassAttributes() {
    PyClassStub stub = getStub();
    if (stub != null) {
      final PyTargetExpression[] children = stub.getChildrenByType(PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.EMPTY_ARRAY);
      return Arrays.asList(children);
    }
    List<PyTargetExpression> result = new ArrayList<PyTargetExpression>();
    for (PsiElement psiElement : getStatementList().getChildren()) {
      if (psiElement instanceof PyAssignmentStatement) {
        final PyAssignmentStatement assignmentStatement = (PyAssignmentStatement)psiElement;
        final PyExpression[] targets = assignmentStatement.getTargets();
        for (PyExpression target : targets) {
          if (target instanceof PyTargetExpression) {
            result.add((PyTargetExpression)target);
          }
        }
      }
    }
    return result;
  }

  @Override
  public PyTargetExpression findClassAttribute(@NotNull String name, boolean inherited) {
    final NameFinder<PyTargetExpression> processor = new NameFinder<PyTargetExpression>(name);
    visitClassAttributes(processor, inherited);
    return processor.getResult();
  }

  public List<PyTargetExpression> getInstanceAttributes() {
    if (myInstanceAttributes == null) {
      myInstanceAttributes = collectInstanceAttributes();
    }
    return myInstanceAttributes;
  }

  @Nullable
  @Override
  public PyTargetExpression findInstanceAttribute(String name, boolean inherited) {
    final List<PyTargetExpression> instanceAttributes = getInstanceAttributes();
    for (PyTargetExpression instanceAttribute : instanceAttributes) {
      if (name.equals(instanceAttribute.getReferencedName())) {
        return instanceAttribute;
      }
    }
    if (inherited) {
      for (PyClass ancestor : iterateAncestorClasses()) {
        final PyTargetExpression attribute = ancestor.findInstanceAttribute(name, false);
        if (attribute != null) {
          return attribute;
        }
      }
    }
    return null;
  }

  private List<PyTargetExpression> collectInstanceAttributes() {
    Map<String, PyTargetExpression> result = new HashMap<String, PyTargetExpression>();

    // __init__ takes priority over all other methods
    PyFunctionImpl initMethod = (PyFunctionImpl)findMethodByName(PyNames.INIT, false);
    if (initMethod != null) {
      collectInstanceAttributes(initMethod, result);
    }
    final PyFunction[] methods = getMethods();
    for (PyFunction method : methods) {
      if (!PyNames.INIT.equals(method.getName())) {
        collectInstanceAttributes(method, result);
      }
    }

    final Collection<PyTargetExpression> expressions = result.values();
    return new ArrayList<PyTargetExpression>(expressions);
  }

  private static void collectInstanceAttributes(@NotNull PyFunction method, @NotNull final Map<String, PyTargetExpression> result) {
    final PyParameter[] params = method.getParameterList().getParameters();
    if (params.length == 0) {
      return;
    }
    final PyFunctionStub methodStub = method.getStub();
    if (methodStub != null) {
      final PyTargetExpression[] targets = methodStub.getChildrenByType(PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.EMPTY_ARRAY);
      for (PyTargetExpression target : targets) {
        if (!result.containsKey(target.getName())) {
          result.put(target.getName(), target);
        }
      }
    }
    else {
      final PyStatementList statementList = method.getStatementList();
      if (statementList != null) {
        statementList.accept(new PyRecursiveElementVisitor() {
          public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
            super.visitPyAssignmentStatement(node);
            collectNewTargets(result, node);
          }
        });
      }
    }
  }

  private static void collectNewTargets(Map<String, PyTargetExpression> collected, PyAssignmentStatement node) {
    final PyExpression[] targets = node.getTargets();
    for (PyExpression target : targets) {
      if (target instanceof PyTargetExpression && PyUtil.isInstanceAttribute(target)) {
        collected.put(target.getName(), (PyTargetExpression)target);
      }
    }
  }

  public boolean isNewStyleClass() {
    return myNewStyle.getValue().getValue();
  }

  private boolean calculateNewStyleClass() {
    final PsiFile containingFile = getContainingFile();
    if (containingFile instanceof PyFile && ((PyFile)containingFile).getLanguageLevel().isPy3K()) {
      return true;
    }
    final PyClass objClass = PyBuiltinCache.getInstance(this).getClass("object");
    if (this == objClass) return true; // a rare but possible case
    if (hasNewStyleMetaClass(this)) return true;
    for (PyClassRef ancestor : iterateAncestors()) {
      final PyClass pyClass = ancestor.getPyClass();
      if (pyClass == null) {
        // unknown, assume new-style class
        return true;
      }
      if (pyClass == objClass) return true;
      if (hasNewStyleMetaClass(pyClass)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasNewStyleMetaClass(PyClass pyClass) {
    final PsiFile containingFile = pyClass.getContainingFile();
    if (containingFile instanceof PyFile) {
      final PsiElement element = ((PyFile)containingFile).getElementNamed(PyNames.DUNDER_METACLASS);
      if (element instanceof PyTargetExpression) {
        final PyQualifiedName qName = ((PyTargetExpression)element).getAssignedQName();
        if (qName != null && qName.matches("type")) {
          return true;
        }
      }
    }
    if (pyClass.findClassAttribute(PyNames.DUNDER_METACLASS, false) != null) {
      return true;
    }
    return false;
  }

  @Override
  public boolean processClassLevelDeclarations(@NotNull PsiScopeProcessor processor) {
    final PyClassStub stub = getStub();
    if (stub != null) {
      final List<StubElement> children = stub.getChildrenStubs();
      for (StubElement child : children) {
        if (!processor.execute(child.getPsi(), ResolveState.initial())) {
          return false;
        }
      }
    }
    else {
      PyResolveUtil.scopeCrawlUp(processor, this, null, this);
    }
    return true;
  }

  @Override
  public boolean processInstanceLevelDeclarations(@NotNull PsiScopeProcessor processor, @Nullable PyExpression location) {
    Map<String, PyTargetExpression> declarationsInMethod = new HashMap<String, PyTargetExpression>();
    PyFunction instanceMethod = PsiTreeUtil.getParentOfType(location, PyFunction.class);
    final PyClass containingClass = instanceMethod != null ? instanceMethod.getContainingClass() : null;
    if (instanceMethod != null && containingClass != null && CompletionUtil.getOriginalElement(containingClass) == this) {
      collectInstanceAttributes(instanceMethod, declarationsInMethod);
      for (PyTargetExpression targetExpression : declarationsInMethod.values()) {
        if (!processor.execute(targetExpression, ResolveState.initial())) {
          return false;
        }
      }
    }
    for (PyTargetExpression expr : getInstanceAttributes()) {
      if (declarationsInMethod.containsKey(expr.getName())) {
        continue;
      }
      if (!processor.execute(expr, ResolveState.initial())) return false;
    }
    return true;
  }

  public int getTextOffset() {
    final ASTNode name = getNameNode();
    return name != null ? name.getStartOffset() : super.getTextOffset();
  }

  public PyStringLiteralExpression getDocStringExpression() {
    return PythonDocStringFinder.find(getStatementList());
  }

  @Override
  public String getDocStringValue() {
    final PyClassStub stub = getStub();
    if (stub != null) {
      return stub.getDocString();
    }
    return PyPsiUtils.strValue(getDocStringExpression());
  }

  public String toString() {
    return "PyClass: " + getName();
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    return Collections.<PyElement>singleton(this);
  }

  public PyElement getElementNamed(final String the_name) {
    return the_name.equals(getName()) ? this : null;
  }

  public boolean mustResolveOutside() {
    return false;
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    ControlFlowCache.clear(this);
    if (myInstanceAttributes != null) {
      myInstanceAttributes = null;
    }
    myPropertyCache = null;
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(this);
    if (scopeOwner instanceof PyFunction) {
      return new LocalSearchScope(scopeOwner);
    }
    return super.getUseScope();
  }

  private static class AncestorsIterable implements Iterable<PyClassRef> {
    private final PyClassImpl myClass;

    public AncestorsIterable(final PyClassImpl pyClass) {
      myClass = pyClass;
    }

    public Iterator<PyClassRef> iterator() {
      return new AncestorsIterator(myClass);
    }
  }

  private static class AncestorsIterator implements Iterator<PyClassRef> {
    List<PyClassRef> pending = new LinkedList<PyClassRef>();
    private final Set<PyClassRef> seen;
    Iterator<PyClassRef> percolator;
    PyClassRef prefetch = null;
    private final PyClassImpl myAClass;

    public AncestorsIterator(PyClassImpl aClass) {
      myAClass = aClass;
      percolator = myAClass.getSuperClassesList().iterator();
      seen = new HashSet<PyClassRef>();
    }

    private AncestorsIterator(PyClassImpl AClass, Set<PyClassRef> seen) {
      myAClass = AClass;
      this.seen = seen;
      percolator = myAClass.getSuperClassesList().iterator();
    }

    public boolean hasNext() {
      // due to already-seen filtering, there's no way but to try and see.
      if (prefetch != null) return true;
      prefetch = getNext();
      return prefetch != null;
    }

    public PyClassRef next() {
      final PyClassRef nextClass = getNext();
      if (nextClass == null) throw new NoSuchElementException();
      return nextClass;
    }

    @Nullable
    private PyClassRef getNext() {
      iterations:
      while (true) {
        if (prefetch != null) {
          PyClassRef ret = prefetch;
          prefetch = null;
          return ret;
        }
        if (percolator.hasNext()) {
          PyClassRef it = percolator.next();
          if (seen.contains(it)) {
            continue iterations; // loop back is equivalent to return next();
          }
          pending.add(it);
          seen.add(it);
          return it;
        }
        else {
          while (pending.size() > 0) {
            PyClassRef it = pending.get(0);
            pending.remove(0);
            PyClass pyClass = it.getPyClass();
            if (pyClass != null) {
              percolator = new AncestorsIterator((PyClassImpl)pyClass, new HashSet<PyClassRef>(seen));
              continue iterations;
            }
          }
          return null;
        }
      }
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private static class AncestorClassesIterable implements Iterable<PyClass> {
    private final PyClassImpl myClass;

    public AncestorClassesIterable(final PyClassImpl pyClass) {
      myClass = pyClass;
    }

    public Iterator<PyClass> iterator() {
      return new AncestorClassesIterator(new AncestorsIterator(myClass));
    }
  }

  private static class AncestorClassesIterator implements Iterator<PyClass> {
    private final AncestorsIterator myAncestorsIterator;
    private PyClass myNext;

    public AncestorClassesIterator(AncestorsIterator ancestorsIterator) {
      myAncestorsIterator = ancestorsIterator;
    }

    @Override
    public boolean hasNext() {
      if (myNext != null) {
        return true;
      }
      while (myAncestorsIterator.hasNext()) {
        PyClassRef clsRef = myAncestorsIterator.getNext();
        if (clsRef == null) {
          return false;
        }
        myNext = clsRef.getPyClass();
        if (myNext != null) {
          return true;
        }
      }
      return false;
    }

    @Nullable
    @Override
    public PyClass next() {
      if (myNext == null) {
        if (!hasNext()) return null;
      }
      PyClass next = myNext;
      myNext = null;
      return next;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}

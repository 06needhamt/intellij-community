package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.reference.impl.CachingReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author cdr
 */
public class FileReference
  implements PsiPolyVariantReference, QuickFixProvider<FileReference>, LocalQuickFixProvider, EmptyResolveMessageProvider {
  public static final FileReference[] EMPTY = new FileReference[0];

  private final int myIndex;
  private TextRange myRange;
  private final String myText;
  @NotNull private final FileReferenceSet myFileReferenceSet;

  public FileReference(@NotNull final FileReferenceSet fileReferenceSet, TextRange range, int index, String text) {
    myFileReferenceSet = fileReferenceSet;
    myIndex = index;
    myRange = range;
    myText = text;
  }

  @NotNull
  private Collection<PsiFileSystemItem> getContexts() {
    final FileReference contextRef = getContextReference();
    if (contextRef == null) {
      return myFileReferenceSet.getDefaultContexts();
    }
    ResolveResult[] resolveResults = contextRef.multiResolve(false);
    ArrayList<PsiFileSystemItem> result = new ArrayList<PsiFileSystemItem>();
    for (ResolveResult resolveResult : resolveResults) {
      if (resolveResult.getElement() != null) {
        result.add((PsiFileSystemItem)resolveResult.getElement());
      }
    }
    return result;
  }

  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    final PsiManager manager = getElement().getManager();
    if (manager instanceof PsiManagerImpl) {
      return ((PsiManagerImpl)manager).getResolveCache().resolveWithCaching(this, MyResolver.INSTANCE, false, false);
    }
    return innerResolve();
  }

  protected ResolveResult[] innerResolve() {
    final String referenceText = getText();
    final Collection<PsiFileSystemItem> contexts = getContexts();
    final Collection<ResolveResult> result = new HashSet<ResolveResult>(contexts.size());
    for (final PsiFileSystemItem context : contexts) {
      if (context != null) {
        innerResolveInContext(referenceText, context, result);
      }
    }
    final int resultCount = result.size();
    return resultCount > 0 ? result.toArray(new ResolveResult[resultCount]) : ResolveResult.EMPTY_ARRAY;
  }

  protected void innerResolveInContext(@NotNull final String text, @NotNull final PsiFileSystemItem context, final Collection<ResolveResult> result) {
    if (text.length() == 0 && !myFileReferenceSet.isEndingSlashNotAllowed() && isLast() || ".".equals(text) || "/".equals(text)) {
      result.add(new PsiElementResolveResult(context));
    }
    else if ("..".equals(text)) {
      final PsiFileSystemItem resolved = context.getParent();
      if (resolved != null) {
        result.add(new PsiElementResolveResult(resolved));
      }
    }
    else {
      final int separatorIndex = text.indexOf('/');
      if (separatorIndex >= 0) {
        final List<ResolveResult> resolvedContexts = new ArrayList<ResolveResult>();
        innerResolveInContext(text.substring(0, separatorIndex), context, resolvedContexts);
        final String restOfText = text.substring(separatorIndex + 1);
        for (ResolveResult contextVariant : resolvedContexts) {
          final PsiFileSystemItem item = (PsiFileSystemItem)contextVariant.getElement();
          if (item != null) {
            innerResolveInContext(restOfText, item, result);
          }
        }
      }
      else {
        final String decoded = decode(text);
        if (decoded != null) {
          processVariants(context,new Processor<PsiElement>() {
            public boolean process(final PsiElement element) {
              final String name = ((PsiNamedElement)element).getName();
              if (name != null) {
                if (myFileReferenceSet.isCaseSensitive() ? decoded.equals(name) : decoded.compareToIgnoreCase(name) == 0) {
                  result.add(new PsiElementResolveResult(element));
                  return false;
                }
              }
              return true;
            }
          });
        }
      }
    }
  }

  @Nullable
  private String decode(final String text) {
    if (myFileReferenceSet.isUrlEncoded()) {
      try {
        return new URI(text).getPath();
      }
      catch (Exception e) {
        return text;
      }
    }
    return text;
  }

  public Object[] getVariants() {
    final String s = getText();
    if (s != null && s.equals("/")) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    final CommonProcessors.CollectUniquesProcessor<PsiElement> collector = new CommonProcessors.CollectUniquesProcessor<PsiElement>();
    final PsiElementProcessor<PsiFileSystemItem> processor = createChildrenProcessor(new FilteringProcessor<PsiElement>(myFileReferenceSet.createCondition(),
                                                                                                                        collector));
    for (PsiFileSystemItem context : getContexts()) {
      for (final PsiElement child : context.getChildren()) {
        if (child instanceof PsiFileSystemItem) {
          processor.execute((PsiFileSystemItem)child);
        }
      }
    }

    final PsiElement[] candidates = collector.toArray(new PsiElement[0]);
    final Object[] variants = new Object[candidates.length];
    System.arraycopy(candidates, 0, variants, 0, candidates.length);
    if (myFileReferenceSet.isUrlEncoded()) {
      for (int i = 0; i < candidates.length; i++) {
        final PsiElement element = candidates[i];
        if (element instanceof PsiNamedElement) {
          final PsiNamedElement psiElement = (PsiNamedElement)element;
          String name = psiElement.getName();
          final String encoded = encode(name);
          if (!encoded.equals(name)) {
            final Icon icon = psiElement.getIcon(Iconable.ICON_FLAG_READ_STATUS | Iconable.ICON_FLAG_VISIBILITY);
            final Object lookupValue = LookupValueFactory.createLookupValue(encoded, icon);
            variants[i] = lookupValue;
          }
        }
      }
    }
    return variants;
  }

  private static String encode(final String name) {
    try {
      return new URI(null, null, name, null).toString();
    }
    catch (Exception e) {
      return name;
    }
  }

  private void processVariants(final PsiFileSystemItem context, final Processor<PsiElement> processor) {
    context.processChildren(createChildrenProcessor(processor));
  }

  private PsiElementProcessor<PsiFileSystemItem> createChildrenProcessor(final Processor<PsiElement> processor) {
    return new PsiElementProcessor<PsiFileSystemItem>() {
      public boolean execute(PsiFileSystemItem element) {
        final VirtualFile file = element.getVirtualFile();
        if (file != null && !file.isDirectory()) {
          final PsiManager psiManager = getElement().getManager();
          if (psiManager != null) {
            final PsiFile psiFile = psiManager.findFile(file);
            if (psiFile != null) {
              element = psiFile;
            }
          }
        }
        return processor.process(element);
      }
    };
  }

  @Nullable
  private FileReference getContextReference() {
    return myIndex > 0 ? myFileReferenceSet.getReference(myIndex - 1) : null;
  }

  public PsiElement getElement() {
    return myFileReferenceSet.getElement();
  }

  public PsiFileSystemItem resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? (PsiFileSystemItem)resolveResults[0].getElement() : null;
  }

  @Nullable
  public PsiFileSystemItem innerSingleResolve() {
    final ResolveResult[] resolveResults = innerResolve();
    return resolveResults.length == 1 ? (PsiFileSystemItem)resolveResults[0].getElement() : null;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiFileSystemItem)) return false;

    final PsiFileSystemItem item = resolve();
    return item != null && FileReferenceHelperRegistrar.areElementsEquivalent(item, (PsiFileSystemItem)element);
  }

  public TextRange getRangeInElement() {
    return myRange;
  }

  public String getCanonicalText() {
    return myText;
  }

  public String getText() {
    return myText;
  }

  public boolean isSoft() {
    return myFileReferenceSet.isSoft();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final ElementManipulator<PsiElement> manipulator = CachingReference.getManipulator(getElement());
    if (manipulator != null) {
      myFileReferenceSet.setElement(manipulator.handleContentChange(getElement(), getRangeInElement(), newElementName));
      //Correct ranges
      int delta = newElementName.length() - myRange.getLength();
      myRange = new TextRange(getRangeInElement().getStartOffset(), getRangeInElement().getStartOffset() + newElementName.length());
      FileReference[] references = myFileReferenceSet.getAllReferences();
      for (int idx = myIndex + 1; idx < references.length; idx++) {
        references[idx].myRange = references[idx].myRange.shiftRight(delta);
      }
      return myFileReferenceSet.getElement();
    }
    throw new IncorrectOperationException("Manipulator for this element is not defined");
  }

  /* Happens when it's been moved to another folder */
  public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
    
    if (!(element instanceof PsiFileSystemItem)) throw new IncorrectOperationException("Cannot bind to element, should be instanceof PsiFileSystemItem: " + element);

    final PsiFileSystemItem fileSystemItem = (PsiFileSystemItem)element;
    VirtualFile dstVFile = fileSystemItem.getVirtualFile();
    if (dstVFile == null) throw new IncorrectOperationException("Cannot bind to non-physical element:" + element);

    final PsiFile file = getElement().getContainingFile();
    final VirtualFile curVFile = file.getVirtualFile();
    if (curVFile == null) throw new IncorrectOperationException("Cannot bind from non-physical element:" + file);

    final Project project = element.getProject();

    final String newName;
    if (myFileReferenceSet.isAbsolutePathReference()) {
      PsiFileSystemItem root = null;
      PsiFileSystemItem dstItem = null;
      for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
        PsiFileSystemItem _dstItem = helper.getPsiFileSystemItem(project, dstVFile);
        if (_dstItem != null) {
          PsiFileSystemItem _root = helper.findRoot(project, dstVFile);
          if (_root != null) {
            root = _root;
            dstItem = _dstItem;
            break;
          }
        }
      }
      if (root == null) return element;
      final String relativePath = PsiFileSystemItemUtil.getRelativePath(root, dstItem);
      if (relativePath == null) {
        return element;
      }
      newName = "/" + relativePath;

    } else { // relative path
      PsiFileSystemItem curItem = null;
      PsiFileSystemItem dstItem = null;
      helpers: for (final FileReferenceHelper<?> helper: FileReferenceHelperRegistrar.getHelpers()) {

        final Collection<PsiFileSystemItem> contexts = helper.getContexts(project, curVFile);
        switch (contexts.size()) {
          case 0:
            continue;
          default:
            for (PsiFileSystemItem context : contexts) {
              final VirtualFile contextFile = context.getVirtualFile();
              assert contextFile != null;
              if (VfsUtil.isAncestor(contextFile, dstVFile, true)) {
                final String path = VfsUtil.getRelativePath(dstVFile, contextFile, '/');
                if (path != null) {
                  return rename(path); 
                }
              }
            }
          case 1:
            PsiFileSystemItem _dstItem = helper.getPsiFileSystemItem(project, dstVFile);
            PsiFileSystemItem _curItem = helper.getPsiFileSystemItem(project, curVFile);
            if (_dstItem != null && _curItem != null) {
              curItem = _curItem;
              dstItem = _dstItem;
              break helpers;
            }
        }
      }
      checkNotNull(curItem, curVFile, dstVFile);
      assert curItem != null;
      if (curItem.equals(dstItem)) {
        return getElement();
      }
      newName = PsiFileSystemItemUtil.getRelativePath(curItem, dstItem);
      if (newName == null) {
        return element;
      }
    }

    return rename(newName);
  }

  private PsiElement rename(final String newName) throws IncorrectOperationException {
    final TextRange range = new TextRange(myFileReferenceSet.getStartInElement(), getRangeInElement().getEndOffset());
    final ElementManipulator<PsiElement> manipulator = CachingReference.getManipulator(getElement());
    if (manipulator == null) {
      throw new IncorrectOperationException("Manipulator not defined for: " + getElement());
    }
    return manipulator.handleContentChange(getElement(), range, newName);
  }

  private static void checkNotNull(final Object o, final VirtualFile curVFile, final VirtualFile dstVFile) throws IncorrectOperationException {
    if (o == null) {
      throw new IncorrectOperationException("Cannot find path between files; src = " + curVFile.getPresentableUrl() + "; dst = " + dstVFile.getPresentableUrl());
    }
  }

  public void registerQuickfix(HighlightInfo info, FileReference reference) {
    for (final FileReferenceHelper helper : getHelpers()) {
      helper.registerFixes(info, reference);
    }
  }

  protected List<FileReferenceHelper> getHelpers() {
    return FileReferenceHelperRegistrar.getHelpers();
  }

  public int getIndex() {
    return myIndex;
  }

  public String getUnresolvedMessagePattern() {
    final StringBuilder builder = new StringBuilder(JavaErrorMessages.message("error.cannot.resolve"));
    builder.append(" ").append(myFileReferenceSet.getTypeName());
    if (!isLast()) {
      for (final FileReferenceHelper helper : getHelpers()) {
        builder.append(" ").append(JavaErrorMessages.message("error.cannot.resolve.infix")).append(" ")
          .append(helper.getDirectoryTypeName());
      }
    }
    builder.append(" ''{0}''.");
    return builder.toString();
  }

  public final boolean isLast() {
    return myIndex == myFileReferenceSet.getAllReferences().length - 1;
  }

  @NotNull
  public FileReferenceSet getFileReferenceSet() {
    return myFileReferenceSet;
  }

  public LocalQuickFix[] getQuickFixes() {
    final List<LocalQuickFix> result = new ArrayList<LocalQuickFix>();
    for (final FileReferenceHelper<?> helper : getHelpers()) {
      result.addAll(helper.registerFixes(null, this));
    }
    return result.toArray(new LocalQuickFix[result.size()]);
  }

  static class MyResolver implements ResolveCache.PolyVariantResolver<FileReference> {
    static final MyResolver INSTANCE = new MyResolver();

    public ResolveResult[] resolve(FileReference ref, boolean incompleteCode) {
      return ref.innerResolve();
    }
  }
}

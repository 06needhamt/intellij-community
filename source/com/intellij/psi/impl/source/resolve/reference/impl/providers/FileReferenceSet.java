package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Maxim.Mossienko
 */
public class FileReferenceSet {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet");

  private static final char SEPARATOR = '/';
  private static final String SEPARATOR_STRING = "/";
  private static final Key<CachedValue<Collection<PsiFileSystemItem>>> DEFAULT_CONTEXTS_KEY = new Key<CachedValue<Collection<PsiFileSystemItem>>>("default file contexts");
  public static final CustomizableReferenceProvider.CustomizationKey<Function<PsiFile, Collection<PsiFileSystemItem>>> DEFAULT_PATH_EVALUATOR_OPTION =
    new CustomizableReferenceProvider.CustomizationKey<Function<PsiFile, Collection<PsiFileSystemItem>>>(PsiBundle.message("default.path.evaluator.option"));
  public static final Function<PsiFile, Collection<PsiFileSystemItem>> ABSOLUTE_TOP_LEVEL = new Function<PsiFile, Collection<PsiFileSystemItem>>() {
          @Nullable
          public Collection<PsiFileSystemItem> fun(final PsiFile file) {
            return getAbsoluteTopLevelDirLocations(file);
          }
        };

  private FileReference[] myReferences;
  private PsiElement myElement;
  private final int myStartInElement;
  private boolean myCaseSensitive;
  private final String myPathString;
  private Collection<PsiFileSystemItem> myDefaultContexts;
  private final boolean myEndingSlashNotAllowed;
  private @Nullable Map<CustomizableReferenceProvider.CustomizationKey, Object> myOptions;

  @Nullable
  public static FileReferenceSet createSet(PsiElement element, final boolean soft, boolean endingSlashNotAllowed, final boolean urlEncoded) {

    String text;
    int offset;

    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
    assert manipulator != null;
    final TextRange range = manipulator.getRangeInElement(element);
    offset = range.getStartOffset();
    text = range.substring(element.getText());
    for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
      text = helper.trimUrl(text);
    }

    return new FileReferenceSet(text, element, offset, null, true, endingSlashNotAllowed) {
      protected boolean isUrlEncoded() {
        return urlEncoded;
      }

      protected boolean isSoft() {
        return soft;
      }
    };
  }


  public FileReferenceSet(String str,
                          PsiElement element,
                          int startInElement,
                          PsiReferenceProvider provider,
                          final boolean isCaseSensitive) {
    this(str, element, startInElement, provider, isCaseSensitive, true);
  }


  public FileReferenceSet(@NotNull String str,
                          PsiElement element,
                          int startInElement,
                          PsiReferenceProvider provider,
                          final boolean isCaseSensitive,
                          boolean endingSlashNotAllowed) {
    myElement = element;
    myStartInElement = startInElement;
    myCaseSensitive = isCaseSensitive;
    myPathString = str.trim();
    myEndingSlashNotAllowed = endingSlashNotAllowed;
    myOptions = provider instanceof CustomizableReferenceProvider ? ((CustomizableReferenceProvider)provider).getOptions() : null;

    reparse(str);
  }

  public PsiElement getElement() {
    return myElement;
  }

  void setElement(final PsiElement element) {
    myElement = element;
  }

  public boolean isCaseSensitive() {
    return myCaseSensitive;
  }

  public void setCaseSensitive(final boolean caseSensitive) {
    myCaseSensitive = caseSensitive;
  }

  public boolean isEndingSlashNotAllowed() {
    return myEndingSlashNotAllowed;
  }

  public int getStartInElement() {
    return myStartInElement;
  }

  public FileReference createFileReference(final TextRange range, final int index, final String text) {
    return new FileReference(this, range, index, text);
  }

  private void reparse(String str) {
    final List<FileReference> referencesList = new ArrayList<FileReference>();
    // skip white space
    int currentSlash = -1;
    while (currentSlash + 1 < str.length() && Character.isWhitespace(str.charAt(currentSlash + 1))) currentSlash++;
    if (currentSlash + 1 < str.length() && str.charAt(currentSlash + 1) == SEPARATOR) currentSlash++;
    int index = 0;

    if (str.equals(SEPARATOR_STRING)) {
      final FileReference fileReference =
        createFileReference(new TextRange(myStartInElement, myStartInElement + 1), index++, SEPARATOR_STRING);
      referencesList.add(fileReference);
    }

    while (true) {
      final int nextSlash = str.indexOf(SEPARATOR, currentSlash + 1);
      final String subreferenceText = nextSlash > 0 ? str.substring(currentSlash + 1, nextSlash) : str.substring(currentSlash + 1);
      final FileReference ref = createFileReference(
        new TextRange(myStartInElement + currentSlash + 1, myStartInElement + (nextSlash > 0 ? nextSlash : str.length())),
        index++,
        subreferenceText);
      referencesList.add(ref);
      if ((currentSlash = nextSlash) < 0) {
        break;
      }
    }

    setReferences(referencesList.toArray(new FileReference[referencesList.size()]));
  }

  private void setReferences(final FileReference[] references) {
    myReferences = references;
  }

  public FileReference getReference(int index) {
    return myReferences[index];
  }

  @NotNull
  public FileReference[] getAllReferences() {
    return myReferences;
  }

  protected boolean isSoft() {
    return false;
  }

  protected boolean isUrlEncoded() {
    return false;
  }

  @NotNull
  public Collection<PsiFileSystemItem> getDefaultContexts() {
    if (myDefaultContexts == null) {
      myDefaultContexts = computeDefaultContexts();
    }
    return myDefaultContexts;
  }

  @NotNull
  public Collection<PsiFileSystemItem> computeDefaultContexts() {
    final PsiFile file = getContainingFile();
    if (file == null) return Collections.emptyList();
    
    if (myOptions != null) {
      final Function<PsiFile, Collection<PsiFileSystemItem>> value = DEFAULT_PATH_EVALUATOR_OPTION.getValue(myOptions);

      if (value != null) {
        return value.fun(file);
      }
    }
    if (isAbsolutePathReference()) {
      return getAbsoluteTopLevelDirLocations(file);
    }

    final CachedValueProvider<Collection<PsiFileSystemItem>> myDefaultContextProvider = new CachedValueProvider<Collection<PsiFileSystemItem>>() {
      public Result<Collection<PsiFileSystemItem>> compute() {
        final Collection<PsiFileSystemItem> contexts = getContextByFile(file);
        return Result.createSingleDependency(contexts,
                                             PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    };
    final CachedValuesManager cachedValuesManager = PsiManager.getInstance(myElement.getProject()).getCachedValuesManager();
    final Collection<PsiFileSystemItem> value =
      cachedValuesManager.getCachedValue(file, DEFAULT_CONTEXTS_KEY, myDefaultContextProvider, false);
    return value == null ? Collections.<PsiFileSystemItem>emptyList() : value;
  }

  @Nullable
  private PsiFile getContainingFile() {
    PsiFile file = myElement.getContainingFile();
    if (file == null) {
      LOG.assertTrue(false, "Invalid element: " + myElement);
    }

    if (!file.isPhysical()) file = file.getOriginalFile();
    return file;
  }

  @Nullable
  private Collection<PsiFileSystemItem> getContextByFile(final @NotNull PsiFile file) {

    final Project project = file.getProject();
    final FileContextProvider contextProvider = FileContextProvider.getProvider(file);
    if (contextProvider != null) {
      final PsiFileSystemItem item = contextProvider.getContextFolder(file);
      if (item != null) {
        return Collections.singleton(item);
      }
      if (useIncludingFileAsContext()) {
        final PsiFile contextFile = contextProvider.getContextFile(file);
        if (contextFile != null) {
          return Collections.<PsiFileSystemItem>singleton(contextFile.getParent());
        }
      }
    }
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      return FileReferenceHelperRegistrar.getNotNullHelper(file).getContexts(project, virtualFile);
    }
    final PsiDirectory parent = file.getParent();
    return parent == null ? Collections.<PsiFileSystemItem>emptyList() : Collections.<PsiFileSystemItem>singleton(parent);
  }

  public String getPathString() {
    return myPathString;
  }

  public boolean isAbsolutePathReference() {
    return myPathString.startsWith(SEPARATOR_STRING);
  }

  protected boolean useIncludingFileAsContext() {
    return true;
  }

  @Nullable
  public PsiElement resolve() {
    return myReferences == null || myReferences.length == 0 ? null : myReferences[myReferences.length - 1].resolve();
  }

  @NotNull
  private static Collection<PsiFileSystemItem> getAbsoluteTopLevelDirLocations(final @NotNull PsiFile file) {

    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module == null) return Collections.emptyList();

    for (final FileReferenceHelper<?> helper : FileReferenceHelperRegistrar.getHelpers()) {
      final Collection<PsiFileSystemItem> roots = helper.getRoots(module);
      if (roots.size() > 0) {
        return roots;
      }
    }

    return Collections.emptyList();
  }

  protected Condition<PsiElement> createCondition() {
    return Condition.TRUE;
  }

  public <Option> void addCustomization(CustomizableReferenceProvider.CustomizationKey<Option> key, Option value) {
    if (myOptions == null) {
      myOptions = new HashMap<CustomizableReferenceProvider.CustomizationKey, Object>(5);
    }
    myOptions.put(key, value);
  }
}

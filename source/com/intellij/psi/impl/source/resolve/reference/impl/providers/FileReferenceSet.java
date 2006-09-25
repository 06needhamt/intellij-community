package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.javaee.web.WebModuleProperties;
import com.intellij.javaee.web.WebUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.impl.source.jsp.JspManager;
import com.intellij.psi.impl.source.jsp.JspContextManager;
import com.intellij.psi.impl.source.resolve.reference.ProcessorRegistry;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
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

  private FileReference[] myReferences;
  private PsiElement myElement;
  private final int myStartInElement;
  @NotNull private final ReferenceType myType;
  private final PsiReferenceProvider myProvider;
  private boolean myCaseSensitive;
  private String myPathString;
  private final boolean myAllowEmptyFileReferenceAtEnd;

  public static final CustomizableReferenceProvider.CustomizationKey<Function<PsiFile, PsiElement>> DEFAULT_PATH_EVALUATOR_OPTION =
    new CustomizableReferenceProvider.CustomizationKey<Function<PsiFile, PsiElement>>(PsiBundle.message("default.path.evaluator.option"));

  private @Nullable Map<CustomizableReferenceProvider.CustomizationKey, Object> myOptions;

  @Nullable
  public static FileReferenceSet createSet(PsiElement element, final boolean soft, boolean endingSlashNotAllowed) {

    String text;
    int offset;

    if (element instanceof XmlAttributeValue) {
      text = ((XmlAttributeValue)element).getValue();
      offset = 1;
    }
    else if (element instanceof XmlTag) {
      final XmlTag tag = ((XmlTag)element);
      final XmlTagValue value = tag.getValue();
      final String s = value.getText();
      text = s.trim();
      offset = value.getTextRange().getStartOffset() + s.indexOf(text) - element.getTextOffset();
    }
    else {
      return null;
    }
    if (text != null) {
      text = WebUtil.trimURL(text);
    }
    return new FileReferenceSet(text, element, offset, ReferenceType.FILE_TYPE, null, true, endingSlashNotAllowed) {
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
    this(str, element, startInElement, ReferenceType.FILE_TYPE, provider, isCaseSensitive, true);
  }

  public FileReferenceSet(String str,
                          PsiElement element,
                          int startInElement,
                          @NotNull ReferenceType type,
                          PsiReferenceProvider provider,
                          final boolean isCaseSensitive) {
    this(str, element, startInElement, type, provider, isCaseSensitive, true);
  }

  public FileReferenceSet(String str,
                          PsiElement element,
                          int startInElement,
                          @NotNull ReferenceType type,
                          PsiReferenceProvider provider,
                          final boolean isCaseSensitive,
                          boolean allowEmptyFileReferenceAtEnd) {
    myType = type;
    myElement = element;
    myStartInElement = startInElement;
    myProvider = provider;
    myCaseSensitive = isCaseSensitive;
    myPathString = str.trim();
    myAllowEmptyFileReferenceAtEnd = allowEmptyFileReferenceAtEnd;
    myOptions = provider instanceof CustomizableReferenceProvider ? ((CustomizableReferenceProvider)provider).getOptions() : null;

    reparse(str);
  }

  PsiElement getElement() {
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
    for (FileReference ref : myReferences) {
      ref.clearResolveCaches();
    }
  }

  int getStartInElement() {
    return myStartInElement;
  }

  PsiReferenceProvider getProvider() {
    return myProvider;
  }

  protected void reparse(String str) {
    final List<FileReference> referencesList = new ArrayList<FileReference>();
    // skip white space
    int currentSlash = -1;
    while (currentSlash + 1 < str.length() && Character.isWhitespace(str.charAt(currentSlash + 1))) currentSlash++;
    if (currentSlash + 1 < str.length() && str.charAt(currentSlash + 1) == SEPARATOR) currentSlash++;
    int index = 0;

    while (true) {
      final int nextSlash = str.indexOf(SEPARATOR, currentSlash + 1);
      final String subreferenceText = nextSlash > 0 ? str.substring(currentSlash + 1, nextSlash) : str.substring(currentSlash + 1);
      if (subreferenceText.length() > 0 || myAllowEmptyFileReferenceAtEnd) { // ? check at end
        FileReference currentContextRef = new FileReference(this, new TextRange(myStartInElement + currentSlash + 1, myStartInElement + (
          nextSlash > 0
          ? nextSlash
          : str.length())), index++, subreferenceText);
        referencesList.add(currentContextRef);
      }
      if ((currentSlash = nextSlash) < 0) {
        break;
      }
    }

    setReferences(referencesList.toArray(new FileReference[referencesList.size()]));
  }

  protected void setReferences(final FileReference[] references) {
    myReferences = references;
  }

  public FileReference getReference(int index) {
    return myReferences[index];
  }

  @NotNull
  public FileReference[] getAllReferences() {
    return myReferences;
  }

  @NotNull
  ReferenceType getType(int index) {
    if (index != myReferences.length - 1) {
      return new ReferenceType(new int[]{myType.getPrimitives()[0], ReferenceType.WEB_DIRECTORY_ELEMENT, ReferenceType.DIRECTORY});
    }
    return myType;
  }

  protected boolean isSoft() {
    return false;
  }

  @NotNull
  public Collection<PsiElement> getDefaultContexts(PsiElement element) {
    Project project = element.getProject();
    PsiFile file = element.getContainingFile();
    if (file == null) {
      LOG.assertTrue(false, "Invalid element: " + element);
    }

    if (!file.isPhysical()) file = file.getOriginalFile();
    if (file == null) return Collections.emptyList();
    if (myOptions != null) {
      final Function<PsiFile, PsiElement> value = DEFAULT_PATH_EVALUATOR_OPTION.getValue(myOptions);

      if (value != null) {
        final PsiElement result = value.fun(file);
        return result == null ? Collections.<PsiElement>emptyList() : Collections.singleton(result);
      }
    }

    final WebModuleProperties properties = WebUtil.getWebModuleProperties(file);

    PsiElement result = null;
    if (isAbsolutePathReference()) {
      result = getAbsoluteTopLevelDirLocation(properties, project, file);
    }
    else {
      JspFile jspFile = PsiUtil.getJspFile(file);
      if (jspFile != null) {
        final JspContextManager manager = JspContextManager.getInstance(project);
        JspFile contextFile = manager.getContextFile(jspFile);
        Set<JspFile> visited = new HashSet<JspFile>();
        while (contextFile != null && !visited.contains(jspFile)) {
          visited.add(jspFile);
          jspFile = contextFile;
          contextFile = manager.getContextFile(jspFile);
        }
        file = jspFile;
      }

      final PsiDirectory dir = file.getContainingDirectory();
      if (dir != null) {
        if (properties != null) {
          result = JspManager.getInstance(project).findWebDirectoryByFile(dir.getVirtualFile(), properties);
          if (result == null) result = dir;
        }
        else {
          result = dir;
        }
      }
    }

    return result == null ? Collections.<PsiElement>emptyList() : Collections.singleton(result);
  }

  public String getPathString() {
    return myPathString;
  }

  public boolean isAbsolutePathReference() {
    return myPathString.startsWith(SEPARATOR_STRING);
  }

  @Nullable
  public static PsiElement getAbsoluteTopLevelDirLocation(final WebModuleProperties properties, final Project project, final PsiFile file) {
    PsiElement result = null;

    if (properties != null) {
      result = JspManager.getInstance(project).findWebDirectoryElementByPath("/", properties);
    }
    else {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        VirtualFile contentRootForFile = index.getSourceRootForFile(virtualFile);
        if (contentRootForFile == null) contentRootForFile = index.getContentRootForFile(virtualFile);

        if (contentRootForFile != null) {
          result = PsiManager.getInstance(project).findDirectory(contentRootForFile);
        }
      }
    }
    return result;
  }

  protected PsiScopeProcessor createProcessor(final List result, ReferenceType type)
    throws ProcessorRegistry.IncompatibleReferenceTypeException {
    return ProcessorRegistry.getProcessorByType(type, result, null);
  }

  public <Option> void addCustomization(CustomizableReferenceProvider.CustomizationKey<Option> key, Option value) {
    if (myOptions == null) {
      myOptions = new HashMap<CustomizableReferenceProvider.CustomizationKey, Object>(5);
    }
    myOptions.put(key,value);
  }
}

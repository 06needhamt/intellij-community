package com.intellij.psi.impl.source.xml;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.html.ScriptSupportUtil;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.parsing.xml.XmlPsiLexer;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class XmlFileImpl extends PsiFileImpl implements XmlFile {
  public XmlFileImpl(FileViewProvider viewProvider) {
    super(getElementType(viewProvider.getVirtualFile().getFileType()),
          getElementType(viewProvider.getVirtualFile().getFileType()), viewProvider);
  }

  public XmlFileImpl(FileViewProvider viewProvider, IElementType elementType, IElementType contentElementType) {
    super(elementType, contentElementType, viewProvider);
  }

  private static IElementType getElementType(final FileType fileTypeByFile) {
    if (fileTypeByFile == StdFileTypes.XML) return XML_FILE;
    if (fileTypeByFile == StdFileTypes.XHTML) return XHTML_FILE;
    if (fileTypeByFile == StdFileTypes.HTML) return HTML_FILE;
    if (fileTypeByFile == StdFileTypes.DTD) return DTD_FILE;
    return null;
  }

  public XmlDocument getDocument() {
    CompositeElement treeElement = calcTreeElement();
    ChameleonTransforming.transformChildren(treeElement);
    return (XmlDocument)treeElement.findChildByRoleAsPsiElement(ChildRole.XML_DOCUMENT);
  }

  public boolean processElements(PsiElementProcessor processor, PsiElement place){
    final XmlDocument document = getDocument();
    return document == null || document.processElements(processor, place);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlFile(this);
  }

  public String toString() {
    return "XmlFile:" + getName();
  }

  private FileType myType = null;
  @NotNull
  public FileType getFileType() {
    if (myType == null) {
      PsiFile originalFile = getOriginalFile();
      if (originalFile != null) {
        myType = originalFile.getFileType();
      }
      else {
        myType = FileTypeManager.getInstance().getFileTypeByFileName(getName());
      }
    }
    return myType;
  }

  public Lexer createLexer() {
    return new XmlPsiLexer();
  }

  public void subtreeChanged() {
    super.subtreeChanged();

    if (isWebFileType()) {
      ScriptSupportUtil.clearCaches(this);
    }
  }

  private boolean isWebFileType() {
    return getFileType() == StdFileTypes.XHTML || getFileType() == StdFileTypes.HTML;
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    if (!super.processDeclarations(processor, substitutor, lastParent, place)) return false;

    if (isWebFileType())
      return ScriptSupportUtil.processDeclarations(this, processor, substitutor, lastParent, place);

    return true;
  }
}

package com.intellij.lang.properties.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleImpl;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2005
 * Time: 12:25:04 AM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesFileImpl extends PsiFileBase implements PropertiesFile {
  private Map<String,List<Property>> myPropertiesMap;
  private List<Property> myProperties;

  public PropertiesFileImpl(Project project, VirtualFile file) {
    super(project, file, StdFileTypes.PROPERTIES.getLanguage());
  }

  public PropertiesFileImpl(Project project, String name, CharSequence text) {
    super(project, name, text, StdFileTypes.PROPERTIES.getLanguage());
  }

  @NotNull
  public FileType getFileType() {
    return StdFileTypes.PROPERTIES;
  }

  public String toString() {
    return "Property file:" + getName();
  }

  @NotNull
  public List<Property> getProperties() {
    ensurePropertiesLoaded();
    return myProperties;
  }

  private ASTNode getPropertiesList() {
    return getNode().findChildByType(PropertiesElementTypes.PROPERTIES_LIST);
  }

  private void ensurePropertiesLoaded() {
    if (myPropertiesMap != null) {
      return;
    }

    final ASTNode[] props = getPropertiesList().findChildrenByFilter(PropertiesElementTypes.PROPERTIES);
    myPropertiesMap = new LinkedHashMap<String, List<Property>>();
    myProperties = new ArrayList<Property>(props.length);
    for (final ASTNode prop : props) {
      final Property property = (Property) prop.getPsi();
      String key = property.getKey();
      List<Property> list = myPropertiesMap.get(key);
      if (list == null) {
        list = new SmartList<Property>();
        myPropertiesMap.put(key, list);
      }
      list.add(property);
      myProperties.add(property);
    }
  }

  public Property findPropertyByKey(@NotNull String key) {
    ensurePropertiesLoaded();
    List<Property> list = myPropertiesMap.get(key);
    return list == null ? null : list.get(0);
  }

  @NotNull
  public List<Property> findPropertiesByKey(@NotNull String key) {
    ensurePropertiesLoaded();
    List<Property> list = myPropertiesMap.get(key);
    return list == null ? Collections.EMPTY_LIST : list;
  }

  @NotNull public ResourceBundle getResourceBundle() {
    String baseName = PropertiesUtil.getBaseName(getVirtualFile());
    return new ResourceBundleImpl(getContainingFile().getContainingDirectory().getVirtualFile(), baseName);
  }

  @NotNull
  public Locale getLocale() {
    return PropertiesUtil.getLocale(getVirtualFile());
  }

  public PsiElement add(PsiElement element) throws IncorrectOperationException {
    if (element instanceof Property) {
      throw new IncorrectOperationException("Use addProperty() instead");
    }
    return super.add(element);
  }

  @NotNull
  public PsiElement addProperty(@NotNull Property property) throws IncorrectOperationException {
    final List<Property> properties = getProperties();
    if (properties.size() != 0) {
      Property lastProperty = properties.get(properties.size() - 1);
      final PsiElement nextSibling = lastProperty.getNextSibling();
      if (nextSibling == null || nextSibling.getText().indexOf("\n") == -1) {
        String text = "\n";
        LeafElement ws = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, text.toCharArray(), 0, text.length(), getTreeElement().getCharTable(), myManager);
        ChangeUtil.addChild((CompositeElement)getPropertiesList(), ws, null);
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
        documentManager.commitDocument(documentManager.getDocument(this));
      }
    }
    getPropertiesList().addChild(property.getNode());
    return property;
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myPropertiesMap = null;
    myProperties = null;
  }
}
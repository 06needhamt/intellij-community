package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.misc.PsiElementSetSpinAllocator;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.StringBuilderSpinAllocator;
import org.apache.tools.ant.taskdefs.Property;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

@SuppressWarnings({"HardCodedStringLiteral"})
public class AntProjectImpl extends AntStructuredElementImpl implements AntProject {

  private static final String[] EMPTY_STRING_ARRAY = new String[0];

  private volatile AntTarget[] myTargets;
  private volatile AntTarget[] myImportedTargets;
  private volatile List<AntFile> myImports;
  private AntFile[] myCachedImportsArray;
  private Set<String> myImportsDependentProperties;
    
  private volatile List<AntProperty> myPredefinedProps = new ArrayList<AntProperty>();
  private volatile Map<String, AntElement> myReferencedElements;
  private volatile String[] myRefIdsArray;
  private volatile AntProject myFakeProject; // project that holds predefined properties
  @NonNls private volatile List<String> myEnvPrefixes;
  @NonNls public static final String ourDefaultEnvPrefix = "env.";

  public AntProjectImpl(final AntFileImpl parent, final XmlTag tag, final AntTypeDefinition projectDefinition) {
    super(parent, tag);
    myDefinition = projectDefinition;
  }

  @NonNls
  public String toString() {
    final @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntProject[");
      final String name = getName();
      builder.append((name == null) ? "unnamed" : name);
      builder.append("]");
      if (getDescription() != null) {
        builder.append(" :");
        builder.append(getDescription());
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public AntElementRole getRole() {
    return AntElementRole.PROJECT_ROLE;
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      super.clearCaches();
      myTargets = null;
      myImportedTargets = null;
      clearImports();
      myReferencedElements = null;
      myRefIdsArray = null;
      myEnvPrefixes = null;
    }
  }

  private void clearImports() {
    myImports = null;
    myCachedImportsArray = null;
    myImportsDependentProperties = null;
  }

  @Nullable
  public String getBaseDir() {
    return computeAttributeValue(getSourceElement().getAttributeValue(AntFileImpl.BASEDIR_ATTR));
  }

  @Nullable
  public String getDescription() {
    final XmlTag tag = getSourceElement().findFirstSubTag(AntFileImpl.DESCRIPTION_ATTR);
    return tag != null ? tag.getValue().getTrimmedText() : null;
  }

  @NotNull
  public AntTarget[] getTargets() {
    synchronized (PsiLock.LOCK) {
      if (myTargets != null) return myTargets;
      //noinspection NonPrivateFieldAccessedInSynchronizedContext
      if (myInGettingChildren) return AntTarget.EMPTY_TARGETS;
      final List<AntTarget> targets = new ArrayList<AntTarget>();
      for (final AntElement child : getChildren()) {
        if (child instanceof AntTarget) targets.add((AntTarget)child);
      }
      final int size = targets.size();
      return myTargets = (size == 0) ? AntTarget.EMPTY_TARGETS : targets.toArray(new AntTarget[size]);
    }
  }

  @Nullable
  public AntTarget getDefaultTarget() {
    for (final PsiReference ref : getReferences()) {
      final GenericReference reference = (GenericReference)ref;
      if (reference.getType().isAssignableTo(ReferenceType.ANT_TARGET)) {
        return (AntTarget)reference.resolve();
      }
    }
    return null;
  }

  @NotNull
  public AntTarget[] getImportedTargets() {
    synchronized (PsiLock.LOCK) {
      if (myImportedTargets == null) {
        if (getImportedFiles().length == 0) {
          myImportedTargets = AntTarget.EMPTY_TARGETS;
        }
        else {
          final Set<PsiElement> targets = PsiElementSetSpinAllocator.alloc();
          try {
            final Set<PsiElement> elementsDepthStack = PsiElementSetSpinAllocator.alloc();
            try {
              getImportedTargets(this, targets, elementsDepthStack);
            }
            finally {
              PsiElementSetSpinAllocator.dispose(elementsDepthStack);
            }
            final int size = targets.size();
            myImportedTargets = (size == 0) ? AntTarget.EMPTY_TARGETS : targets.toArray(new AntTarget[size]);
          }
          finally {
            PsiElementSetSpinAllocator.dispose(targets);
          }
        }
      }
    }
    return myImportedTargets;
  }

  @Nullable
  public AntTarget getTarget(final String name) {
    for (final AntTarget target : getTargets()) {
      if (name.equals(target.getName())) {
        return target;
      }
    }
    return null;
  }

  /**
   * This method returns not only files that could be included via the <import>
   * task, but also a fake file which aggregates definitions resolved from all
   * entity references under the root tag.
   */
  @NotNull
  public AntFile[] getImportedFiles() {
    synchronized (PsiLock.LOCK) {
      if (myImports == null) {
        // this is necessary to avoid recurrent getImportedFiles() and stack overflow
        myImports = new ArrayList<AntFile>();
        final XmlTag se = getSourceElement();
        final PsiFile psiFile = se.getContainingFile();
        final StringBuilder builder = StringBuilderSpinAllocator.alloc();
        try {
          for (final XmlTag tag : se.getSubTags()) {
            // !!! a tag doesn't belong to the current file, so we decide it's resolved via an entity ref
            if (!psiFile.equals(tag.getContainingFile())) {
              buildTagText(tag, builder);
            }
            else if (AntFileImpl.IMPORT_TAG.equals(tag.getName())) {
              final String fileName = tag.getAttributeValue(AntFileImpl.FILE_ATTR);
              if (fileName != null) {
                final AntFile imported = AntImportImpl.getImportedFile(fileName, this);
                if (imported != null) {
                  addImportedFile(imported);
                }
                else {
                  registerImportsDependentProperties(fileName);
                }
              }
            }
          }
          if (builder.length() > 0) {
            builder.insert(0, "\">");
            final String baseDir = getBaseDir();
            if (baseDir != null && baseDir.length() > 0) {
              builder.insert(0, baseDir);
            }
            builder.insert(0, "<project basedir=\"");
            builder.append("</project>");
            final PsiElementFactory elementFactory = getManager().getElementFactory();
            final XmlFile xmlFile = (XmlFile)elementFactory.createFileFromText(
              "dummyEntities.xml", 
              StdFileTypes.XML, 
              builder.toString(), 
              LocalTimeCounter.currentTime(), 
              false, false
            );
            addImportedFile(new AntFileImpl(xmlFile.getViewProvider()) {
              //
              // this is necessary for computing file paths in tags resolved as entity references
              //
              public VirtualFile getContainingPath() {
                return AntProjectImpl.this.getAntFile().getContainingPath();
              }
            });
          }
        }
        finally {
          StringBuilderSpinAllocator.dispose(builder);
        }
      }
      if (myCachedImportsArray == null) {
        myCachedImportsArray = myImports.toArray(new AntFile[myImports.size()]);
      }
      return myCachedImportsArray;
    }
  }

  private void registerImportsDependentProperties(final String fileName) {
    int startProp = 0;
    while ((startProp = fileName.indexOf("${", startProp)) >= 0) {
      if (startProp == 0 || fileName.charAt(startProp - 1) != '$') {
        // if the '$' is not escaped
        final int endProp = fileName.indexOf('}', startProp + 2);
        if (endProp > startProp + 2) {
          if (myImportsDependentProperties == null) {
            myImportsDependentProperties = new HashSet<String>();
          }
          myImportsDependentProperties.add(fileName.substring(startProp + 2, endProp));
        }
      }
      startProp += 2;
    }
  }

  private void addImportedFile(final AntFile imported) {
    try {
      myImports.add(imported);
    }
    finally {
      myCachedImportsArray = null;
    }
  }

  public void registerRefId(final String id, AntElement element) {
    synchronized (PsiLock.LOCK) {
      if (id == null || id.length() == 0) return;
      if (myReferencedElements == null) {
        myReferencedElements = new HashMap<String, AntElement>();
      }
      myReferencedElements.put(id, element);
    }
  }

  @Nullable
  public AntElement getElementByRefId(String refid) {
    synchronized (PsiLock.LOCK) {
      if (myReferencedElements == null) return null;
      refid = computeAttributeValue(refid);
      return myReferencedElements.get(refid);
    }
  }

  @NotNull
  public String[] getRefIds() {
    synchronized (PsiLock.LOCK) {
      if (myRefIdsArray == null) {
        if (myReferencedElements == null) {
          myRefIdsArray = EMPTY_STRING_ARRAY;
        }
        else {
          myRefIdsArray = myReferencedElements.keySet().toArray(new String[myReferencedElements.size()]);
        }
      }
      return myRefIdsArray;
    }
  }

  public void addEnvironmentPropertyPrefix(@NotNull final String envPrefix) {
    synchronized (PsiLock.LOCK) {
      checkEnvList();
      final String env = (envPrefix.endsWith(".")) ? envPrefix : envPrefix + '.';
      if (myEnvPrefixes.indexOf(env) < 0) {
        myEnvPrefixes.add(env);
        for (final AntProperty element : getProperties()) {
          final String name = element.getName();
          if (name != null && name.startsWith(ourDefaultEnvPrefix)) {
            setProperty(env + name.substring(ourDefaultEnvPrefix.length()), element);
          }
        }
        if (myFakeProject != null) {
          myFakeProject.addEnvironmentPropertyPrefix(envPrefix);
        }
      }
    }
  }

  public boolean isEnvironmentProperty(@NotNull final String propName) {
    synchronized (PsiLock.LOCK) {
      checkEnvList();
      if (!propName.endsWith(".")) {
        for (final String prefix : myEnvPrefixes) {
          if (propName.startsWith(prefix)) {
            return true;
          }
        }
      }
      return false;
    }
  }

  public List<String> getEnvironmentPrefixes() {
    synchronized (PsiLock.LOCK) {
      checkEnvList();
      return myEnvPrefixes;
    }
  }

  @Nullable
  public AntProperty getProperty(final String name) {
    synchronized (PsiLock.LOCK) {
      if (getParent() == null) return null;
      checkPropertiesMap();
      return super.getProperty(name);
    }
  }

  public void setProperty(final String name, final AntProperty element) {
    synchronized (PsiLock.LOCK) {
      if (getParent() != null) {
        checkPropertiesMap();
        super.setProperty(name, element);
        // hack: if there are any imports defined in terms of this property, they will be recalculated 
        if (myImportsDependentProperties != null && myImportsDependentProperties.contains(name)) {
          clearImports();
        }
      }
    }
  }

  @NotNull
  public AntProperty[] getProperties() {
    synchronized (PsiLock.LOCK) {
      if (getParent() == null) return AntProperty.EMPTY_ARRAY;
      checkPropertiesMap();
      return super.getProperties();
    }
  }

  @SuppressWarnings({"UseOfObsoleteCollectionType"})
  void loadPredefinedProperties(final Hashtable properties, final Map<String, String> externalProps) {
    final Enumeration props = (properties != null) ? properties.keys() : (new Hashtable()).keys();
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    builder.append("<project name=\"predefined properties\">");
    try {
      while (props.hasMoreElements()) {
        final String name = (String)props.nextElement();
        final String value = (String)properties.get(name);
        builder.append("<property name=\"");
        builder.append(name);
        builder.append("\" value=\"");
        builder.append(value);
        builder.append("\"/>");
      }
      final Map<String, String> envMap = System.getenv();
      for (final String name : envMap.keySet()) {
        final String value = envMap.get(name);
        if (name.length() > 0) {
          builder.append("<property name=\"");
          builder.append(ourDefaultEnvPrefix);
          builder.append(name);
          builder.append("\" value=\"");
          builder.append(value);
          builder.append("\"/>");
        }
      }
      if (externalProps != null) {
        for (final String name : externalProps.keySet()) {
          final String value = externalProps.get(name);
          builder.append("<property name=\"");
          builder.append(name);
          builder.append("\" value=\"");
          builder.append(value);
          builder.append("\"/>");
        }
      }
      final VirtualFile file = getAntFile().getVirtualFile();
      String basedir = getBaseDir();
      if (basedir == null) {
        basedir = ".";
      }
      if (file != null) {
        final VirtualFile dir = file.getParent();
        if (dir != null) {
          try {
            basedir = new File(dir.getPath(), basedir).getCanonicalPath();
          }
          catch (IOException e) {
            // ignore
          }
        }
      }
      if (basedir != null) {
        builder.append("<property name=\"basedir\" value=\"");
        builder.append(basedir);
        builder.append("\"/>");
      }
      builder.append("<property name=\"ant.home\" value=\"\"/>");
      builder.append("<property name=\"ant.version\" value=\"1.6.5\"/>");
      builder.append("<property name=\"ant.project.name\" value=\"");
      final String name = getName();
      builder.append((name == null) ? "" : name);
      builder.append("\"/>");
      builder.append("<property name=\"ant.java.version\" value=\"");
      builder.append(SystemInfo.JAVA_VERSION);
      builder.append("\"/>");
      if (file != null) {
        final String path = file.getPath();
        builder.append("<property name=\"ant.file\" value=\"");
        builder.append(path);
        builder.append("\"/>");
        if (name != null) {
          builder.append("<property name=\"ant.file.");
          builder.append(name);
          builder.append("\" value=\"${ant.file}\"/>");
        }
      }
      builder.append("</project>");
      final XmlFile xmlFile = (XmlFile)getManager().getElementFactory()
        .createFileFromText("dummy.xml", StdFileTypes.XML, builder, LocalTimeCounter.currentTime(), false, false);
      final XmlDocument document = xmlFile.getDocument();
      if (document == null) return;
      final XmlTag rootTag = document.getRootTag();
      if (rootTag == null) return;
      final AntTypeDefinition propertyDef = getAntFile().getBaseTypeDefinition(Property.class.getName());
      myFakeProject = new AntProjectImpl(null, rootTag, myDefinition);
      for (final XmlTag tag : rootTag.getSubTags()) {
        final AntPropertyImpl property = new AntPropertyImpl(myFakeProject, tag, propertyDef) {
          public PsiFile getContainingFile() {
            return getSourceElement().getContainingFile();
          }

          public PsiElement getNavigationElement() {
            if (AntFileImpl.BASEDIR_ATTR.equals(getName())) {
              final XmlAttribute attr = AntProjectImpl.this.getSourceElement().getAttribute(AntFileImpl.BASEDIR_ATTR, null);
              if (attr != null) return attr;
            }
            return super.getNavigationElement();
          }
        };
        myPredefinedProps.add(property);
      }
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
    setPredefinedProperties();
  }

  private void setPredefinedProperties() {
    for (final AntProperty property : myPredefinedProps) {
      setProperty(property.getName(), property);
    }
  }

  private void checkPropertiesMap() {
    if (myProperties == null) {
      myProperties = new HashMap<String, AntProperty>(myPredefinedProps.size());
      setPredefinedProperties();
    }
  }

  private void checkEnvList() {
    if (myEnvPrefixes == null) {
      myEnvPrefixes = new ArrayList<String>();
      myEnvPrefixes.add(ourDefaultEnvPrefix);
    }
  }

  protected AntElement[] getChildrenInner() {
    synchronized (PsiLock.LOCK) {
      if (!myInGettingChildren) {
        final AntElement[] children = super.getChildrenInner();
        fixUndefinedElements(this, children);
        return children;
      }
      return AntElement.EMPTY_ARRAY;
    }
  }

  private static void fixUndefinedElements(final AntStructuredElement parent, final AntElement[] elements) {
    for (int i = 0; i < elements.length; i++) {
      final AntElement element = elements[i];
      if (element instanceof AntStructuredElement) {
        AntStructuredElement se = (AntStructuredElement)element;
        if (se.getTypeDefinition() == null) {
          se = (AntStructuredElement)AntElementFactory.createAntElement(parent, se.getSourceElement());
          if (se != null) {
            elements[i] = se;
          }
        }
        if (se != null) {
          fixUndefinedElements(se, (AntElement[])element.getChildren());
        }
      }
    }
  }

  private static void getImportedTargets(final AntProject project,
                                         final Set<PsiElement> targets,
                                         final Set<PsiElement> elementsDepthStack) {
    if (elementsDepthStack.contains(project)) return;
    elementsDepthStack.add(project);
    try {
      for (final AntFile imported : project.getImportedFiles()) {
        final AntProject importedProject = imported.getAntProject();
        if (importedProject != null && !elementsDepthStack.contains(importedProject)) {
          for (final AntTarget target : importedProject.getTargets()) {
            targets.add(target);
          }
        }
        getImportedTargets(importedProject, targets, elementsDepthStack);
      }
    }
    finally {
      elementsDepthStack.remove(project);
    }
  }

  private static void buildTagText(final XmlTag tag, final StringBuilder builder) {
    //
    // this quite strange creation of the tag text is necessary since
    // tag.getText() removes whitespaces from source text
    //
    boolean firstChild = true;
    for (final PsiElement tagChild : tag.getChildren()) {
      if (!firstChild) {
        builder.append(' ');
      }
      if (tagChild instanceof XmlTag) {
        buildTagText((XmlTag)tagChild, builder);
      }
      else {
        builder.append(tagChild.getText());
      }
      firstChild = false;
    }
  }
}

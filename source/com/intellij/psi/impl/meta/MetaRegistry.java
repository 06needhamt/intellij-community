package com.intellij.psi.impl.meta;

import com.intellij.jsp.impl.RelaxedNsXmlNSDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.NamespaceFilter;
import com.intellij.psi.filters.position.TargetNamespaceFilter;
import com.intellij.psi.meta.MetaDataRegistrar;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.*;
import com.intellij.reference.SoftReference;
import com.intellij.xml.impl.schema.NamedObjectDescriptor;
import com.intellij.xml.impl.schema.SchemaNSDescriptor;
import com.intellij.xml.impl.schema.XmlAttributeDescriptorImpl;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.05.2003
 * Time: 3:31:09
 * To change this template use Options | File Templates.
 */
public class MetaRegistry extends MetaDataRegistrar implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.meta.MetaRegistry");
  private static final List<MyBinding> ourBindings = new ArrayList<MyBinding>();

  public static final String[] SCHEMA_URIS = { XmlUtil.XML_SCHEMA_URI, XmlUtil.XML_SCHEMA_URI2, XmlUtil.XML_SCHEMA_URI3 };

  static {
    {
      addMetadataBinding(
          new AndFilter(
              new NamespaceFilter(SCHEMA_URIS),
              new ClassFilter(XmlDocument.class)
          ),
          SchemaNSDescriptor.class
      );

      addMetadataBinding(
          new AndFilter(
              new NamespaceFilter(SCHEMA_URIS),
              new TextFilter("schema")
          ),
          SchemaNSDescriptor.class
      );
    }
    {
      addMetadataBinding(
          new OrFilter(
              new AndFilter(
                  new ContentFilter(
                    new OrFilter(
                      new ClassFilter(XmlElementDecl.class),
                      new ClassFilter(XmlConditionalSection.class)
                    )
                  ),
                  new ClassFilter(XmlDocument.class)
              ),
              new ClassFilter(XmlMarkupDecl.class)
          ),
          com.intellij.xml.impl.dtd.XmlNSDescriptorImpl.class
      );
    }

    {
      addMetadataBinding(new AndFilter(
          new NamespaceFilter(SCHEMA_URIS),
          new TextFilter("element")
      ),
                         XmlElementDescriptorImpl.class);
    }

    {
      addMetadataBinding(
          new AndFilter(
              new NamespaceFilter(SCHEMA_URIS),
              new TextFilter("attribute")
          ),
          XmlAttributeDescriptorImpl.class
      );
    }

    {
      addMetadataBinding(
          new ClassFilter(XmlElementDecl.class),
          com.intellij.xml.impl.dtd.XmlElementDescriptorImpl.class
      );
    }

    {
      addMetadataBinding(
          new ClassFilter(XmlAttributeDecl.class),
          com.intellij.xml.impl.dtd.XmlAttributeDescriptorImpl.class
      );
    }

    {
      addMetadataBinding(
          new AndFilter(
              new ClassFilter(XmlDocument.class),
              new TargetNamespaceFilter(XmlUtil.XHTML_URI),
              new NamespaceFilter(SCHEMA_URIS)),
          RelaxedNsXmlNSDescriptor.class
      );
    }

    {
      addMetadataBinding(new AndFilter(
          new NamespaceFilter(SCHEMA_URIS),
          new TextFilter(new String[] {"complexType","simpleType", "group","attributeGroup" })
      ),
                         NamedObjectDescriptor.class);
    }

  }

  public static final Key<SoftReference<CachedValue<PsiMetaDataBase>>> META_DATA_KEY = Key.create("META DATA KEY");

  public static void bindDataToElement(final PsiElement element, final PsiMetaDataBase data){
    SoftReference<CachedValue<PsiMetaDataBase>> value = new SoftReference<CachedValue<PsiMetaDataBase>>(
      element.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<PsiMetaDataBase>() {
      public CachedValueProvider.Result<PsiMetaDataBase> compute() {
        data.init(element);
        return new Result<PsiMetaDataBase>(data, data.getDependences());
      }
    }));
    element.putUserData(META_DATA_KEY, value);
  }

  public MetaRegistry() {
    // RegisterInPsi.metaData(this);
  }

  private final static SoftReference<CachedValue<PsiMetaDataBase>> NULL = new SoftReference<CachedValue<PsiMetaDataBase>>(null);

  public static PsiMetaData getMeta(final PsiElement element) {
    final PsiMetaDataBase base = getMetaBase(element);
    return base instanceof PsiMetaData ? (PsiMetaData)base : null;
  }

  public static PsiMetaDataBase getMetaBase(final PsiElement element) {
    ProgressManager.getInstance().checkCanceled();
    PsiMetaDataBase ret = null;
    SoftReference<CachedValue<PsiMetaDataBase>> value = element.getUserData(META_DATA_KEY);
    if (value == null || (value != NULL && value.get() == null)) {
      for (final MyBinding binding : ourBindings) {
        try {
          if (isAcceptable(binding.myFilter, element)) {
            final PsiMetaDataBase data = binding.myDataClass.newInstance();
            final CachedValue<PsiMetaDataBase> cachedValue = element.getManager().getCachedValuesManager()
              .createCachedValue(new CachedValueProvider<PsiMetaDataBase>() {
                public Result<PsiMetaDataBase> compute() {
                  if (!isAcceptable(binding.myFilter, element)) {
                    clearMetaForElement(element);
                    final PsiMetaDataBase data1 = getMeta(element);
                    if (data1 == null) return new Result<PsiMetaDataBase>(null);
                    return new Result<PsiMetaDataBase>(data1, data1.getDependences());
                  }

                  data.init(element);
                  return new Result<PsiMetaDataBase>(data, data.getDependences());
                }
              }, false);
            value = new SoftReference<CachedValue<PsiMetaDataBase>>(cachedValue);
            ret = cachedValue.getValue();
            break;
          }
        }
        catch (IllegalAccessException iae) {
          value = null;
        }
        catch (InstantiationException ie) {
          value = null;
        }
      }
      element.putUserData(META_DATA_KEY, value != null ? value : NULL);
    }
    else if(value != NULL){
      ret = value.get().getValue();
    }

    return ret;
  }

  private static boolean isAcceptable(final ElementFilter filter, final PsiElement element) {
    return filter.isClassAcceptable(element.getClass()) &&
        filter.isAcceptable(element, element.getParent());
  }

  public static <T extends PsiMetaDataBase> void addMetadataBinding(ElementFilter filter, Class<T> aMetadataClass, Disposable parentDisposable) {
    final MyBinding binding = new MyBinding(filter, aMetadataClass);
    addBinding(binding);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        ourBindings.remove(binding);
      }
    });
  }

  public static <T extends PsiMetaDataBase> void addMetadataBinding(ElementFilter filter, Class<T> aMetadataClass) {
    addBinding(new MyBinding(filter, aMetadataClass));
  }

  private static <T extends PsiMetaDataBase> void addBinding(final MyBinding binding) {
    ourBindings.add(0, binding);
  }

  public static void clearMetaForElement(PsiElement element) {
    element.putUserData(META_DATA_KEY, null);
  }

  public <T extends PsiMetaDataBase> void registerMetaData(ElementFilter filter, Class<T> metadataDescriptorClass) {
    addMetadataBinding(filter, metadataDescriptorClass);
  }

  @NonNls
  public String getComponentName() {
    return "MetaRegistry";
  }

  public void initComponent() {}
  public void disposeComponent() {}

  private static class MyBinding {
    ElementFilter myFilter;
    Class<PsiMetaDataBase> myDataClass;

    public <T extends PsiMetaDataBase> MyBinding(ElementFilter filter, Class<T> dataClass) {
      LOG.assertTrue(filter != null);
      LOG.assertTrue(dataClass != null);
      myFilter = filter;
      myDataClass = (Class)dataClass;
    }
  }
}

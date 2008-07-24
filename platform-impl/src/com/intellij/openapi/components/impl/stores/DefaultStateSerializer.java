package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageId;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.io.IOException;


@SuppressWarnings({"deprecation"})
class DefaultStateSerializer {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.DefaultStateSerializer");

  private DefaultStateSerializer() {
  }

  static Element serializeState(Object state, final Storage storage) throws  WriteExternalException {
    if (state instanceof Element) {
      return (Element)state;
    }
    else if (state instanceof JDOMExternalizable) {
      JDOMExternalizable jdomExternalizable = (JDOMExternalizable)state;

      final Element element = new Element("temp_element");
      jdomExternalizable.writeExternal(element);
      return element;
    }
    else {
      return  XmlSerializer.serialize(state, new SkipDefaultValuesSerializationFilters() {
        public boolean accepts(final Accessor accessor, final Object bean) {
          if (!super.accepts(accessor, bean)) return false;

          if (storage != null) {
            final Annotation[] annotations = accessor.getAnnotations();
            for (Annotation annotation : annotations) {
              if (StorageId.class.isAssignableFrom(annotation.annotationType())) {
                StorageId storageId = (StorageId)annotation;

                if (!storageId.value().equals(storage.id())) return false;
              }
            }

            return storage.isDefault();
          }

          return true;
        }
      });
    }
  }

  @SuppressWarnings({"unchecked"})
  @Nullable
  static <T> T deserializeState(@Nullable Element stateElement, Class <T> stateClass, @Nullable T mergeInto) throws StateStorage.StateStorageException {
    if (stateElement == null) return mergeInto;

    if (stateClass.equals(Element.class)) {
      //assert mergeInto == null;
      return (T)stateElement;
    }
    else if (JDOMExternalizable.class.isAssignableFrom(stateClass)) {
      if (mergeInto != null) {
        try {
          String elementText = JDOMUtil.writeElement(stateElement, "\n");
          LOG.error("State is " + stateClass.getName() + ", merge into is " + mergeInto.toString() + ", state element text is " + elementText);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
      try {
        final T t = stateClass.newInstance();
        try {
          ((JDOMExternalizable)t).readExternal(stateElement);
          return t;
        }
        catch (InvalidDataException e) {
          throw new StateStorage.StateStorageException(e);
        }
      }
      catch (InstantiationException e) {
        throw new StateStorage.StateStorageException(e);
      }
      catch (IllegalAccessException e) {
        throw new StateStorage.StateStorageException(e);
      }
    }
    else {
      if (mergeInto == null) {
        return XmlSerializer.deserialize(stateElement, stateClass);
      }
      else {
        XmlSerializer.deserializeInto(mergeInto, stateElement);
        return mergeInto;
      }
    }
  }

}

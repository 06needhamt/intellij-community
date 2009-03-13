package com.intellij.util.indexing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 26, 2007
 * V class MUST have equals / hashcode properly defined!!!
 */
public interface FileBasedIndexExtension<K, V> {
  ExtensionPointName<FileBasedIndexExtension> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.fileBasedIndex");
  int DEFAULT_CACHE_SIZE = 1024;

  ID<K, V> getName();
  
  DataIndexer<K, V, FileContent> getIndexer();
  
  KeyDescriptor<K> getKeyDescriptor();
  
  DataExternalizer<V> getValueExternalizer();
  
  FileBasedIndex.InputFilter getInputFilter();
  
  boolean dependsOnFileContent();
  
  int getVersion();

  /**
   * @see FileBasedIndexExtension#DEFAULT_CACHE_SIZE
   */
  int getCacheSize();
}

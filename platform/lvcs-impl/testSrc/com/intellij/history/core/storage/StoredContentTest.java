package com.intellij.history.core.storage;

import com.intellij.history.core.InMemoryStorage;
import com.intellij.history.core.LocalVcsTestCase;
import org.junit.Test;


public class StoredContentTest extends LocalVcsTestCase {
  @Test
  public void testEqualityAndHash() {
    assertTrue(new StoredContent(null, 1).equals(new StoredContent(null, 1)));

    assertFalse(new StoredContent(null, 1).equals(null));
    assertFalse(new StoredContent(null, 1).equals(new StoredContent(null, 2)));

    assertTrue(new StoredContent(null, 1).hashCode() == new StoredContent(null, 1).hashCode());
    assertTrue(new StoredContent(null, 1).hashCode() != new StoredContent(null, 2).hashCode());
  }

  @Test
  public void testUnavailableIfExceptionOccurs() {
    Storage goodStorage = new InMemoryStorage() {
      @Override
      protected byte[] loadContentData(final int id) throws BrokenStorageException {
        return new byte[0];
      }
    };

    Storage brokenStorage = new InMemoryStorage() {
      @Override
      protected byte[] loadContentData(int id) throws BrokenStorageException {
        throw new BrokenStorageException();
      }
    };

    assertTrue(new StoredContent(goodStorage, 0).isAvailable());
    assertFalse(new StoredContent(brokenStorage, 0).isAvailable());
  }
}

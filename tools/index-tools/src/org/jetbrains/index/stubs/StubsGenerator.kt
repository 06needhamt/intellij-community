/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * @author traff
 */

package org.jetbrains.index.stubs

import com.google.common.hash.HashCode
import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.stubs.*
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.io.PersistentHashMap
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import java.io.File
import java.util.*


open class StubsGenerator(private val stubsVersion: String) {

  fun buildStubsForRoots(stubsStorageFilePath: String,
                         roots: List<VirtualFile>,
                         fileFilter: VirtualFileFilter) {
    val hashing = FileContentHashing()

    val stubExternalizer = StubTreeExternalizer()
    val storage = PersistentHashMap<HashCode, SerializedStubTree>(File(stubsStorageFilePath + ".input"),
                                                                  HashCodeDescriptor.instance, stubExternalizer)

    println("Writing stubs to ${storage.baseFile.absolutePath}")

    val serializationManager = SerializationManagerImpl(File(stubsStorageFilePath + ".names"))

    try {
      val map = HashMap<HashCode, Pair<String, SerializedStubTree>>()

      for (file in roots) {
        VfsUtilCore.visitChildrenRecursively(file, object : VirtualFileVisitor<Boolean>() {
          override fun visitFile(file: VirtualFile): Boolean {
            try {
              if (fileFilter.accept(file)) {
                val fileContent = FileContentImpl(file, file.contentsToByteArray())
                val stub = buildStubForFile(fileContent, serializationManager)
                val hashCode = hashing.hashString(fileContent)

                val bytes = BufferExposingByteArrayOutputStream()
                serializationManager.serialize(stub, bytes)

                val contentLength =
                  if (file.fileType.isBinary) {
                    -1
                  }
                  else {
                    fileContent.psiFileForPsiDependentIndex.textLength
                  }

                val stubTree = SerializedStubTree(bytes.internalBuffer, bytes.size(), stub, file.length, contentLength)
                val item = map.get(hashCode)
                if (item == null) {
                  storage.put(hashCode, stubTree)
                  map.put(hashCode, Pair(fileContent.contentAsText.toString(), stubTree))
                }
                else {
                  TestCase.assertEquals(item.first, fileContent.contentAsText.toString())
                  TestCase.assertTrue(stubTree == item.second)
                }
              }
            }
            catch (e: NoSuchElementException) {
              return false
            }

            return true
          }
        })
      }
    }
    finally {
      storage.close()
      Disposer.dispose(serializationManager)

      writeStubsVersionFile(stubsStorageFilePath, stubsVersion)
    }
  }

  open fun buildStubForFile(fileContent: FileContentImpl,
                            serializationManager: SerializationManagerImpl): Stub {

    return ReadAction.compute<Stub, Throwable> { StubTreeBuilder.buildStubTree(fileContent) }
  }
}

fun writeStubsVersionFile(stubsStorageFilePath: String, stubsVersion: String) {
  FileUtil.writeToFile(File(stubsStorageFilePath + ".version"), stubsVersion)
}

fun mergeStubs(paths: List<String>, stubsFilePath: String, projectPath: String, stubsVersion: String) {
  val app = IdeaTestApplication.getInstance()
  val project = ProjectManager.getInstance().loadAndOpenProject(projectPath)!!
  // we don't need a project here, but I didn't find a better way to wait until indexes and components are initialized

  try {
    val stubExternalizer = StubTreeExternalizer()

    val storageFile = File(stubsFilePath + ".input")
    if (storageFile.exists()) {
      storageFile.delete()
    }

    val storage = PersistentHashMap<HashCode, SerializedStubTree>(storageFile,
                                                                  HashCodeDescriptor.instance, stubExternalizer)

    val stringEnumeratorFile = File(stubsFilePath + ".names")
    if (stringEnumeratorFile.exists()) {
      stringEnumeratorFile.delete()
    }

    val newSerializationManager = SerializationManagerImpl(stringEnumeratorFile)

    val map = HashMap<HashCode, Int>()

    println("Writing results to $stubsFilePath")

    for (path in paths) {
      println("Reading stubs from $path")
      var count = 0
      val fromStorageFile = File(stubsFilePath + ".input")
      val fromStorage = PersistentHashMap<HashCode, SerializedStubTree>(fromStorageFile,
                                                                        HashCodeDescriptor.instance, stubExternalizer)

      val serializationManager = SerializationManagerImpl(File(stubsFilePath + ".names"))

      try {
        fromStorage.processKeysWithExistingMapping(
          { key ->
            count++
            val value = fromStorage.get(key)

            val stub = value.getStub(false, serializationManager)

            // re-serialize stub tree to correctly enumerate strings in the new string enumerator
            val bytes = BufferExposingByteArrayOutputStream()
            newSerializationManager.serialize(stub, bytes)

            val newStubTree = SerializedStubTree(bytes.internalBuffer, bytes.size(), null, value.byteContentLength,
                                                 value.charContentLength)

            if (storage.containsMapping(key)) {
              if (newStubTree != storage.get(key)) { // TODO: why are they slightly different???
                val s = storage.get(key).getStub(false, newSerializationManager)

                val bytes2 = BufferExposingByteArrayOutputStream()
                newSerializationManager.serialize(stub, bytes2)

                val newStubTree2 = SerializedStubTree(bytes2.internalBuffer, bytes2.size(), null, value.byteContentLength,
                                                      value.charContentLength)

                TestCase.assertTrue(newStubTree == newStubTree2) // wtf!!! why are they equal now???
              }
              map.put(key, map.get(key)!! + 1)
            }
            else {
              storage.put(key, newStubTree)
              map.put(key, 1)
            }
            true
          })

      }
      finally {
        fromStorage.close()
        Disposer.dispose(serializationManager)
      }

      println("Items in ${fromStorageFile.absolutePath}: $count")
    }

    storage.close()
    Disposer.dispose(newSerializationManager)

    val total = map.size

    println("Total items in storage: $total")

    writeStubsVersionFile(stubsFilePath, stubsVersion)

    for (i in 2..paths.size) {
      val count = map.entries.stream().filter({ e -> e.value == i }).count()
      println("Intersection between $i: ${"%.2f".format(if (total > 0) 100.0 * count / total else 0.0)}%")
    }
  }
  finally {
    UIUtil.invokeAndWaitIfNeeded(Runnable {
      ProjectManager.getInstance().closeProject(project)
      WriteAction.run<Throwable> {
        Disposer.dispose(project)
        Disposer.dispose(app)
      }
    })

  }

  System.exit(0)
}


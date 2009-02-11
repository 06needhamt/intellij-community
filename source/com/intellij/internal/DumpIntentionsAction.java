/*
 * User: anna
 * Date: 28-Jun-2007
 */
package com.intellij.internal;

import com.intellij.codeInsight.intention.impl.config.IntentionActionMetaData;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DumpIntentionsAction extends AnAction {
  public DumpIntentionsAction() {
    super("Dump Intentions");
  }

  public void actionPerformed(AnActionEvent e) {
    final VirtualFile[] files =
      FileChooser.chooseFiles(e.getData(PlatformDataKeys.PROJECT), FileChooserDescriptorFactory.createSingleFolderDescriptor());
    if (files.length > 0) {
      final List<IntentionActionMetaData> list = IntentionManagerSettings.getInstance().getMetaData();
      final File root = VfsUtil.virtualToIoFile(files[0]);
      Element el = new Element("root");
      Map<String, Element> categoryMap = new HashMap<String, Element>();
      for (IntentionActionMetaData metaData : list) {

        try {
          Element metadataElement = new Element("intention");
          metadataElement.setAttribute("family", metaData.getFamily());
          metadataElement.setAttribute("description", metaData.getDescription().getText());

          String key = StringUtil.join(metaData.myCategory, ".");
          Element element = getCategoryElement(categoryMap, el, metaData, key, metaData.myCategory.length - 1);
          element.addContent(metadataElement);
        }
        catch (IOException e1) {
          e1.printStackTrace();
        }
      }

      try {
        JDOMUtil.writeDocument(new Document(el), new File(root, "intentions.xml"), "\n");
      }
      catch (IOException e1) {
        e1.printStackTrace();
      }
    }
  }

  private static Element getCategoryElement(Map<String, Element> categoryMap, Element rootElement, IntentionActionMetaData metaData, String key, int idx) {
    Element element = categoryMap.get(key);
    if (element == null) {

      element = new Element("category");
      element.setAttribute("name", metaData.myCategory[idx]);
      categoryMap.put(key, element);
      if (idx == 0) {
        rootElement.addContent(element);
      } else {
        getCategoryElement(categoryMap, rootElement, metaData, StringUtil.join(metaData.myCategory, 0, metaData.myCategory.length - 1, "."), idx - 1).addContent(element);
      }
    }
    return element;
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(PlatformDataKeys.PROJECT) != null);
  }
}
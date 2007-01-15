/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 05-Jan-2007
 */
package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.ex.InspectionApplication;
import com.intellij.codeInspection.reference.SmartRefElementPointerImpl;
import com.thoughtworks.xstream.io.xml.XppReader;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.StringReader;
import java.util.*;

public class OfflineViewParseUtil {
  @NonNls private static final String PACKAGE = "package";
  @NonNls private static final String DESCRIPTION = "description";
  @NonNls private static final String HINTS = "hints";
  @NonNls private static final String LINE = "line";
  @NonNls private static final String MODULE = "module";

  private OfflineViewParseUtil() {
  }

  public static Map<String, Set<OfflineProblemDescriptor>> parse(final String problems) {
    final TObjectIntHashMap<String> fqName2IdxMap = new TObjectIntHashMap<String>();
    final Map<String, Set<OfflineProblemDescriptor>> package2Result = new THashMap<String, Set<OfflineProblemDescriptor>>();
    final XppReader reader = new XppReader(new StringReader(problems));
    try {
      while(reader.hasMoreChildren()) {
        reader.moveDown(); //problem
        final OfflineProblemDescriptor descriptor = new OfflineProblemDescriptor();
        while(reader.hasMoreChildren()) {
          reader.moveDown();
          if (SmartRefElementPointerImpl.ENTRY_POINT.equals(reader.getNodeName())) {
            descriptor.setType(reader.getAttribute(SmartRefElementPointerImpl.TYPE_ATTR));
            descriptor.setFQName(reader.getAttribute(SmartRefElementPointerImpl.FQNAME_ATTR));
            final List<String> parentTypes = new ArrayList<String>();
            final List<String> parentNames = new ArrayList<String>();
            int deep = 0;
            while (true) {
              if (reader.hasMoreChildren()) {
                reader.moveDown();
                parentTypes.add(reader.getAttribute(SmartRefElementPointerImpl.TYPE_ATTR));
                parentNames.add(reader.getAttribute(SmartRefElementPointerImpl.FQNAME_ATTR));                
                deep ++;
              } else {
                while (deep-- > 0) {
                  reader.moveUp();
                }
                break;
              }
            }
            if (!parentTypes.isEmpty() && !parentNames.isEmpty()) {
              descriptor.setParentType(parentTypes.toArray(new String[parentTypes.size()]));
              descriptor.setParentFQName(parentNames.toArray(new String[parentNames.size()]));
            }
          }
          if (DESCRIPTION.equals(reader.getNodeName())) {
            descriptor.setDescription(reader.getValue());
          }
          if (LINE.equals(reader.getNodeName())) {
            descriptor.setLine(Integer.parseInt(reader.getValue()));
          }
          if (MODULE.equals(reader.getNodeName())) {
            descriptor.setModule(reader.getValue());
          }
          if (HINTS.equals(reader.getNodeName())) {
            while(reader.hasMoreChildren()) {
              reader.moveDown();
              List<String> hints = descriptor.getHints();
              if (hints == null) {
                hints = new ArrayList<String>();
                descriptor.setHints(hints);
              }
              hints.add(reader.getValue());
              reader.moveUp();
            }
          }
          if (PACKAGE.equals(reader.getNodeName())) {
            appendDescriptor(package2Result, reader.getValue(), descriptor);
          }
          while(reader.hasMoreChildren()) {
            reader.moveDown();
            if (PACKAGE.equals(reader.getNodeName())) {
              appendDescriptor(package2Result, reader.getValue(), descriptor);
            }
            reader.moveUp();
          }
          reader.moveUp();
        }

        final String fqName = descriptor.getFQName();
        if (!fqName2IdxMap.containsKey(fqName)) {
          fqName2IdxMap.put(fqName, 0);
        }
        int idx = fqName2IdxMap.get(fqName);
        descriptor.setProblemIndex(idx);
        fqName2IdxMap.put(fqName, idx + 1);

        reader.moveUp();
      }
    }
    finally {
      reader.close();
    }
    return package2Result;
  }

  private static void appendDescriptor(final Map<String, Set<OfflineProblemDescriptor>> package2Result,
                                       final String packageName,
                                       final OfflineProblemDescriptor descriptor) {
    Set<OfflineProblemDescriptor> descriptors = package2Result.get(packageName);
    if (descriptors == null) {
      descriptors = new HashSet<OfflineProblemDescriptor>();
      package2Result.put(packageName, descriptors);
    }
    descriptors.add(descriptor);
  }

  @Nullable
  public static String parseProfileName(String descriptors) {
    final XppReader reader = new XppReader(new StringReader(descriptors));
    try {
      return reader.getAttribute(InspectionApplication.PROFILE);
    }
    catch (Exception e) {
      return null;
    }
    finally {
      reader.close();
    }
  }
}
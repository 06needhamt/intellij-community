package com.intellij.structuralsearch.impl.matcher;

import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.HtmlUtil;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 19, 2004
 * Time: 6:56:25 PM
 * To change this template use File | Settings | File Templates.
 */
public class MatcherImplUtil {
  public static final Key<List<PsiCodeBlock>> UNMATCHED_CATCH_BLOCK_CONTENT_VAR_KEY = Key.create("UnmatchedCatchBlock");
  public static final Key<List<PsiParameter>> UNMATCHED_CATCH_PARAM_CONTENT_VAR_KEY = Key.create("UnmatchedCatchParam");

  public static void transform(MatchOptions options) {
    if (options.hasVariableConstraints()) return;
    PatternCompiler.transformOldPattern(options);
  }

  public enum TreeContext {
    File, Block, Class
  }
  public static PsiElement[] createTreeFromText(String text, TreeContext context, FileType fileType, Project project) throws IncorrectOperationException {
    PsiElementFactory elementFactory = PsiManager.getInstance(project).getElementFactory();
    if (fileType != StdFileTypes.JAVA) {
      final PsiFile fileFromText = elementFactory.createFileFromText("dummy." + fileType.getDefaultExtension(), "<QQQ>\n" + text + "\n</QQQ>");

      return HtmlUtil.getRealXmlDocument(((XmlFile)fileFromText).getDocument()).getRootTag().getSubTags();
    } else if (fileType == StdFileTypes.JAVA) {
      PsiElement[] result = PsiElement.EMPTY_ARRAY;

      if (context == TreeContext.Block) {
        PsiElement element = elementFactory.createStatementFromText("{\n"+ text + "\n}", null);
        result = ((PsiBlockStatement)element).getCodeBlock().getChildren();
        final int extraChildCount = 4;

        if (result.length > extraChildCount) {
          PsiElement[] newresult = new PsiElement[result.length-extraChildCount];
          final int extraChildStart = 2;
          System.arraycopy(result,extraChildStart,newresult,0,result.length-extraChildCount);
          result = newresult;
        } else {
          result = PsiElement.EMPTY_ARRAY;
        }

      } else if (context == TreeContext.Class) {
        PsiElement element = elementFactory.createStatementFromText("class A {\n"+ text + "\n}", null);
        PsiClass clazz = (PsiClass)((PsiDeclarationStatement)element).getDeclaredElements()[0];
        PsiElement startChild = clazz.getLBrace();
        if (startChild != null) startChild = startChild.getNextSibling();

        PsiElement endChild = clazz.getRBrace();
        if (endChild != null) endChild = endChild.getPrevSibling();

        List<PsiElement> resultElementsList = new ArrayList<PsiElement>(3);
        for(PsiElement el = startChild.getNextSibling(); el != endChild && el != null; el = el.getNextSibling()) {
          resultElementsList.add( el );
        }

        result = resultElementsList.toArray(new PsiElement[resultElementsList.size()]);
      } else {
        result = elementFactory.createFileFromText("__dummy.java",text).getChildren();
      }

      return result;
    }
    return PsiElement.EMPTY_ARRAY;
  }
}

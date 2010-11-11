package com.jetbrains.python;

import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.PyArgumentList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Tests parameter info available via ^P at call sites.
 * <br/>User: dcheryasov
 * Date: Jul 14, 2009 3:42:44 AM
 */
public class PyParameterInfoTest extends LightMarkedTestCase {
  protected Map<String, PsiElement> loadTest() {
    String fname = "/paramInfo/" + getTestName(false) + ".py";
    return configureByFile(fname);
  }

  public void testSimpleFunction() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 3);

    PsiElement arg1 = marks.get("<arg1>");
    feignCtrlP(arg1.getTextOffset()).check("a,b,c", new String[]{"a,"});
    feignCtrlP(arg1.getTextOffset()+1).check("a,b,c", new String[]{"a,"});
    feignCtrlP(arg1.getTextOffset()-3).assertNotFound(); // ^P before arglist gives nothing

    PsiElement arg2 = marks.get("<arg2>");
    feignCtrlP(arg2.getTextOffset()).check("a,b,c", new String[]{"b,"});
    feignCtrlP(arg2.getTextOffset()+1).check("a,b,c", new String[]{"b,"});
    feignCtrlP(arg2.getTextOffset()+2).check("a,b,c", new String[]{"c"}); // one too far after arg2, and we came to arg3

    PsiElement arg3 = marks.get("<arg3>");
    feignCtrlP(arg3.getTextOffset()).check("a,b,c", new String[]{"c"});
    feignCtrlP(arg3.getTextOffset()+1).check("a,b,c", new String[]{"c"});
    feignCtrlP(arg3.getTextOffset()-1).check("a,b,c", new String[]{"c"}); // space before arg goes to that arg
    feignCtrlP(arg3.getTextOffset()+2).assertNotFound(); // ^P on a ")" gives nothing
  }

  public void testStarredFunction() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 4);

    PsiElement arg1 = marks.get("<arg1>");
    feignCtrlP(arg1.getTextOffset()).check("a,b,*c", new String[]{"a,"});
    feignCtrlP(arg1.getTextOffset()+1).check("a,b,*c", new String[]{"a,"});
    
    PsiElement arg2 = marks.get("<arg2>");
    feignCtrlP(arg2.getTextOffset()).check("a,b,*c", new String[]{"b,"});
    feignCtrlP(arg2.getTextOffset()+1).check("a,b,*c", new String[]{"b,"});
    
    PsiElement arg3 = marks.get("<arg3>");
    feignCtrlP(arg3.getTextOffset()).check("a,b,*c", new String[]{"*c"});
    feignCtrlP(arg3.getTextOffset()+1).check("a,b,*c", new String[]{"*c"});

    PsiElement arg4 = marks.get("<arg4>");
    feignCtrlP(arg4.getTextOffset()).check("a,b,*c", new String[]{"*c"});
    feignCtrlP(arg4.getTextOffset()+1).check("a,b,*c", new String[]{"*c"});
    feignCtrlP(arg4.getTextOffset()+2).assertNotFound();
  }

  public void testKwdFunction() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 4);

    PsiElement arg1 = marks.get("<arg1>");
    feignCtrlP(arg1.getTextOffset()).check("a,b,**c", new String[]{"a,"});
    feignCtrlP(arg1.getTextOffset()+1).check("a,b,**c", new String[]{"a,"});

    PsiElement arg2 = marks.get("<arg2>");
    feignCtrlP(arg2.getTextOffset()).check("a,b,**c", new String[]{"b,"});
    feignCtrlP(arg2.getTextOffset()+1).check("a,b,**c", new String[]{"b,"});


    PsiElement arg3 = marks.get("<arg3>");
    feignCtrlP(arg3.getTextOffset()).check("a,b,**c", new String[]{"**c"});
    feignCtrlP(arg3.getTextOffset()+1).check("a,b,**c", new String[]{"**c"});

    PsiElement arg4 = marks.get("<arg4>");
    feignCtrlP(arg4.getTextOffset()).check("a,b,**c", new String[]{"**c"});
    feignCtrlP(arg4.getTextOffset()+1).check("a,b,**c", new String[]{"**c"});
  }

  public void testKwdOutOfOrder() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 4);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,**c", new String[]{"**c"});

    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b,**c", new String[]{"b,"});

    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a,b,**c", new String[]{"a,"});

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,**c", new String[]{"**c"});
  }

  public void testStarArg() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,c", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b,c", new String[]{"b,","c"});
    feignCtrlP(marks.get("<arg2a>").getTextOffset()).check("a,b,c", new String[]{"b,","c"});
  }

  public void testKwdArg() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,c", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b,c", new String[]{"b,","c"});
    feignCtrlP(marks.get("<arg2a>").getTextOffset()).check("a,b,c", new String[]{"b,","c"});
  }

  public void testKwdArgOutOfOrder() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,c", new String[]{"b,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b,c", new String[]{"a,","c"});
    feignCtrlP(marks.get("<arg2a>").getTextOffset()).check("a,b,c", new String[]{"a,","c"});
  }

  public void testStarredAndKwdFunction() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 6);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,*c,**d", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b,*c,**d", new String[]{"b,"});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a,b,*c,**d", new String[]{"*c,"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a,b,*c,**d", new String[]{"*c,"});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("a,b,*c,**d", new String[]{"**d"});
    feignCtrlP(marks.get("<arg6>").getTextOffset()).check("a,b,*c,**d", new String[]{"**d"});
  }

  public void testNestedArg() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 4);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,(b,c),d", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,(b,c),d", new String[]{"b,"});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a,(b,c),d", new String[]{"c"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a,(b,c),d", new String[]{"d"});

    feignCtrlP(marks.get("<arg2>").getTextOffset()-2).check("a,(b,c),d", new String[]{}); // before nested tuple: no arg matches
  }

  public void testDoubleNestedArg() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 5);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,(b,(c,d)),e", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,(b,(c,d)),e", new String[]{"b,"});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a,(b,(c,d)),e", new String[]{"c,"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a,(b,(c,d)),e", new String[]{"d"});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("a,(b,(c,d)),e", new String[]{"e"});
  }

  public void testNestedMultiArg() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,(b,c),d", new String[]{"a,"});
    feignCtrlP(marks.get("<arg23>").getTextOffset()).check("a,(b,c),d", new String[]{"b,","c"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a,(b,c),d", new String[]{"d"});
  }

  public void testStarredParam() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,c", new String[]{"a,"});
    feignCtrlP(marks.get("<arg23>").getTextOffset()).check("a,b,c", new String[]{"b,","c"});
  }

  public void testStarredParamAndArg() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 4);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,*c", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b,*c", new String[]{"b,"});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a,b,*c", new String[]{"*c"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a,b,*c", new String[]{"*c"});
  }


  public void testSimpleMethod() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self,a", new String[]{"a"}, new String[]{"self,"});
  }

  public void testSimpleClassFunction() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self,a", new String[]{"self,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self,a", new String[]{"a"});
  }

  public void testReassignedFunction() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b", new String[]{"b"});
  }

  public void testReassignedInstanceMethod() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self,a,b,c", new String[]{"a,"}, new String[]{"self,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self,a,b,c", new String[]{"b,"}, new String[]{"self,"});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("self,a,b,c", new String[]{"c"}, new String[]{"self,"});
  }

  public void testReassignedClassInit() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self,a,b", new String[]{"a,"}, new String[]{"self,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self,a,b", new String[]{"b"}, new String[]{"self,"});
  }

  public void testInheritedClassInit() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self,a,b", new String[]{"a,"}, new String[]{"self,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self,a,b", new String[]{"b"}, new String[]{"self,"});
  }

  public void testRedefinedNewConstructorCall() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("cls,a,b", new String[]{"a,"}, new String[]{"cls,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("cls,a,b", new String[]{"b"}, new String[]{"cls,"});
  }

  public void testRedefinedNewDirectCall() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("cls,a,b", new String[]{"cls,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("cls,a,b", new String[]{"a,"});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("cls,a,b", new String[]{"b"});
  }

  public void testIgnoreNewInOldStyleClass() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self,one", new String[]{"one"}, new String[]{"self,"});
  }


  public void testBoundMethodSimple() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self,a,b", new String[]{"a,"}, new String[]{"self,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self,a,b", new String[]{"b"}, new String[]{"self,"});
  }

  public void testBoundMethodReassigned() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self,a,b", new String[]{"a,"}, new String[]{"self,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self,a,b", new String[]{"b"}, new String[]{"self,"});
  }

  public void testConstructorFactory() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 1);

    feignCtrlP(marks.get("<arg>").getTextOffset()).check("self,color", new String[]{"color"}, new String[]{"self,"});
  }


  public void testBoundMethodStatic() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b", new String[]{"b"});
  }

  public void testSimpleLambda() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("x", new String[]{"x"});
  }

  public void testReassignedLambda() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("x,y", new String[]{"x,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("x,y", new String[]{"y"});
  }

  public void testLambdaVariousArgs() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 4);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("x,y=1,*args,**kwargs", new String[]{"x,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("x,y=1,*args,**kwargs", new String[]{"y=1,"});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("x,y=1,*args,**kwargs", new String[]{"*args,"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("x,y=1,*args,**kwargs", new String[]{"**kwargs"});
  }

  public void testTupleAndNamedArg1() throws Exception {
    // PY-1268
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg_c>").getTextOffset()).check("a,b,c", new String[]{"c"});
    feignCtrlP(marks.get("<arg_star>").getTextOffset()).check("a,b,c", new String[]{"a,", "b,"});
  }

  public void testTupleAndNamedArg2() throws Exception {
    // PY-1268
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg_star>").getTextOffset()).check("a,b,c", new String[]{"a,", "b,"});
    feignCtrlP(marks.get("<arg_c>").getTextOffset()).check("a,b,c", new String[]{"c"});
  }

  public void testTupleArgPlainParam() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 1);
    feignCtrlP(marks.get("<arg>").getTextOffset()).check("a,b,c", new String[]{"b,"});
  }



  public void testStaticmethod() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b", new String[]{"b"});
  }

  /**
   * Imitates pressing of Ctrl+P; fails if results are not as expected.
   * @param offset offset of 'cursor' where ^P is pressed.
   * @return a {@link Collector} with collected hint info.
   * @throws Exception if it fails
   */
  private Collector feignCtrlP(int offset) throws Exception {
    Collector collector = new Collector(myFixture.getProject(), myFixture.getFile(), offset);
    PyParameterInfoHandler handler = new PyParameterInfoHandler();
    collector.setParameterOwner(handler.findElementForParameterInfo(collector)); // finds arglist, sets items to show
    if (collector.getParameterOwner() != null) {
      assertEquals("Collected one analysis result", 1, collector.myItems.length);
      handler.updateParameterInfo((PyArgumentList)collector.getParameterOwner(), collector); // moves offset to correct parameter
      handler.updateUI((PyArgumentList.AnalysisResult)collector.getItemsToShow()[0], collector); // sets hint text and flags
    }
    return collector;
  }

  /**
   * Imitates the normal UI contexts to the extent we use it. Collects highlighting.
   */
  private static class Collector implements ParameterInfoUIContextEx, CreateParameterInfoContext, UpdateParameterInfoContext {

    private final PsiFile myFile;
    private final int myOffset;
    private int myIndex;
    private Object[] myItems;
    private final Project myProject;
    private final Editor myEditor;
    private PyArgumentList myParamOwner;
    private String[] myTexts;
    private EnumSet<Flag>[] myFlags;

    private Collector(Project project, PsiFile file, int offset) {
      myProject = project;
      myEditor = null;
      myFile = file;
      myOffset = offset;
    }

    @Override
    public void setupUIComponentPresentation(String[] texts, EnumSet<Flag>[] flags, Color background) {
      assert texts.length == flags.length;
      myTexts = texts;
      myFlags = flags;
    }

    @Override
    public void setupUIComponentPresentation(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled,
                                             boolean strikeout, boolean isDisabledBeforeHighlight, Color background) {
      // nothing, we don't use it
    }

    @Override
    public boolean isUIComponentEnabled() {
      return true;
    }

    @Override
    public boolean isUIComponentEnabled(int index) {
      return true;
    }

    @Override
    public void setUIComponentEnabled(boolean enabled) { }

    @Override
    public void setUIComponentEnabled(int index, boolean b) { }

    @Override
    public int getCurrentParameterIndex() {
      return myIndex;
    }

    @Override
    public void removeHint() { }

    @Override
    public void setParameterOwner(PsiElement o) {
      assertTrue("Found element is a python arglist", o == null || o instanceof PyArgumentList);
      myParamOwner = (PyArgumentList)o;
    }

    @Override
    public PsiElement getParameterOwner() {
      return myParamOwner;
    }

    @Override
    public void setHighlightedParameter(Object parameter) {
      // nothing, we don't use it
    }

    @Override
    public void setCurrentParameter(int index) {
      myIndex = index;
    }

    @Override
    public Color getDefaultParameterColor() {
      return java.awt.Color.BLACK;
    }

    @Override
    public Object[] getItemsToShow() {
      return myItems;
    }

    @Override
    public void setItemsToShow(Object[] items) {
      myItems = items;
    }

    @Override
    public void showHint(PsiElement element, int offset, ParameterInfoHandler handler) { }

    @Override
    public int getParameterListStart() {
      return 0; // we don't use it
    }

    @Override
    public Object[] getObjectsToView() {
      return null; // we don't use it
    }

    @Override
    public PsiElement getHighlightedElement() {
      return null;  // we don't use it
    }

    @Override
    public void setHighlightedElement(PsiElement elements) {
      // nothing, we don't use it
    }

    @Override
    public Project getProject() {
      return myProject;
    }

    @Override
    public PsiFile getFile() {
      return myFile;
    }

    @Override
    public int getOffset() {
      return myOffset;
    }

    @Override
    @NotNull
    public Editor getEditor() {
      return myEditor;
    }

    /**
     * Checks if hint data look as expected.
     * @param text expected text of the hint, without formatting
     * @param highlighted expected highlighted substrings of hint
     * @param disabled expected disabled substrings of hint
     */
    public void check(String text, String[] highlighted, String[] disabled) {
      assertEquals("Signature", text, StringUtil.join(myTexts, ""));
      StringBuilder wrongs = new StringBuilder();
      // see if highlighted matches
      Set<String> highlight_set = new HashSet<String>();
      ContainerUtil.addAll(highlight_set, highlighted);
      for (int i = 0; i < myTexts.length; i += 1) {
        if (myFlags[i].contains(Flag.HIGHLIGHT) && !highlight_set.contains(myTexts[i])) {
          wrongs.append("Highlighted unexpected '").append(myTexts[i]).append("'. ");
        }
      }
      for (int i = 0; i < myTexts.length; i += 1) {
        if (!myFlags[i].contains(Flag.HIGHLIGHT) && highlight_set.contains(myTexts[i])) {
          wrongs.append("Not highlighted expected '").append(myTexts[i]).append("'. ");
        }
      }
      // see if disabled matches
      Set<String> disabled_set = new HashSet<String>();
      ContainerUtil.addAll(disabled_set, disabled);
      for (int i = 0; i < myTexts.length; i += 1) {
        if (myFlags[i].contains(Flag.DISABLE) && !disabled_set.contains(myTexts[i])) {
          wrongs.append("Highlighted a disabled '").append(myTexts[i]).append("'. ");
        }
      }
      for (int i = 0; i < myTexts.length; i += 1) {
        if (!myFlags[i].contains(Flag.DISABLE) && disabled_set.contains(myTexts[i])) {
          wrongs.append("Not disabled expected '").append(myTexts[i]).append("'. ");
        }
      }
      //
      if (wrongs.length() > 0) fail(wrongs.toString());
    }

    public void check(String text, String[] highlighted) {
      check(text, highlighted, ArrayUtil.EMPTY_STRING_ARRAY);
    }

    public void assertNotFound() {
      assertNull(myParamOwner);
    }
  }
}

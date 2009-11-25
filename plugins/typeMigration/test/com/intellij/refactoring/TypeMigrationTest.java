package com.intellij.refactoring;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiType;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 22.07.2003
 * Time: 22:46:44
 * To change this template use Options | File Templates.
 */

public class TypeMigrationTest extends TypeMigrationTestBase {

  public String getTestRoot() {
    return "/refactoring/typeMigration/";
  }


  public void testT07() throws Exception {
    doTestFieldType("f", PsiType.INT.createArrayType(), myJavaFacade.getElementFactory().createTypeFromText("java.lang.Integer", null).createArrayType());
  }
  
  public void testT08() throws Exception {
    doTestFieldType("f",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Integer", null).createArrayType(),
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType());
  }



  public void testT09() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.lang.Integer", null).createArrayType(),
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType());
  }

  public void testT10() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.Integer>", null), myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null));
  }

  public void testT11() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.util.Map<java.lang.Integer, java.lang.Integer>", null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Map<java.lang.String, java.lang.Integer>", null));
  }

  public void testT12() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.Integer>", null), myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null));
  }

   public void testT13() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null), myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.Integer>", null));
  }

  public void testT14() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("B", null), myJavaFacade.getElementFactory().createTypeFromText("A", null));
  }

  //do not touch javadoc refs etc
  public void testT15() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("B", null), myJavaFacade.getElementFactory().createTypeFromText("A", null));
  }

  //do not touch signature with method type parameters 
  public void testT16() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("A", null), myJavaFacade.getElementFactory().createTypeFromText("B", null));
  }

  //change method signature inspired by call on parameters
  public void testT17() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("A", null), myJavaFacade.getElementFactory().createTypeFromText("B", null));
  }

  //extending iterable -> used in foreach statement
  public void testT18() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("A", null), myJavaFacade.getElementFactory().createTypeFromText("B", null));
  }

  public void testT19() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.util.Map<java.lang.String, java.lang.String>", null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.HashMap<java.lang.Integer, java.lang.Integer>", null));
  }

  public void testT20() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.util.HashMap<java.lang.Integer, java.lang.Integer>", null), myJavaFacade.getElementFactory().createTypeFromText("java.util.Map<java.lang.String, java.lang.String>", null));
  }

  public void testT21() throws Exception {
    doTestFieldType("f",myJavaFacade.getElementFactory().createTypeFromText("java.util.Map<java.lang.String, java.util.List<java.lang.String>>", null), myJavaFacade.getElementFactory().createTypeFromText("java.util.Map<java.lang.String, java.util.Set<java.lang.String>>", null));
  }

  //varargs : removed after migration?!
  public void testT22() throws Exception {
    doTestFieldType("f",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Integer", null));
  }

  //substitution from super class: type params substitution needed
  public void testT23() throws Exception {
    doTestFieldType("f",
                    myJavaFacade.getElementFactory().createTypeFromText("HashMap<java.lang.String, java.util.Set<java.lang.String>>", null),
                    myJavaFacade.getElementFactory().createTypeFromText("HashMap<java.lang.String, java.util.List<java.lang.String>>", null));
  }

  //check return type unchanged when it is possible
  public void testT24() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("C", null), myJavaFacade.getElementFactory().createTypeFromText("D", null));
  }

  public void testT25() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("C", null), myJavaFacade.getElementFactory().createTypeFromText("D", null));
  }

  //check param type change
  public void testT26() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("C", null), myJavaFacade.getElementFactory().createTypeFromText("D", null));
  }

  public void testT27() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("C", null), myJavaFacade.getElementFactory().createTypeFromText("D", null));
  }

  //list --> array
  public void testT28() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType());
  }

  public void testT29() throws Exception {
    doTestMethodType("get", myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType(),
                     myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null));
  }

  public void testT30() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType());
  }

  
  public void testT31() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("Test", null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Object", null));
  }

  //non code usages
  public void testT32() throws Exception {
    doTestFirstParamType("bar",
                     myJavaFacade.getElementFactory().createTypeFromText("long", null),
                     myJavaFacade.getElementFactory().createTypeFromText("int", null));
  }

  //change type arguments for new expressions: l = new ArrayList<String>() -> l = new ArrayList<Integer>()
  public void testT33() throws Exception {
    doTestFieldType("l", myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.Integer>", null));
  }

  //new expression new ArrayList<String>() should be left without modifications
  public void testT34() throws Exception {
    doTestFieldType("l", myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.AbstractList<java.lang.String>", null));
  }

  public void testT35() throws Exception {
    doTestFieldType("myParent", myJavaFacade.getElementFactory().createTypeFromText("Test", null),
                    myJavaFacade.getElementFactory().createTypeFromText("TestImpl", null));
  }

  //covariant/contravariant positions for primitive types 36-39
  public void testT36() throws Exception {
    doTestFirstParamType("foo", PsiType.INT, PsiType.BYTE);
  }

  public void testT37() throws Exception {
    doTestFirstParamType("foo", PsiType.SHORT, PsiType.INT);
  }

  public void testT38() throws Exception {
    doTestFirstParamType("foo", PsiType.SHORT, PsiType.LONG);
  }

  public void testT39() throws Exception {
    doTestFirstParamType("foo", PsiType.SHORT, PsiType.BYTE);
  }

  //Set s = new HashSet() -> HashSet s = new HashSet();
  public void testT40() throws Exception {
    doTestFieldType("l", myJavaFacade.getElementFactory().createTypeFromText("java.util.List", null), myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList", null));
  }

  //Set s = new HashSet<String>() -> HashSet s = new HashSet<String>();
  public void testT41() throws Exception {
    doTestFieldType("l", myJavaFacade.getElementFactory().createTypeFromText("java.util.List", null), myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList", null));
  }

  //Set s = new HashSet() -> HashSet<String> s = new HashSet();
  public void testT42() throws Exception {
    doTestFieldType("l", myJavaFacade.getElementFactory().createTypeFromText("java.util.List", null), myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<java.lang.String>", null));
  }

  //long l; Object o = l -> long l; Long o = l;
  public void testT43() throws Exception {
    doTestFieldType("o", myJavaFacade.getElementFactory().createTypeFromText("java.lang.Object", null), myJavaFacade.getElementFactory().createTypeFromText("java.lang.Long", null));
  }

  //long l; int  i; l = i; -> long l; byte i; l = i;
  public void testT44() throws Exception {
    doTestFieldType("i", PsiType.INT, PsiType.BYTE);
  }

  //long l; int i; l = i; -> byte l; -> byte i; l = i;
  public void testT45() throws Exception {
    doTestFieldType("l", PsiType.LONG, PsiType.BYTE);
  }

  //byte i; long j = i; -> byte i; int j = i;
  public void testT46() throws Exception {
    doTestFieldType("j", PsiType.LONG, PsiType.INT);
  }

  //o = null -? int o = null
  public void testT47() throws Exception {
    doTestFieldType("o", myJavaFacade.getElementFactory().createTypeFromText("java.lang.Object", null), PsiType.INT);
  }

  //covariant/contravariant assignments: leave types if possible change generics signature only  48-49
  // foo(AbstractSet<String> s){Set<String> ss = s} -> foo(AbstractSet<Integer> s){Set<Integer> ss = s}
  public void testT48() throws Exception {
    doTestFirstParamType("foo", myJavaFacade.getElementFactory().createTypeFromText("java.util.AbstractSet<A>", null),
                                myJavaFacade.getElementFactory().createTypeFromText("java.util.AbstractSet<B>", null));
  }

  // Set<String> f; foo(AbstractSet<String> s){f = s} -> Set<Integer>f; foo(AbstractSet<Integer> s){f = s}
  public void testT49() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<A>", null),
                           myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<B>", null));
  }

  //captured wildcard: Set<? extends JComponent> s; Set<? extends JComponet>  c1 = s; -> Set<? extends JButton> s; Set<? extends JButton> c1 = s;
  public void testT50() throws Exception {
    doTestFieldType("c1", myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? extends JComponent>", null),
                      myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? extends JButton>", null));
  }

  //array initialization: 51-52
  public void testT51() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType(),
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Object", null).createArrayType());
  }

  public void testT52() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.util.Set", null).createArrayType(),
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Object", null).createArrayType());
  }

  //generic type promotion to array initializer
  public void testT53() throws Exception {
    doTestFieldType("f", PsiType.DOUBLE.createArrayType(),
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<java.lang.String>", null).createArrayType());
  }

  //wildcard type promotion to expressions 54-55
  public void testT54() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<java.lang.Object>", null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? extends java.lang.Integer>", null));
  }

  public void testT55() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<java.lang.Object>", null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<?>", null));
  }

  //array index should be integer 56-57
  public void testT56() throws Exception {
    doTestFirstParamType("foo", PsiType.INT, PsiType.DOUBLE);
  }

  public void testT57() throws Exception {
    doTestFirstParamType("foo", PsiType.INT, PsiType.BYTE);
  }

  //Arrays can be assignable to Object/Serializable/Cloneable 58-59; ~ 60 varargs
  public void testT58() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType(),
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Object", null));
  }

  public void testT59() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType(),
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Cloneable", null));
  }

  public void testT60() throws Exception {
    doTestFieldType("p", PsiType.INT.createArrayType(),
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Object", null));
  }

  //change parameter type -> vararg; assignment changed to array
  public void testT61() throws Exception {
    doTestFirstParamType("foo", PsiType.INT, new PsiEllipsisType(PsiType.INT));
  }

  //change field type -> change vararg parameter type due to assignment: 62-63
  public void testT62() throws Exception {
    doTestFieldType("p", PsiType.INT.createArrayType(), myJavaFacade.getElementFactory().createTypeFromText("java.lang.Object", null));
  }

  public void testT63() throws Exception {
    doTestFieldType("p", PsiType.INT.createArrayType(), PsiType.DOUBLE.createArrayType());
  }

  //remove vararg type: 64-66 
  public void testT64() throws Exception {
    doTestFirstParamType("foo", new PsiEllipsisType(PsiType.INT), PsiType.INT);
  }

  public void testT65() throws Exception {
    doTestFirstParamType("foo", new PsiEllipsisType(PsiType.INT), myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testT66() throws Exception {
    doTestFirstParamType("foo", new PsiEllipsisType(PsiType.INT), PsiType.INT);
  }

  public void testT67() throws Exception {
    doTestFirstParamType("methMemAcc", myJavaFacade.getElementFactory().createTypeFromText("java.lang.Object", null), myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testT68() throws Exception {
    doTestFirstParamType("foo", PsiType.INT, PsiType.DOUBLE);
  }

  public void testT69() throws Exception {
    doTestFirstParamType("foo", PsiType.INT, PsiType.BYTE);
  }

  public void testT70() throws Exception {
    doTestFieldType("a", PsiType.INT.createArrayType().createArrayType(), PsiType.FLOAT.createArrayType().createArrayType());
  }

  public void testT71() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_LANG_CLASS, null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class<? extends java.lang.Number>", null));
  }

  public void testT72() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_LANG_CLASS, null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class<java.lang.Integer>", null));
  }

  public void testT73() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<javax.swing.JComponent>", null).createArrayType().createArrayType(),
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<java.awt.Component>", null).createArrayType().createArrayType());
  }

  //prefix/postfix expression; binary expressions 74-76
  public void testT74() throws Exception {
    doTestFirstParamType("meth", PsiType.INT, PsiType.FLOAT);
  }


  public void testT75() throws Exception {
    doTestFirstParamType("meth", PsiType.INT, myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testT76() throws Exception {
    doTestFirstParamType("meth", PsiType.BYTE, PsiType.FLOAT);
  }

  //+= , etc 77-78
  public void testT77() throws Exception {
    doTestFirstParamType("meth", PsiType.INT, myJavaFacade.getElementFactory().createTypeFromText("java.lang.Object", null));
  }

  public void testT78() throws Exception {
    doTestFirstParamType("meth", PsiType.INT, myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  //casts 79-80,83
  public void testT79() throws Exception {
    doTestFirstParamType("meth", PsiType.INT, PsiType.BYTE);
  }

  public void testT80() throws Exception {
    doTestFirstParamType("meth", PsiType.INT, PsiType.DOUBLE);
  }

  public void testT83() throws Exception {
    doTestFirstParamType("meth", PsiType.INT, myJavaFacade.getElementFactory().createTypeFromText("java.lang.Object", null));
  }

  //instanceofs 81-82
  public void testT81() throws Exception {
    doTestFirstParamType("foo", myJavaFacade.getElementFactory().createTypeFromText("java.lang.Object", null),
                         myJavaFacade.getElementFactory().createTypeFromText("A", null));
  }

  public void testT82() throws Exception {
    doTestFirstParamType("foo", myJavaFacade.getElementFactory().createTypeFromText("java.lang.Object", null),
                         myJavaFacade.getElementFactory().createTypeFromText("C", null));
  }


  public void testT84() throws Exception {
    doTestFirstParamType("meth", myJavaFacade.getElementFactory().createTypeFromText("java.util.Set", null),
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? extends java.util.Set>", null));
  }

  public void testT85() throws Exception {
    doTestFieldType("str", myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Integer", null));
  }

  //array <-> list 86-89;94;95
  public void testT86() throws Exception {
    doTestMethodType("getArray", myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType(),
                     myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null));
  }

  
   public void testT87() throws Exception {
    doTestMethodType("getArray",
                     myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null),
                     myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType());
  }

  public void testT88() throws Exception {
    doTestMethodType("getArray",
                     myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType(),
                     myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null));
  }

  public void testT89() throws Exception {
    doTestMethodType("getArray",
                     myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null),
                     myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType());
  }

  public void testT94() throws Exception {
    doTestMethodType("getArray",
                     myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null),
                     myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType());
  }

  public void testT95() throws Exception {
    doTestMethodType("getArray",
                     myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType(),
                     myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null));
  }


  public void testT90() throws Exception {
    doTestFieldType("l", myJavaFacade.getElementFactory().createTypeFromText("java.util.List<B>", null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.List<A>", null));
  }

  //element type -> element type array
  public void testT91() throws Exception {
    doTestMethodType("foo",
                     myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null),
                     myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType());
  }

  //List<S>=new ArrayList<S>{}; -> List<I>=new ArrayList<I>{}; anonymous 
  public void testT92() throws Exception {
    doTestFieldType("f", myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.Integer>", null));
  }

  //generics signature do not support primitives: Map<Boolean, String> - Map<boolean, String>
  public void testT93() throws Exception {
    doTestFirstParamType("foo", myJavaFacade.getElementFactory().createTypeFromText("java.lang.Boolean", null), PsiType.BOOLEAN);
  }

  //field initializers procession
  public void testT96() throws Exception {
    doTestFieldType("f1", myJavaFacade.getElementFactory().createTypeFromText("java.lang.Integer", null), myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testT97() throws Exception {
    doTestFieldType("f1", myJavaFacade.getElementFactory().createTypeFromText("java.lang.Integer", null).createArrayType(), PsiType.INT);
  }

  //list <-> array conversion in assighnemt statements
  public void testT98() throws Exception {
    doTestMethodType("getArray", myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType(),
                     myJavaFacade.getElementFactory().createTypeFromText("java.util.List<java.lang.String>", null));
  }

  //escape pattern from []
  public void testT99() throws Exception {
    doTestFieldType("f",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<java.util.List<char[]>>", null),
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<java.util.List<int[]>>", null));
  }
}

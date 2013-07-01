package com.intellij.navigation
import com.intellij.ide.util.gotoByName.ChooseByNameBase
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.GotoClassModel2
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.Consumer
import com.intellij.util.concurrency.Semaphore
/**
 * @author peter
 */
class ChooseByNameTest extends LightCodeInsightFixtureTestCase {

  public void "test goto class order by matching degree"() {
    def startMatch = myFixture.addClass("class UiUtil {}")
    def wordSkipMatch = myFixture.addClass("class UiAbstractUtil {}")
    def camelMatch = myFixture.addClass("class UberInstructionUxTopicInterface {}")
    def middleMatch = myFixture.addClass("class BaseUiUtil {}")
    def elements = getPopupElements(new GotoClassModel2(project), "uiuti")
    assert elements == [startMatch, wordSkipMatch, camelMatch, ChooseByNameBase.NON_PREFIX_SEPARATOR, middleMatch]
  }

  public void "test annotation syntax"() {
    def match = myFixture.addClass("@interface Anno1 {}")
    myFixture.addClass("class Anno2 {}")
    def elements = getPopupElements(new GotoClassModel2(project), "@Anno")
    assert elements == [match]
  }

  public void "test no result for empty patterns"() {
    myFixture.addClass("@interface Anno1 {}")
    myFixture.addClass("class Anno2 {}")

    def popup = createPopup(new GotoClassModel2(project))
    assert getPopupElements(popup, "") == []
    popup.close(false)

    assert getPopupElements(new GotoClassModel2(project), "@") == []
  }

  public void "test filter overridden methods from goto symbol"() {
    def intf = myFixture.addClass("""
class Intf {
  void xxx1() {}
  void xxx2() {}
}""")
    def impl = myFixture.addClass("""
class Impl extends Intf {
    void xxx1() {}
    void xxx3() {}
}
""")

    def elements = getPopupElements(new GotoSymbolModel2(project), "xxx")

    assert intf.findMethodsByName('xxx1', false)[0] in elements
    assert intf.findMethodsByName('xxx2', false)[0] in elements

    assert impl.findMethodsByName('xxx3', false)[0] in elements
    assert !(impl.findMethodsByName('xxx1', false)[0] in elements)
  }

  public void "test disprefer underscore"() {
    def intf = myFixture.addClass("""
class Intf {
  void _xxx1() {}
  void xxx2() {}
}""")

    def elements = getPopupElements(new GotoSymbolModel2(project), "xxx")
    assert elements == [intf.findMethodsByName('xxx2', false), ChooseByNameBase.NON_PREFIX_SEPARATOR, intf.findMethodsByName('_xxx1', false)]
  }

  public void "test prefer exact extension matches"() {
    def m = myFixture.addFileToProject("relaunch.m", "")
    def mod = myFixture.addFileToProject("reference.mod", "")
    def elements = getPopupElements(new GotoFileModel(project), "re*.m")
    assert elements == [m, mod]
  }

  public void "test prefer better path matches"() {
    def fooIndex = myFixture.addFileToProject("foo/index.html", "foo")
    def fooBarIndex = myFixture.addFileToProject("foo/bar/index.html", "foo bar")
    def barFooIndex = myFixture.addFileToProject("bar/foo/index.html", "bar foo")
    def elements = getPopupElements(new GotoFileModel(project), "foo/index")
    assert elements == [fooIndex, barFooIndex, fooBarIndex]
  }

  private List<Object> getPopupElements(ChooseByNameModel model, String text) {
    return getPopupElements(createPopup(model), text)
  }

  private static ArrayList<String> getPopupElements(ChooseByNamePopup popup, String text) {
    List<Object> elements = ['empty']
    def semaphore = new Semaphore()
    semaphore.down()
    popup.scheduleCalcElements(text, false, false, ModalityState.NON_MODAL, { set ->
      elements = set as List
      semaphore.up()
    } as Consumer<Set<?>>)
    assert semaphore.waitFor(1000)
    return elements
  }

  private ChooseByNamePopup createPopup(ChooseByNameModel model) {
    def popup = ChooseByNamePopup.createPopup(project, model, (PsiElement)null, "")
    Disposer.register(testRootDisposable, { popup.close(false) } as Disposable)
    popup
  }

  @Override
  protected boolean runInDispatchThread() {
    return false
  }

  @Override
  protected void invokeTestRunnable(Runnable runnable) throws Exception {
    runnable.run()
  }
}

// Items: arg, cast, for, instanceof, not, par, var
public class Foo {
    void m() {
        int foo = 2;
        int i = foo<caret> + 2 /**/ ;
    }
}
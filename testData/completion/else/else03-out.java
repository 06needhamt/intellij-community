// Items: arg, assert, else, if, not, var, while
public class Foo {
    void m(boolean x, boolean y, boolean z) {
        if (!x || !y || !z) {
            <caret>
        }
        value = dummyAssignment;
    }
}
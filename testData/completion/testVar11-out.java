// Items: m, f, arg, cast, for, not, var
public class Foo {
    Foo f;
    Foo m() {
        Foo foo = m().m().f;<caret>
        m();
        return null;
    }
}
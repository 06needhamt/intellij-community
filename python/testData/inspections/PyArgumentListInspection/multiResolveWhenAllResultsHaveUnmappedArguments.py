class C1:
    def foo(self, x):
        return self


class C2:
    def foo(self, x, y):
        return self


def f():
    """
    :rtype: C1 | C2
    """
    pass


f().foo<warning descr="Unexpected argument(s)Possible callees:C1.foo(self, x)C2.foo(self, x, y)">(1, 2, 3)</warning>

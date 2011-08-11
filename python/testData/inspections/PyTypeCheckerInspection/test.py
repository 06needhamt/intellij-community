def f1(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10=10, p11='11'):
    """
    :type p1: integer
    :type p2: integer
    :type p3: float
    :type p4: float
    :type p5: int
    :type p6: integer
    :type p7: integer
    :type p8: int
    :type p9: int
    :type p10: int
    :type p11: string
    """
    return p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + p10 + int(p11)


def test_1():
    p7 = int('7')
    f1(1,
       <warning descr="Expected type 'int', got 'str' instead">'2'</warning>,
       3.0, 4, 5, int('6'), p7, p8=-8,
       <warning descr="Expected type 'int', got 'str' instead">p9='foo'</warning>,
       <warning descr="Expected type 'int', got 'str' instead">p10='foo'</warning>)


def str_to_none(b):
    """
    :type b: str
    """
    pass


def unicode_to_none(s):
    """
    :type s: unicode
    """
    pass


def string_to_none(s):
    """
    :type s: string
    """
    pass


def str_or_unicode_to_none(s):
    """
    :type s: str or unicode
    """
    pass


def test_str_unicode():
    b1 = 'hello'
    s1 = u'привет'
    b2 = str(-1)
    s2 = unicode(3.14)
    ENC = 'utf-8'
    str_to_none(<warning descr="Expected type 'str', got 'unicode' instead">b1.decode(ENC)</warning>)
    unicode_to_none(b1.decode(ENC))
    string_to_none(b1.decode(ENC))
    str_or_unicode_to_none(b1.decode(ENC))
    b1.encode(ENC)
    s1.decode(ENC)
    str_to_none(s1.encode(ENC))
    unicode_to_none(<warning descr="Expected type 'unicode', got 'str' instead">s1.encode(ENC)</warning>)
    string_to_none(s1.encode(ENC))
    str_or_unicode_to_none(s1.encode(ENC))
    b2.decode(ENC)
    b2.encode(ENC)
    s2.decode(ENC)
    s2.encode(ENC)


def f_list_tuple(spam, eggs):
    """
    :type spam: list of string
    :type eggs: (bool, int, unicode)
    """
    return spam, eggs


def test_list_tuple():
    f_list_tuple(<warning descr="Expected type 'list of one of (str, unicode)', got 'list of int' instead">[1, 2, 3]</warning>,
                 (<warning descr="Expected type 'tuple(bool,int,unicode)', got 'tuple(bool,int,str)' instead">False, 2, ''</warning>))


def test_builtin_numerics():
    abs(False)
    int(10)
    long(False)
    float(False)
    complex(False)
    divmod(False, False)
    divmod(<warning descr="Expected type 'one of (int, long, float, complex)', got 'str' instead">'foo'</warning>,
           <warning descr="Expected type 'one of (int, long, float, complex)', got 'unicode' instead">u'bar'</warning>)
    pow(False, True)
    round(False,
          <warning descr="Expected type 'one of (int, long, float, None)', got 'str' instead">'foo'</warning>)


def test_generator():
    def gen(n):
        for x in xrange(n):
            yield str(x)
    def f_1(xs):
        """
        :type xs: list of int
        """
        return xs
    def f_2(xs):
        """
        :type xs: Sequence of int
        """
        return xs
    def f_3(xs):
        """
        :type xs: Container of int
        """
        return xs
    def f_4(xs):
        """
        :type xs: Iterator of int
        """
        return xs
    def f_5(xs):
        """
        :type xs: Iterable of int
        """
        return xs
    def f_6(xs):
        """
        :type xs: list
        """
        return xs
    def f_7(xs):
        """
        :type xs: Sequence
        """
        return xs
    def f_8(xs):
        """
        :type xs: Container
        """
        return xs
    def f_9(xs):
        """
        :type xs: Iterator
        """
        return xs
    def f_10(xs):
        """
        :type xs: Iterable
        """
        return xs
    def f_11(xs):
        """
        :type xs: list of string
        """
        return xs
    def f_12(xs):
        """
        :type xs: Sequence of string
        """
        return xs
    def f_13(xs):
        """
        :type xs: Container of string
        """
        return xs
    def f_14(xs):
        """
        :type xs: Iterator of string
        """
        return xs
    def f_15(xs):
        """
        :type xs: Iterable of string
        """
        return xs
    return [
        ''.join(gen(10)),
        f_1(<warning descr="Expected type 'list of int', got 'Iterator of str' instead">gen(11)</warning>),
        f_2(<warning descr="Expected type 'Sequence of int', got 'Iterator of str' instead">gen(11)</warning>),
        f_3(<warning descr="Expected type 'Container of int', got 'Iterator of str' instead">gen(11)</warning>),
        f_4(<warning descr="Expected type 'Iterator of int', got 'Iterator of str' instead">gen(11)</warning>),
        f_5(<warning descr="Expected type 'Iterable of int', got 'Iterator of str' instead">gen(11)</warning>),
        f_6(<warning descr="Expected type 'list', got 'Iterator of str' instead">gen(11)</warning>),
        f_7(<warning descr="Expected type 'Sequence', got 'Iterator of str' instead">gen(11)</warning>),
        f_8(<warning descr="Expected type 'Container', got 'Iterator of str' instead">gen(11)</warning>),
        f_9(gen(11)),
        f_10(gen(11)),
        f_11(<warning descr="Expected type 'list of one of (str, unicode)', got 'Iterator of str' instead">gen(11)</warning>),
        f_12(<warning descr="Expected type 'Sequence of one of (str, unicode)', got 'Iterator of str' instead">gen(11)</warning>),
        f_13(<warning descr="Expected type 'Container of one of (str, unicode)', got 'Iterator of str' instead">gen(11)</warning>),
        f_14(gen(11)),
        f_15(gen(11)),
        f_15('foo'.split('o')),
    ]

# PY-4025
def test_function_assignments():
    def g(x):
        """
        :type x: int
        """
        return x
    g(<warning descr="Expected type 'int', got 'str' instead">"str"</warning>) #fail
    h = g
    h(<warning descr="Expected type 'int', got 'str' instead">"str"</warning>) #fail


def test_old_style_classes():
    class C:
        pass
    def f(x):
        """
        :type x: object
        """
        pass
    f(C()) #pass


def test_partly_unknown_type():
    def f():
        """
        :rtype: None or unknown or int or long
        """
    def g(x):
        """
        :type x: object
        """
    g(f())


def test_type_assertions():
    def f_1():
        """
        :rtype: int or str or None
        """
    def f_2():
        """
        :rtype: int or None
        """
    def f_3():
        """
        :rtype: unknown
        """
    def f_4():
        """
        :rtype: object
        """
    def f_5():
        """
        :rtype: int or object
        """
    def f_6():
        """
        :rtype: int or unknown or float
        """
    def f_7():
        """
        :rtype: int or unknown
        """
    def print_int(x):
        """
        :type x: int
        """
        print(x)
    def print_int_or_str(x):
        """
        :type x: int or str
        """
    x_1 = f_1()
    print_int(<warning descr="Expected type 'int', got 'one of (int, str, None)' instead">x_1</warning>)
    print_int_or_str(<warning descr="Expected type 'one of (int, str)', got 'one of (int, str, None)' instead">x_1</warning>)
    if isinstance(x_1, int):
        print_int(x_1)
    if isinstance(x_1, str):
        print_int_or_str(x_1)
    x_7 = f_7()
    print_int(x_7)

def test_local_type_resolve():
    class C():
        def f(self):
            return 2
    c = C()
    x = c.f()
    y = x
    return y + <warning descr="Expected type 'one of (int, long, float, complex)', got 'str' instead">'foo'</warning>
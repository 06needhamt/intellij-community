// PARAM_TYPES: T
trait T

class A(a: Int, b: Int): T

class B(t: T): T by <selection>t</selection>
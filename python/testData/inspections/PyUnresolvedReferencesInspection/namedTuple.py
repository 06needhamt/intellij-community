from collections import namedtuple

Point = namedtuple('Point', ['x', 'y'], verbose=True)

print Point.x, Point.y

p = Point(11, y=22)
print p.x + p.y
print p.__add__
print p._asdict()
print Point._fields
print p._replace

if isinstance(p, Point):
    p.x

class C(namedtuple('C', 'x')):
    def f(self):
        return self

c = C()
print(c.x, c.f())

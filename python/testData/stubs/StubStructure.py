def deco(fun):
  return fun # valid

class FooClass:
  staticField = deco
  def __init__(self):
    self.instanceField = 2

  @deco
  def fooFunction(fooParam1, fooParam2=0) :
    pass

def topLevelFunction(tlfp1, tlfp2) :
  pass
  
top1 = 1
if True:
  top2 = 2

# encoding: utf-8
"""
This thing tries to restore public interface of objects that don't have a python
source: C extensions and built-in objects. It does not reimplement the
'inspect' module, but complements it.

Since built-ins don't have many features that full-blown objects have, 
we do not support some fancier things like metaclasses.

We use certain kind of doc comments ("f(int) -> list") as a hint for functions'
input and output, especially in builtin functions.

This code has to work with CPython versions from 2.2 to 3.0, and hopefully with
compatible versions of Jython and IronPython.
"""

import sys
import os
import string
import stat
import types
#import __builtin__

try:
  import inspect
except ImportError:
  inspect = None # it may fail

import re

string_mod = string

version = (
  (sys.hexversion & (0xff << 24)) >> 24,
  (sys.hexversion & (0xff << 16)) >> 16
)

if version[0] == 1:
  printable_chars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!"#$%&\'()*+,-./:;<=>?@[\\]^_`{|}~  \t\n\r\x0b\x0c'
else:
  printable_chars = string_mod.printable

DIGITS = string_mod.digits

if version[0] >= 3:
  import builtins as the_builtins
  string = "".__class__
  LETTERS = string_mod.ascii_letters
  STR_TYPES = (getattr(the_builtins, "bytes"), str)
  
  NUM_TYPES = (int, float)
  SIMPLEST_TYPES = NUM_TYPES + STR_TYPES + (None.__class__,)
  EASY_TYPES = NUM_TYPES + STR_TYPES + (None.__class__, dict, tuple, list)

  def str_join(a_list, a_str):
    return string.join(a_str, a_list)
    
  def the_exec(source, context):
    exec(source, context)
    
else: # < 3.0
  import __builtin__ as the_builtins
  LETTERS = string_mod.letters
  STR_TYPES = (getattr(the_builtins, "unicode"), str)
  
  NUM_TYPES = (int, long, float)
  SIMPLEST_TYPES = NUM_TYPES + STR_TYPES + (types.NoneType,)
  EASY_TYPES = NUM_TYPES + STR_TYPES + (types.NoneType, dict, tuple, list)

  def str_join(a_list, a_str):
    return string_mod.join(a_list, a_str)

  def the_exec(source, context):
    exec (source) in context
    

#
IDENT_PATTERN = "[A-Za-z_][0-9A-Za-z_]*" # re pattern for identifier
STR_CHAR_PATTERN = "[0-9A-Za-z_.,\+\-&\*% ]" 

DOC_FUNC_RE = re.compile("(?:.*\.)?(\w+)\(([^\)]*)\).*") # $1 = function name, $2 = arglist

SANE_REPR_RE = re.compile(IDENT_PATTERN + "(?:\(.*\))?") # identifier with possible (...), go catches

IDENT_RE = re.compile("(" + IDENT_PATTERN + ")") # $1 = identifier

STARS_IDENT_RE = re.compile("(\*?\*?" + IDENT_PATTERN + ")") # $1 = identifier, maybe with a * or **

IDENT_EQ_RE = re.compile("(" + IDENT_PATTERN + "\s*=)") # $1 = identifier with a following '='

VAL_RE  = re.compile(
  "(-?[0-9]+)|"+
  "('" + STR_CHAR_PATTERN + "*')|"+
  '("' + STR_CHAR_PATTERN + '*")|'+
  "(\[\])|"+
  "(\{\})|"+
  "(\(\))|" +
  "(None)"
) # $? = sane default value

def _searchbases(cls, accum):
  # logic copied from inspect.py
  if cls not in accum:
    accum.append(cls)
    for x in cls.__bases__:
      _searchbases(x, accum)

def getMRO(a_class):
  # logic copied from inspect.py
  "Returns a tuple of MRO classes."
  if hasattr(a_class, "__mro__"):
    return a_class.__mro__
  elif hasattr(a_class, "__bases__"):
    bases = []
    _searchbases(a_class, bases)
    return tuple(bases)
  else:
    return tuple()


def getBases(a_class): # TODO: test for classes that don't fit this scheme
  "Returns a sequence of class's bases."
  if hasattr(a_class, "__bases__"):
    return a_class.__bases__
  else:
    return ()


def isCallable(x):
  return hasattr(x, '__call__')


def sortedNoCase(p_array):
  "Sort an array case insensitevely, returns a sorted copy"
  p_array = list(p_array)    
  if version[0] < 3:
    def c(x, y):
      x = x.upper()
      y = y.upper()
      if x > y:
        return 1
      elif x < y:
        return -1
      else:
        return 0
    p_array.sort(c)
  else:
    p_array.sort(key=lambda x: x.upper())
    
  return p_array

_prop_types = [type(property())]
try: _prop_types.append(types.GetSetDescriptorType)
except: pass

try: _prop_types.append(types.MemberDescriptorType)
except: pass

_prop_types = tuple(_prop_types)

def isProperty(x):
  return isinstance(x, _prop_types)



FAKE_CLASSOBJ_NAME = "___Classobj"
BUILTIN_MOD_NAME = the_builtins.__name__

def isSaneRValue(x):
  return isinstance(x, EASY_TYPES)
  
def sanitizeIdent(x):
  "Takes an identifier and returns it sanitized"
  if x in ("class", "object", "def", "self", "None"):
    return "p_" + x
  else:
    return x

doing_builtins = False

class FakeClassObj:
  "A mock class representing the old style class base."
  __module__ = None
  __class__ = None
  def __init__(self):
    pass


class ModuleRedeclarator(object):
  
  def __init__(self, module, outfile, indent_size=4):
    """
    Create new instance.
    @param module module to restore.
    @param outfile output file, must be open and writable.
    @param indent_size amount of space characters per indent
    """
    self.module = module
    self.outfile = outfile
    self.indent_size = indent_size
    self._indent_step = " " * indent_size
    self.imported_modules = {"": the_builtins}
    self._defined = {} # contains True for every name defined so far
    
    
  def indent(self, level):
    "Return indentation whitespace for given level."
    return self._indent_step * level
    
    
  def out(self, what, indent=0):
    "Output the argument, indenting as nedded, and adding a eol"
    self.outfile.write(self.indent(indent));
    self.outfile.write(what);
    self.outfile.write("\n");
    
    
  def outDocstring(self, docstring, indent):
    if docstring is not None and isinstance(docstring, str):
      self.out('"""', indent)
      for line in docstring.split("\n"):
        self.out(line, indent)
      self.out('"""', indent)

  def outDocAttr(self, p_object, indent, p_class=None):
    if hasattr(p_object, "__doc__"):
      the_doc = p_object.__doc__
      if p_class and the_doc == object.__init__.__doc__ and p_object is not object.__init__ and p_class.__doc__:
        the_doc = p_class.__doc__ # replace stock init's doc with class's
        the_doc += "\n# (copied from class doc)"
      self.outDocstring(the_doc, indent)
    else:
      self.out("# no doc", indent)
    
  # Some values are known to be of no use in source and needs to be suppressed.
  # Dict is keyed by module names, with "*" meaning "any module";
  # values are lists of names of members whose value must be pruned.
  SKIP_VALUE_IN_MODULE = {
    "sys": (
      "modules", "path_importer_cache", "argv", "builtins",
      "last_traceback", "last_type", "last_value",
    ),
    "*":   ("__builtins__",)
  }

  # Some values are special and are better represented by hand-crafted constructs.
  # Dict is keyed by (module name, member name) and value is the replacement.
  REPLACE_MODULE_VALUES = {
    (BUILTIN_MOD_NAME, "None"): "object()"
  }
  if version[0] <= 2:
    for std_file in ("stdin", "stdout", "stderr"):
      REPLACE_MODULE_VALUES[("sys", std_file)] = "file('')" #

  # Some functions and methods of some builtin classes have special signatures.
  # {("class", "method"): ("signature_string")}
  PREDEFINED_BUILTIN_SIGS = {
    ("object", "__init__"): "(self)",
    ("str", "__init__"): "(self, x)", # overrides a fake
    ("type", "__init__"): "(self, name, bases=None, dict=None)", # overrides a fake
    ("int", "__init__"): "(self, x, base=10)", # overrides a fake
    ("list", "__init__"): "(self, seq=())",
    ("tuple", "__init__"): "(self, seq=())", # overrides a fake
    ("set", "__init__"): "(self, seq=())",
    ("dict", "__init__"): "(self, seq=None, **kwargs)",
    (None, "min"): "(*args)",
    (None, "max"): "(*args)",
  }

  if version[0] < 3:
    PREDEFINED_BUILTIN_SIGS[("unicode", "__init__")] = "(self, x, encoding=None, errors='strict')" # overrides a fake
    PREDEFINED_BUILTIN_SIGS[("super", "__init__")] = "(self, type1, type2=None)"
  else:
    PREDEFINED_BUILTIN_SIGS[("super", "__init__")] = "(self, type1=None, type2=None)"


  # Some builtin classes effectively change __init__ signature without overriding it.
  # This callable serves as a placeholder to be replaced via REDEFINED_BUILTIN_SIGS
  def fake_builtin_init(self): pass # just a callable, sig doesn't matter
  fake_builtin_init.__doc__ = object.__init__.__doc__ # this forces class's doc to be used instead

  # This is a list of builtin classes to use fake init
  FAKE_BUILTIN_INITS = (tuple, type, int, str)
  if version[0] < 3:
    import __builtin__ as b2
    FAKE_BUILTIN_INITS = FAKE_BUILTIN_INITS + (getattr(b2, "unicode"),)
    del b2

  # Some builtin methods are decorated, but this is hard to detect.
  # {("class_name", "method_name"): "@decorator"}
  KNOWN_DECORATORS = {
    ("dict", "fromkeys"): "@static",
  }

  def isSkippedInModule(self, p_module, p_value):
    "Returns True if p_value's value must be skipped for module p_module."
    skip_list = self.SKIP_VALUE_IN_MODULE.get(p_module, [])
    if p_value in skip_list:
      return True
    skip_list = self.SKIP_VALUE_IN_MODULE.get("*", [])
    if p_value in skip_list:
      return True
    return False
    
    
  def findImportedName(self, item):
    """
    Finds out how the item is represented in imported modules.
    @param item what to check
    @return qualified name (like "sys.stdin") or None
    """
    if not isinstance(item, SIMPLEST_TYPES):
      for mname in self.imported_modules:
        m = self.imported_modules[mname]
        for inner_name in m.__dict__:
          suspect = getattr(m, inner_name)
          if suspect is item:
            if mname:
              mname += "."
            elif self.module is the_builtins: # don't short-circuit builtins 
              return None
            return mname + inner_name
    return None


  def fmtValue(self, p_value, indent, prefix="", postfix="", as_name=None):
    """
    Formats and outputs value (it occupies and entire line).
    @param p_value the value.
    @param indent indent level.
    @param prefix text to print before the value
    @param postfix text to print after the value
    @param as_name hints which name are we trying to print; helps with circular refs. 
    """
    if isinstance(p_value, SIMPLEST_TYPES):
      self.out(prefix + repr(p_value) + postfix, indent)
    else:
      imported_name = self.findImportedName(p_value)
      if imported_name:
        self.out(prefix + imported_name + postfix, indent)
      else:
        if isinstance(p_value, (list, tuple)):
          if len(p_value) == 0:
            self.out(prefix + repr(p_value) + postfix, indent)
          else:
            if isinstance(p_value, list):
              lpar, rpar = "[", "]"
            else:
              lpar, rpar = "(", ")"
            self.out(prefix + lpar, indent)
            for v in p_value:
              self.fmtValue(v, indent+1, postfix=",")
            self.out(rpar  + postfix, indent)
        elif isinstance(p_value, dict):
          if len(p_value) == 0:
            self.out(prefix + repr(p_value) + postfix, indent)
          else:
            self.out(prefix + "{", indent)
            for k in p_value:
              v = p_value[k]
              if isinstance(k, SIMPLEST_TYPES):
                self.fmtValue(v, indent+1, prefix=repr(k)+": ", postfix=",")
              else:
                # both key and value need fancy formatting
                self.fmtValue(k, indent+1, postfix=": ")
                self.fmtValue(v, indent+2)
                self.out(",", indent+1)
            self.out("}" + postfix, indent)
        else: # something else, maybe representable
          # look up this value in the module.
          found_name = ""
          for inner_name in self.module.__dict__:
            if self.module.__dict__[inner_name] is p_value:
              found_name = inner_name
              break
          if self._defined.get(found_name, False):
            self.out(prefix + found_name + postfix, indent)
          else:
            # a forward / circular declaration happens
            notice = ""
            s = repr(p_value)
            if found_name:
              if found_name == as_name:
                notice = " # (!) real value is " + s
                s = "None"
              else:
                notice = " # (!) forward: " + found_name + ", real value is " + s
            if SANE_REPR_RE.match(s):
              self.out(prefix + s + postfix + notice, indent)
            else:
              if not found_name:
                notice = " # (!) real value is " + s
              self.out(prefix + "None" + postfix + notice, indent)        
        
        

  def redoFunction(self, p_func, p_name, indent, p_class=None, p_modname=None):
    """
    Restore function argument list as best we can.
    @param p_func function or method object
    @param p_name function name as known to owner
    @param indent indentation level
    @param p_class the class that contains this function as a method 
    """

    def seemsToHaveSelf(reqargs):
      """"
      @param requargs a list of required arguments of a method
      @return true if param_name looks like a 'self' parameter
      """
      if not reqargs:
        return False
      # handle special cases of builtins, like object.__new__
      if doing_builtins and reqargs == ["S", "*more"]:
        return True
      else:
        return reqargs[0] == "self" 

    # real work
    classname = p_class and p_class.__name__ or None
    # any decorators?
    if doing_builtins and p_modname == BUILTIN_MOD_NAME:
      deco = self.KNOWN_DECORATORS.get((classname, p_name), None)
      if deco:
        self.out(deco + " # known case", indent);
    if p_name == "__new__":
      self.out("@staticmethod # known case of __new__", indent);
    if inspect and inspect.isfunction(p_func):
      args, varg, kwarg, defaults = inspect.getargspec(p_func)
      spec = []
      dcnt = defaults and len(defaults)-1 or -1
      args = args or []
      args.reverse() # backwards, for easier defaults handling
      for arg in args: 
        if dcnt >= 0:
          arg += " = " + repr(defaults[dcnt])
          dcnt -= 1
        spec.insert(0, arg)
      if varg:
        spec.append("*" + varg)
      if kwarg:
        spec.append("**" + kwarg)
      self.out("def " + p_name + "(" + ", ".join(spec) + "): # reliably restored by inspect", indent);
      self.outDocAttr(p_func, indent+1, p_class)
      self.out("pass", indent+1);
    elif doing_builtins and p_modname == BUILTIN_MOD_NAME and (classname, p_name) in self.PREDEFINED_BUILTIN_SIGS:
      spec = self.PREDEFINED_BUILTIN_SIGS[(classname, p_name)]
      self.out("def " + p_name + spec + ": # known special case of " + (classname and classname+"." or "") + p_name, indent)
      self.outDocAttr(p_func, indent+1, p_class)
      self.out("pass", indent+1);
    else:
      # __doc__ is our best source of arglist
      sig_note = "real signature unknown"
      spec = []
      is_init = (p_name == "__init__" and p_class is not None)
      funcdoc = None
      if is_init and hasattr(p_class, "__doc__"):
        if hasattr(p_func, "__doc__"):
          funcdoc = p_func.__doc__
        if funcdoc == object.__init__.__doc__:
          funcdoc = p_class.__doc__
      elif hasattr(p_func, "__doc__"):
        funcdoc = p_func.__doc__
      sig_restored = False
      if isinstance(funcdoc, STR_TYPES):  
        m = DOC_FUNC_RE.search(funcdoc)
        if m:
          matches = m.groups()
          if matches[0] == p_name or is_init: 
            # they seem to really mention what we need
            sig_note = "restored from __doc__"
            sig_restored = True
            reqargs = []
            optargs = []
            optargvals = [] # values of optional args, "=x" or "", one per optarg
            argmod = 1 # argument modifier counter for duplicate values
            if len(matches) > 1:
              argstr = matches[1]
              # cut between fixed and optional args  
              brk_cutpos = string.find(argstr, '[') # (a, b[, c]) 
              if brk_cutpos < 0:
                brk_cutpos = len(argstr)
              star_cutpos = string.find(argstr, '*') # (a, *b) 
              if star_cutpos < 0:
                star_cutpos = len(argstr)
              eq_cutpos = len(argstr)   
              m = IDENT_EQ_RE.search(argstr) # (a, b=1)
              if m:
                eq_cutpos = m.start()
              cutpos = min(star_cutpos, brk_cutpos, eq_cutpos) # before this point come required args, after it optional
              # possible "required args" 
              for arg in argstr[:cutpos].split(", "): 
                arg = arg.strip("\"'") # doc might speak of f("foo")
                m = IDENT_RE.search(arg)
                if m and m.groups() and m.groups()[0]:
                  argname = sanitizeIdent(m.groups(0)[0])
                  if argname in reqargs:
                    argname += str(argmod) # foo -> foo1, etc
                    argmod += 1
                  reqargs.append(argname)
                elif arg == "...":
                  arg = "*more" # doc might speak of f(x, ...)
                  reqargs.append(arg)
                  # else: skip the unknown thing
              # possible "optional args" 
              for arg in argstr[cutpos:].split(','):
                m = STARS_IDENT_RE.search(arg)
                if m and m.groups() and m.groups()[0]: # got default value?
                  argname = sanitizeIdent(m.groups(0)[0])
                  if argname in reqargs or argname in optargs:
                    argname += str(argmod) # foo -> foo1, etc
                    argmod += 1
                  optargs.append(argname)
                  if argname.startswith("*"):
                    optargvals.append("") # "*x" args can't have default values
                  else:
                    mdef = VAL_RE.search(arg)
                    if mdef:
                      defval = arg[mdef.start() : mdef.end()]
                    else:
                      defval = 'None'
                    optargvals.append("="+defval)
              # we may be missng 'self' because doc comment omits it
              if p_class:
                if self.KNOWN_DECORATORS.get((classname, p_name), None) == "@static":
                  pass
                elif not reqargs or not seemsToHaveSelf(reqargs):
                  reqargs.insert(0, "self")
                else:
                  if sig_note:
                      sig_note += "; "
                  sig_note += "considered " + reqargs[0] + " to be 'self'"
            # reconstruct the spec
            spec = reqargs + [n + v for (n, v) in zip(optargs, optargvals)]
        else:
          funcdoc = None
      if not sig_restored:
        # use an allow-all declaration
        if p_class:
          spec.append("self")
        spec.append("*args")
        spec.append("**kwargs")
      self.out("def " + p_name + "(" + ", ".join(spec) + "): # " + sig_note, indent);
      self.outDocstring(funcdoc, indent+1)
      self.out("pass", indent+1);

  def redoClass(self, p_class, p_name, indent, p_modname=None):
    """
    Restores a class definition.
    @param p_class the class object
    @param p_name function name as known to owner
    @param indent indentation level
    """
    bases = getBases(p_class)
    base_def = ""
    if bases:
      base_def = "(" + ", ".join([x.__name__ for x in bases]) + ")"
    self.out("class " + p_name + base_def + ":", indent);
    self.outDocAttr(p_class, indent+1)
    # inner parts
    if hasattr(p_class, "__dict__"):
      methods = {}
      properties = {}
      others = {}
      for item_name in p_class.__dict__:
        if item_name in ("__dict__", "__doc__", "__module__"):
          # must be declared in base types
          if p_modname == BUILTIN_MOD_NAME and p_name in ("object", FAKE_CLASSOBJ_NAME):
            if item_name == "__dict__":
              item = {}
            else:
              item = ""
          else:
            continue # in all other cases. must be skipped
        else:
          item =  p_class.__dict__[item_name]
        if isCallable(item):
          methods[item_name] = item
        elif isProperty(item):
          properties[item_name] = item
        else:
          others[item_name] = item
        #
      # add fake __init__s to type and tuple to have the right sig
      if p_class in self.FAKE_BUILTIN_INITS:
        methods["__init__"] = self.fake_builtin_init
      #  
      for item_name in sortedNoCase(methods.keys()):
        item =  methods[item_name]
        self.redoFunction(item, item_name, indent+1, p_class, p_modname)
        self.out("", 0) # empty line after each item
      #  
      for item_name in sortedNoCase(properties.keys()):
        item =  properties[item_name]
        self.out(item_name + " =  property(None, None, None)", indent+1); # TODO: handle docstring
      if properties:
        self.out("", 0) # empty line after the block
      #
      for item_name in sortedNoCase(others.keys()):
        item =  others[item_name]
        self.fmtValue(item, indent+1, prefix = item_name + " = ")
      if others:
        self.out("", 0) # empty line after the block
      #  
    if not methods and not properties and not others:
      self.out("pass", indent+1);
    
    
  def redo(self, p_name):
    """
    Restores module declarations.
    Intended for built-in modules and thus does not handle import statements. 
    """
    self.out("# encoding: utf-8", 0); # NOTE: maybe encoding must be selectable
    if hasattr(self.module, "__name__"):
      mod_name = " calls itself " + self.module.__name__
    else:
      mod_name = " does not know its name"
    self.out("# module " + p_name +  mod_name, 0)
    if hasattr(self.module, "__file__"):
      self.out("# from file " + self.module.__file__, 0)
    self.outDocAttr(self.module, 0)
    # find whatever other self.imported_modules the module knows; effectively these are imports
    for item_name in self.module.__dict__:
      item = self.module.__dict__[item_name]
      if isinstance(item, type(sys)):
        self.imported_modules[item_name] = item
        if hasattr(item, "__name__"):
          self.out("import " + item.__name__ + " as " + item_name + " # refers to " + str(item))
        else:
          self.out(item_name + " = None # XXX name unknown, refers to " + str(item))
    self.out("", 0) # empty line after imports
    # group what else we have into buckets
    vars_simple = {}
    vars_complex = {}
    funcs = {}
    classes = {}
    reexports = {} # contains not real objects, but qualified id strings, like "sys.stdout"
    #
    for item_name in self.module.__dict__:
      if item_name in ("__dict__", "__doc__", "__module__", "__file__", "__name__"):
        continue
      try:
        item = getattr(self.module, item_name) # let getters do the magic
      except:
        item = self.module.__dict__[item_name] # have it raw
      # check if it has percolated from an imported module
      imported_name = self.findImportedName(item)
      if imported_name is not None:
        reexports[item_name] = imported_name
      else:
        if isinstance(item, type) or item is FakeClassObj: # some classes are callable, check them before functions
          classes[item_name] = item
        elif isCallable(item):
          funcs[item_name] = item
        elif isinstance(item, type(sys)):
          continue # self.imported_modules handled above already
        else:
          if isinstance(item, SIMPLEST_TYPES):
            vars_simple[item_name] = item
          else:
            vars_complex[item_name] = item
        #  
    # sort and output every bucket
    if reexports:
      self.out("# reexported imports", 0)
      self.out("", 0)
      for item_name in sortedNoCase(reexports.keys()):
        item = reexports[item_name]
        self.out(item_name + " = " + item, 0)
        self._defined[item_name] = True
      self.out("", 0) # empty line after group
    #
    if vars_simple:
      prefix = "" # try to group variables by common prefix
      PREFIX_LEN = 2 # default prefix length if we can't guess better
      self.out("# Variables with simple values", 0)
      for item_name in sortedNoCase(vars_simple.keys()):
        item = vars_simple[item_name]
        # track the prefix
        if len(item_name) >= PREFIX_LEN:
          prefix_pos = string.rfind(item_name, "_") # most prefixes end in an underscore
          if prefix_pos < 1:
            prefix_pos = PREFIX_LEN
          beg = item_name[0:prefix_pos]
          if prefix != beg:
            self.out("", 0) # space out from other prefix
            prefix = beg
        else:
          prefix = ""
        # output
        replacement = self.REPLACE_MODULE_VALUES.get((p_name, item_name), None)
        if replacement is not None:
          self.out(item_name + " = " + replacement + " # real value of type "+ str(type(item)) + " replaced", 0);
        elif self.isSkippedInModule(p_name, item_name):
          self.out(item_name + " = None # real value of type "+ str(type(item)) + " skipped", 0)
        else:
          self.fmtValue(item, 0, prefix = item_name + " = " )
        self._defined[item_name] = True
      self.out("", 0); # empty line after vars
    #
    if funcs:
      self.out("# functions", 0)
      self.out("", 0)
      for item_name in sortedNoCase(funcs.keys()):
        item = funcs[item_name]
        self.redoFunction(item, item_name, 0, p_modname=p_name)
        self._defined[item_name] = True
        self.out("", 0) # empty line after each item
    else:
      self.out("# no functions", 0)
    #
    if classes:
      self.out("# classes", 0)
      self.out("", 0)
      # sort classes so that inheritance order is preserved
      cls_list = [] # items are (class_name, mro_tuple)
      for cls_name in sortedNoCase(classes.keys()):
        cls = classes[cls_name]
        ins_index = len(cls_list)
        for i in range(ins_index):
          maybe_child_bases = cls_list[i][1]  
          if cls in maybe_child_bases:
            ins_index = i # we could not go farther than current ins_index
            break         # ...and need not go fartehr than first known child
        cls_list.insert(ins_index, (cls_name, getMRO(cls)))     
      for item_name in [cls_item[0] for cls_item in cls_list]:
        item = classes[item_name]
        self.redoClass(item, item_name, 0, p_modname=p_name)
        self._defined[item_name] = True
        self.out("", 0) # empty line after each item
    else:
      self.out("# no classes", 0)
    #
    if vars_complex:
      self.out("# variables with complex values", 0)
      self.out("", 0)
      for item_name in sortedNoCase(vars_complex.keys()):
        item = vars_complex[item_name]
        replacement = self.REPLACE_MODULE_VALUES.get((p_name, item_name), None)
        if replacement is not None:
          self.out(item_name + " = " + replacement + " # real value of type "+ str(type(item)) + " replaced", 0);
        elif self.isSkippedInModule(p_name, item_name):
          self.out(item_name + " = None # real value of type "+ str(type(item)) + " skipped", 0);
        else:
          self.fmtValue(item, 0, prefix = item_name + " = " , as_name = item_name);
        self._defined[item_name] = True
        self.out("", 0) # empty line after each item
    

# command-line interface
if __name__ == "__main__":
  from getopt import getopt
  import os
  if sys.version_info[0] > 2:
    import io  # in 3.0
    fopen = io.open
  else:
    fopen = open
  
  # handle cmdline
  helptext="""Generates interface skeletons for python modules.
  Usage: generator [options] [name ...]
  Every "name" is a (qualified) module name, e.g. "foo.bar"
  Output files will be named as modules plus ".py" suffix.
  Normally every name processed will be printed and stdout flushed. 
  Options are:
  -h -- prints this help message.
  -d dir -- output dir, must be writable. If not given, current dir is used. 
  -b -- use names from sys.builtin_module_names
  -q -- quiet, do not print anything on stdout. Errors still go to stderr.
  -u -- update, only recreate skeletons for newer files, and skip unchanged.
  """
  opts, fnames = getopt(sys.argv[1:], "d:hbqu")
  opts = dict(opts)
  if not opts or '-h' in opts:
    print(helptext)
    sys.exit(0)
  if '-b' not in opts and not fnames:
    print("Neither -b nor any module name given")  
    sys.exit(1)
  quiet = '-q' in opts
  update_mode = "-u" in opts
  subdir = opts.get('-d', '')
  # determine names
  names = fnames
  if '-b' in opts:
    doing_builtins = True
    names.extend(sys.builtin_module_names)
    if not '__builtin__' in names:
      names.append('__builtin__')
    if '__main__' in names:
      names.remove('__main__') # we don't want ourselves processed
  # go on
  for name in names:
    if not quiet:
      sys.stdout.write(name + "\n")
      sys.stdout.flush()
    action = "doing nothing"
    try:
      quals = name.split(".")
      dirname = subdir
      if dirname:
        dirname += os.path.sep # "a -> a/"
      for pathindex in range(len(quals)-1): # create dirs for all quals but last
        dirname += os.path.sep.join(quals[0 : pathindex+1])
        if not os.path.isdir(dirname):
          action = "creating subdir " + dirname
          os.makedirs(dirname)
      fname = dirname + os.path.sep + quals[-1] + ".py"
      #
      action = "importing"
      try:
        mod = __import__(name)
      except ImportError:
        sys.stderr.write("Name " + name + " failed to import\n")
        continue
      # we can't really import a.b.c, only a, so follow the path
      for q in quals[1:]:
        action = "getting submodule " + q
        try:
          mod = getattr(mod, q)
        except AttributeError:
          sys.stderr.write("Name " + name + " is not really importable at point " + q + "\n")
          sys.exit(0)
      #
      if update_mode and hasattr(mod, "__file__"):
        action = "probing " + fname
        mod_mtime = os.path.exists(mod.__file__) and os.path.getmtime(mod.__file__) or 0.0
        file_mtime = os.path.exists(fname) and os.path.getmtime(fname) or 0.0
        if mod_mtime <= file_mtime:
          continue # skip the file
      if doing_builtins and name == BUILTIN_MOD_NAME:
        action = "grafting"
        setattr(mod, FAKE_CLASSOBJ_NAME, FakeClassObj)
      action = "opening " + fname
      outfile = fopen(fname, "w")
      action = "restoring"
      r = ModuleRedeclarator(mod, outfile)
      r.redo(name)
      action = "closing " + fname
      outfile.close()
    except:
      sys.stderr.write("Failed to process " + name + " while " + action + "\n")
      continue

# encoding: utf-8
# this code must work under pythons 2.2 through 3.0

import sys
import os


version = (
  (sys.hexversion & (0xff << 24)) >> 24,
  (sys.hexversion & (0xff << 16)) >> 16
)

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

def is_binary(path, f):
    suffixes = ('.so', '.pyd')
    for suf in suffixes:
      if f.endswith(suf):
        return True
    if f.endswith('.pyc') or f.endswith('.pyo'):
      fullname = os.path.join(path, f[:-1])
      return not os.path.exists(fullname)
    return False

def find_binaries(paths):
  """
  Finds binaries in the given list of paths. 
  Understands nested paths, as sys.paths have it (both "a/b" and "a/b/c").
  Tries to be case-insensitive, but case-preserving.
  @param paths a list of paths.
  @return a list like [(module_name: full_path),.. ]
  """
  SEP = os.path.sep
  res = {} # {name.upper(): (name, full_path)}
  if not paths:
    return {}
  if hasattr(os, "java"): # jython can't have binary modules
    return {} 
  paths = sortedNoCase(paths)
  for path in paths:
    for root, dirs, files in os.walk(path):
      cutpoint = path.rfind(SEP)
      if cutpoint > 0:
        preprefix = path[(cutpoint + len(SEP)):] + '.'
      else:
        preprefix = ''
      prefix = root[(len(path) + len(SEP)):].replace(SEP, '.')
      if prefix:
        prefix += '.'
      #print root, path, prefix, preprefix # XXX
      for f in files:
        if is_binary(root, f):
          name = f[:f.rindex('.')]
          #print "+++ ", name
          if preprefix:
            #print("prefixes: ", prefix, preprefix) # XXX
            pre_name = (preprefix + prefix + name).upper()
            if pre_name in res:
              res.pop(pre_name) # there might be a dupe, if paths got both a/b and a/b/c
            #print "+ ", name # XXX
          the_name = prefix + name
          res[the_name.upper()] = (the_name, root + SEP + f)
  return list(res.values())


# command-line interface
if __name__ == "__main__":
  from getopt import getopt

  helptext="""Finds binary importable python modules.
  Usage:
    find_binaries.py -h -- prints this message.
    find_binaries.py [dir ...]
  Every "dir" will be non-recursively searched for binary modules (.so, .pyd).
  The list of full found modules is printed to stdout in the following format:
    module_namme <space> full paths to the binary file <newline>
  On filesystems that don't hamour case properly, module_name may have a wrong 
  case. Python import should be able to cope with this, though.
  If no dirs are given. sys.path will be the list of dirs.
  """
  opts, dirs = getopt(sys.argv[1:], "h")
  opts = dict(opts)
  if '-h' in opts:
    print(helptext)
    sys.exit(0)
    
  if not dirs:
    dirs = sys.path
  
  for name, path in find_binaries(dirs):
    sys.stdout.write(name)
    sys.stdout.write(" ")
    sys.stdout.write(path)
    sys.stdout.write("\n")
    

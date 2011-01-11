import os
import imp
import sys
import types
import re
from tcmessages import TeamcityServiceMessages
from tcunittest import TeamcityTestRunner

from nose_helper import TestLoader, ContextSuite

PYTHON_VERSION_MAJOR = sys.version_info[0]
PYTHON_VERSION_MINOR = sys.version_info[1]

ENABLE_DEBUG_LOGGING = False
if os.getenv("UTRUNNER_ENABLE_DEBUG_LOGGING"):
  ENABLE_DEBUG_LOGGING = True

def debug(what):
  if ENABLE_DEBUG_LOGGING:
    print >>sys.stdout, what

modules = {}
def getModuleName(prefix, cnt):
  return prefix + "%" + str(cnt)

def loadSource(fileName):
  baseName = os.path.basename(fileName)
  moduleName = os.path.splitext(baseName)[0]
  if moduleName in modules: # add unique number to prevent name collisions
    cnt = 2
    prefix = moduleName
    while getModuleName(prefix, cnt) in modules:
      cnt += 1
    moduleName = getModuleName(prefix, cnt)
  debug("/ Loading " + fileName + " as " + moduleName)
  module = imp.load_source(moduleName, fileName)
  modules[moduleName] = module
  return module

def walkModules(modules, dirname, names):
  for name in names:
    if name.endswith(".py"):
      modules.append(loadSource(os.path.join(dirname, name)))

def loadModulesFromFolderRec(folder):
  modules = []
  if PYTHON_VERSION_MAJOR == 3:
    for root, dirs, files in os.walk(folder, walkModules, modules):
      for name in files:
        if name.endswith(".py"):
          modules.append(loadSource(os.path.join(root, name)))
  else:
    os.path.walk(folder, walkModules, modules)

  return modules

def loadModulesFromFolderUsingPattern(folder, pattern):
  ''' loads modules from folder ,
      check if module name matches given pattern'''
  modules = loadModulesFromFolderRec(folder)
  prog_list = [re.compile(pat.strip()) for pat in pattern.split(',')]
  result = []  

  for module in modules:
    for prog in prog_list:
      if prog.match(module.__name__):
        result.append(module)
  return result

if __name__ == "__main__":
  #testLoader = unittest.TestLoader()
  testLoader = TestLoader()

  #all = unittest.TestSuite()
  all = ContextSuite()

  for arg in sys.argv[1:]:
    arg = arg.strip()
    if len(arg) == 0:
      continue

    a = arg.split("::")
    if len(a) == 1:
      # From module or folder
      a_splitted = a[0].split(";")
      if len(a_splitted) != 1:
        # means we have pattern to match against
        if a_splitted[0].endswith("/"):
          debug("/ from folder " + a_splitted[0] + ". Use pattern: " + a_splitted[1])
          modules = loadModulesFromFolderUsingPattern(a_splitted[0], a_splitted[1])
      else:
        if a[0].endswith("/"):
          debug("/ from folder " + a[0])
          modules = loadModulesFromFolderRec(a[0])
        else:
          debug("/ from module " + a[0])
          modules = [loadSource(a[0])]

      for module in modules:
        all.addTests(testLoader.loadTestsFromModule(module, True))

    elif len(a) == 2:
      # From testcase
      debug("/ from testcase " + a[1] + " in " + a[0])
      module = loadSource(a[0])
      all.addTests(testLoader.loadTestsFromTestClass(getattr(module, a[1])))
    else:
      # From method in class or from function
      debug("/ from method " + a[2] + " in testcase " +  a[1] + " in " + a[0])
      module = loadSource(a[0])
      if a[1] == "":
          # test function, not method
          all.addTest(testLoader.makeTest(getattr(module, a[2])))
      else:
          testCaseClass = getattr(module, a[1])
          try:
              all.addTest(testCaseClass(a[2]))
          except:
              # class is not a testcase inheritor
              all.addTest(testLoader.makeTest(getattr(testCaseClass, a[2]), testCaseClass))


  debug("/ Loaded " + str(all.countTestCases()) + " tests")
  TeamcityServiceMessages(sys.stdout).testCount(all.countTestCases())
  TeamcityTestRunner().run(all)
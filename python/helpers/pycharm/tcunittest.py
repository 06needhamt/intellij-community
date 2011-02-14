import traceback, sys
from unittest import TestResult
import datetime

from tcmessages import TeamcityServiceMessages

PYTHON_VERSION_MAJOR = sys.version_info[0]

def strclass(cls):
    if not cls.__name__:
        return cls.__module__
    return "%s.%s" % (cls.__module__, cls.__name__)

class TeamcityTestResult(TestResult):
    def __init__(self, stream=sys.stdout):
        TestResult.__init__(self)

        self.output = stream
        self.messages = TeamcityServiceMessages(self.output, prepend_linebreak=True)
        self.current_suite = None

    def find_first(self, val):
      quot = val[0]
      count = 1
      quote_ind = val[count:].find(quot)
      while val[count+quote_ind-1] == "\\":
        count = count + quote_ind + 1
        quote_ind = val[count:].find(quot)

      return val[0:quote_ind+count+1]

    def find_second(self, val):
      quot = val[-1]
      count = 1
      quote_ind = val[:len(val)-count-1].rfind(quot)
      while val[quote_ind-1] == "\\":
        quote_ind = val[:quote_ind-1].rfind(quot)

      return val[quote_ind:]


    def formatErr(self, err):
        exctype, value, tb = err
        return ''.join(traceback.format_exception(exctype, value, tb))

    def getTestName(self, test):
        if hasattr(test, '_testMethodName'):
            if test._testMethodName == "runTest":
                return str(test)
            return test._testMethodName
        else:
            return str(test)

    def getTestId(self, test):
        return test.id

    def addSuccess(self, test):
        TestResult.addSuccess(self, test)

    def addError(self, test, err):
        TestResult.addError(self, test, err)

        err = self.formatErr(err)

        self.messages.testError(self.getTestName(test),
            message='Error', details=err)

    def addFailure(self, test, err):
        TestResult.addFailure(self, test, err)

        error_value = str(err[1])

        if error_value.startswith("'") or error_value.startswith('"'):
            # let's unescape strings to show sexy multiline diff in PyCharm.
            # By default all caret return chars are escaped by testing framework
            first = self._unescape(self.find_first(error_value))
            second = self._unescape(self.find_second(error_value))
        else:
          first = second = ""
        err = self.formatErr(err)

        self.messages.testFailed(self.getTestName(test),
            message='Failure', details=err, expected=first, actual=second)

    def addSkip(self, test, reason):
        self.messages.testIgnored(self.getTestName(test), message=reason)

    def __getSuite(self, test):
      if hasattr(test, "suite"):
        suite = strclass(test.suite)
        suite_location = test.suite.location
        location = test.suite.abs_location
        if hasattr(test, "lineno"):
          location = location + ":" + str(test.lineno)
        else:
          location = location + ":" + str(test.test.lineno)
      else:
        suite = strclass(test.__class__)
        suite_location = "python_uttestid://" + suite
        location = "python_uttestid://" + str(test.id())

      return (suite, location, suite_location)

    def startTest(self, test):
        suite, location, suite_location = self.__getSuite(test)
        if suite != self.current_suite:
            if self.current_suite:
                self.messages.testSuiteFinished(self.current_suite)
            self.current_suite = suite
            self.messages.testSuiteStarted(self.current_suite, location=suite_location)
        setattr(test, "startTime", datetime.datetime.now())
        self.messages.testStarted(self.getTestName(test), location=location)

    def stopTest(self, test):
        start = getattr(test, "startTime", datetime.datetime.now())
        d = datetime.datetime.now() - start
        duration=d.microseconds / 1000 + d.seconds * 1000 + d.days * 86400000
        self.messages.testFinished(self.getTestName(test), duration=int(duration))

    def endLastSuite(self):
        if self.current_suite:
            self.messages.testSuiteFinished(self.current_suite)
            self.current_suite = None

    def _unescape(self, text):
      if PYTHON_VERSION_MAJOR == 3:
        return text
      else:
        return text.decode('string_escape')

class TeamcityTestRunner:
    def __init__(self, stream=sys.stdout):
        self.stream = stream

    def _makeResult(self):
        return TeamcityTestResult(self.stream)

    def run(self, test):
        result = self._makeResult()
        test(result)
        result.endLastSuite()
        return result

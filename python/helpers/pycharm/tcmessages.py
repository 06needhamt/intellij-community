import sys

class TeamcityServiceMessages:
    quote = {"'": "|'", "|": "||", "\n": "|n", "\r": "|r", ']': '|]'}
    
    def __init__(self, output=sys.stdout):
        self.output = output
    
    def escapeValue(self, value):
        return "".join([self.quote.get(x, x) for x in str(value)])
    
    def message(self, messageName, **properties):
        s = "##teamcity[" + messageName
        for k, v in properties.items():
            if v is None:
                continue
            s = s + " %s='%s'" % (k, self.escapeValue(v))
        s += "]\n"

        self.output.write(s)

    def testSuiteStarted(self, suiteName, location=None):
        self.message('testSuiteStarted', name=suiteName, location=location)

    def testSuiteFinished(self, suiteName):
        self.message('testSuiteFinished', name=suiteName)

    def testStarted(self, testName, location=None):
        self.message('testStarted', name=testName, location=location)

    def testFinished(self, testName, duration=None):
        self.message('testFinished', name=testName, duration=duration)

    def testIgnored(self, testName, message=''):
        self.message('testIgnored', name=testName, message=message)
        
    def testFailed(self, testName, message='', details=''):
        self.message('testFailed', name=testName, message=message, details=details)
        
    def testStdOut(self, testName, out):
        self.message('testStdOut', name=testName, out=out)

    def testStdErr(self, testName, out):
        self.message('testStdErr', name=testName, out=out)

    def testCount(self, count):
        self.message('testCount', count=count)

package com.qaprosoft.jenkins

@Grab('org.testng:testng:6.8.8')
import org.testng.xml.Parser
import org.testng.xml.XmlSuite

class Utils {

    static def printStackTrace(Exception e) {
        def stringStacktrace = ""
        e.getStackTrace().each { traceLine ->
            stringStacktrace = stringStacktrace + "\tat " + traceLine + "\n"
        }
        return "${e.getClass().getName()}: ${e.getMessage()}\n" + stringStacktrace
    }

    static def encodeToBase64(stringValue) {
        return stringValue.bytes.encodeBase64().toString()
    }

    static XmlSuite parseSuite(String path) {
        def xmlFile = new Parser(path)
        xmlFile.setLoadClasses(false)
        List<XmlSuite> suiteXml = xmlFile.parseToList()
        XmlSuite currentSuite = suiteXml.get(0)
        return currentSuite
    }

    static boolean isParamEmpty(value) {
        if (value == null) {
            return true
        }  else {
            return value.toString().isEmpty() || value.toString().equalsIgnoreCase("NULL")
        }
    }

    static def getSuiteParameter(defaultValue, parameterName, currentSuite){
        def value = defaultValue
        if (!isParamEmpty(currentSuite.getParameter(parameterName))) {
            value = currentSuite.getParameter(parameterName)
        }
        return value
    }

    static def replaceTrailingSlash(value) {
        return value.replaceAll(".\$","")
    }

    static boolean getBooleanParameterValue(parameter, currentSuite){
        return !isParamEmpty(currentSuite.getParameter(parameter)) && currentSuite.getParameter(parameter).toBoolean()
    }
}
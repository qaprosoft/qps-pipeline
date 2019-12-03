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
        def value
        def suiteOwnerList = []
        if (!isParamEmpty(currentSuite.getParameter(parameterName))) {
            value = currentSuite.getParameter(parameterName)
            if (value.contains(", ")) {
                suiteOwnerList = value.split(", ")
            } else if (value.contains(",")) {
                suiteOwnerList = value.split(",")
            } else {
                suiteOwnerList.add(defaultValue)
            }
        }
        return suiteOwnerList[0]
    }

    static def replaceTrailingSlash(value) {
        return value.replaceAll(".\$","")
    }

    static def replaceStartSlash(String value) {
        if (value[0].equals("/")) {
            value = value.replaceFirst("/", "")
        }
        return value
    }

    static def replaceSlashes(String value, String str) {
        if (value.contains("/")) {
            value = value.replaceAll("/", str)
        }
        return value
    }

    static boolean getBooleanParameterValue(parameter, currentSuite){
        return !isParamEmpty(currentSuite.getParameter(parameter)) && currentSuite.getParameter(parameter).toBoolean()
    }
}
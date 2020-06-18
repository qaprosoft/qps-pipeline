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
        } else {
            return value.toString().isEmpty() || value.toString().equalsIgnoreCase("NULL")
        }
    }

    static def getSuiteParameter(defaultValue, parameterName, currentSuite) {
        def value = defaultValue
        if (!isParamEmpty(currentSuite.getParameter(parameterName))) {
            value = currentSuite.getParameter(parameterName)
        }
        return value
    }

    static def replaceTrailingSlash(value) {
        return value.replaceAll(".\$", "")
    }

    static def replaceStartSlash(String value) {
        if (value[0].equals("/")) {
            value = value.replaceFirst("/", "")
        }
        return value
    }

    static def replaceSpecialSymbols(String value) {
        return value.replaceAll("[^\\w-]", "_")
    }

    static boolean getBooleanParameterValue(parameter, currentSuite) {
        return !isParamEmpty(currentSuite.getParameter(parameter)) && currentSuite.getParameter(parameter).toBoolean()
    }

    static def replaceMultipleSymbolsToOne(String value, String symbol) {
        if (value.contains(symbol + symbol)) {
            value = value.replace(symbol + symbol, symbol)
            replaceMultipleSymbolsToOne(value, symbol)
        } else {
            //delete last character if character == symbol
            if (value[-1] == symbol) {
                value = value.substring(0, value.length() - 1)
            }
            return value
        }
    }
}

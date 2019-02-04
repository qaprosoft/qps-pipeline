package com.qaprosoft

@Grab('org.testng:testng:6.8.21')
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

}
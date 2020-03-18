package com.qaprosoft.jenkins

@Grab('org.testng:testng:6.8.8')
import org.testng.xml.Parser
import org.testng.xml.XmlSuite
import com.qaprosoft.jenkins.pipeline.Configuration

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


    static def getZafiraServiceParameters(orgName) {
        def orgFolderName = Paths.get(Configuration.get(Configuration.Parameter.JOB_NAME)).getName(0).toString()
        def zafiraURLCredentials = orgFolderName + "-zafira_service_url"
        def zafiraTokenCredentials = orgFolderName + "-zafira_access_token"
        def resultList

        if (getCredentials(zafiraURLCredentials)){
            context.withCredentials([context.usernamePassword(credentialsId:zafiraURLCredentials, usernameVariable:'KEY', passwordVariable:'VALUE')]) {
                resultList.add(context.env.VALUE)
            }
        } else {
            resultList.add('')
        }

        if (getCredentials(zafiraTokenCredentials)){
            context.withCredentials([context.usernamePassword(credentialsId:zafiraTokenCredentials, usernameVariable:'KEY', passwordVariable:'VALUE')]) {
                resultList.add(context.env.VALUE)
            }
        } else {
            resultList.add('')
        }

        return resultList
    }
}
package com.qaprosoft.jenkins

@Grab('org.testng:testng:6.8.8')
import org.testng.xml.Parser
import org.testng.xml.XmlSuite
import javax.xml.parsers.DocumentBuilderFactory

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

    static def parseSuite(filePath){
//        logger.debug("filePath: " + filePath)
        XmlSuite currentSuite = null
        try {
            currentSuite = parseSuite(filePath)
        } catch (FileNotFoundException e) {
//            logger.error("ERROR! Unable to find suite: " + filePath)
//            logger.error(printStackTrace(e))
        } catch (Exception e) {
//            logger.error("ERROR! Unable to parse suite: " + filePath)
//            logger.error(printStackTrace(e))
        }
        return currentSuite
    }

//    static def getAttributeValue(filePath, attributeName) {
//        return parseSuite(filePath).getAttribute(attributeName)
//    }

    static def getAttributeValue(filePath, attribute) {
        def suite = parseSuite(filePath)
        def res = ""
        def file = new File(filePath)
        def documentBuilderFactory = DocumentBuilderFactory.newInstance()

        documentBuilderFactory.setValidating(false)
        documentBuilderFactory.setNamespaceAware(true)
        try {
            documentBuilderFactory.setFeature("http://xml.org/sax/features/namespaces", false)
            documentBuilderFactory.setFeature("http://xml.org/sax/features/validation", false)
            documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
            documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

            def documentBuilder = documentBuilderFactory.newDocumentBuilder()
            def document = documentBuilder.parse(file)

            for (int i = 0; i < document.getChildNodes().getLength(); i++) {
                def nodeMapAttributes = document.getChildNodes().item(i).getAttributes()
                if (nodeMapAttributes == null) {
                    continue
                }

                // get "name" from suite element
                // <suite verbose="1" name="Carina Demo Tests - API Sample" thread-count="3" >
                Node nodeName = nodeMapAttributes.getNamedItem("name")
                if (nodeName == null) {
                    continue
                }

                if (suite.getName().equals(nodeName.getNodeValue())) {
                    // valid suite node detected
                    Node nodeAttribute = nodeMapAttributes.getNamedItem(attribute)
                    if (nodeAttribute != null) {
                        res = nodeAttribute.getNodeValue()
                        break
                    }
                }
            }
        } catch (Exception e) {
//            LOGGER.warn("Unable to get attribute '" + attribute +"' from suite: " + suite.getXmlSuite().getFileName(), e)
        }

        return res
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

    static def replaceSpecialSymbols(String value) {
        return value.replaceAll("[^\\w-]", "_")
    }

    static boolean getBooleanParameterValue(parameter, currentSuite){
        return !isParamEmpty(currentSuite.getParameter(parameter)) && currentSuite.getParameter(parameter).toBoolean()
    }
}
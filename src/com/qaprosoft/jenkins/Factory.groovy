package com.qaprosoft.jenkins

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.Utils
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// groovy script for initialization and execution all kind of jobdsl factories which are transfered from pipeline scanner script

Logger logger = new Logger(this)
def slurper = new JsonSlurper()

String factoryDataMap = readFileFromWorkspace("factories.json")
logger.info("FactoryDataMap: ${JsonOutput.prettyPrint(factoryDataMap)}")
def factories = new LinkedHashMap(slurper.parseText(factoryDataMap))
boolean exceptionOccurred = false

factories.each {
    try {
        def factory = Class.forName(it.value.clazz)?.newInstance(this)
        logger.debug("Factory before load: ${it.value.dump()}")
        factory.load(it.value)
        logger.debug("Factory after load: ${factory.dump()}")
        factory.create()
    } catch (Exception e) {
        logger.error(Utils.printStackTrace(e))
        exceptionOccurred = true
    }
}
if (exceptionOccurred) {
    logger.error("Something went wrong during job creation. Please, check stacktrace for more information.")
    throw new RuntimeException("JobDslException occurred.")
}

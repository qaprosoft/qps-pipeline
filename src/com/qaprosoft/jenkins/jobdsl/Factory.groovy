package com.qaprosoft.jenkins.jobdsl

import com.qaprosoft.Logger
import com.qaprosoft.Utils
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// groovy script for initialization and execution all kind of jobdsl factories which are transfered from pipeline scanner script

def logLevel = binding.variables.PIPELINE_LOG_LEVEL
Logger logger = new Logger(this)
def slurper = new JsonSlurper()
String factoryDataMap = readFileFromWorkspace("factories.json")
logger.info("FactoryDataMap: ${JsonOutput.prettyPrint(factoryDataMap)}")
def factories = new HashMap(slurper.parseText(factoryDataMap))
//Lambda replaced with for loop, because printf method necessary for logging logic seems not to work inside each closure
for(factory in factories){
    try {
        def factoryObject = Class.forName(factory.value.clazz)?.newInstance(this)
        logger.debug("Factory before load: ${factory.value.dump()}")
        factoryObject.load(factory.value)
        logger.debug("Factory after load: ${factoryObject.dump()}")
        factoryObject.create()
    } catch (Exception e) {
        logger.error(Utils.printStackTrace(e))
        throw new RuntimeException("JobDSL Exception")
    }
}

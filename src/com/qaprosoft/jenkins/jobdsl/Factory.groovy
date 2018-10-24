package com.qaprosoft.jenkins.jobdsl

import com.qaprosoft.jenkins.Utils
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// groovy script for initialization and execution all kind of jobdsl factories which are transfered from pipeline scanner script

def logLevel = binding.variables.PIPELINE_LOG_LEVEL
def slurper = new JsonSlurper()
String factoryDataMap = readFileFromWorkspace("factories.json")
printf Utils.info(logLevel,"FactoryDataMap: ${JsonOutput.prettyPrint(factoryDataMap)}")
def factories = new HashMap(slurper.parseText(factoryDataMap))
//Lambda replaced with for loop, because printf method necessary for logging logic seems not to work inside each closure
for(factory in factories){
    try {
        def factoryObject = Class.forName(factory.value.clazz)?.newInstance(this)
        printf Utils.debug(logLevel, "Factory before load: ${factory.value.dump()}")
        factoryObject.load(factory.value)
        printf Utils.debug(logLevel, "Factory after load: ${factoryObject.dump()}")
        factoryObject.create()
    } catch (Exception e) {
        printf Utils.error(logLevel, Utils.printStackTrace(e))
        throw new RuntimeException("JobDSL Exception")
    }
}

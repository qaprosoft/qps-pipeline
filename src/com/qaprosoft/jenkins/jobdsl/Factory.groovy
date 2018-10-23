package com.qaprosoft.jenkins.jobdsl

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.Utils
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// groovy script for initialization and execution all kind of jobdsl factories which are transfered from pipeline scanner script

def logLevel = binding.variables.PIPELINE_LOG_LEVEL
def slurper = new JsonSlurper()
String factoryDataMap = readFileFromWorkspace("factories.json")
printf Logger.info(logLevel,"FactoryDataMap: ${JsonOutput.prettyPrint(factoryDataMap)}")
def factories = new HashMap(slurper.parseText(factoryDataMap))
factories.each{
	try {
		def factory = Class.forName(it.value.clazz)?.newInstance(this)
        printf "ttttt"
        println Logger.info(logLevel, "Factory before load: ${it.value.dump()}")
		factory.load(it.value)
        println Logger.info(logLevel, "Factory after load: ${factory.dump()}")
		factory.create()
	} catch (Exception e) {
		println Utils.printStackTrace(e)
        throw new RuntimeException("JobDSL Exception")
	}
}

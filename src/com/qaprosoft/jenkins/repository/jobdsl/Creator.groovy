package com.qaprosoft.jenkins.repository.jobdsl

// groovy script for initialization and execution all kind of jobdsl factories which are transfered from pipeline scanner script

import groovy.json.*
import javaposse.jobdsl.dsl.jobs.WorkflowJob

def slurper = new JsonSlurper()

String factoryDataMap = readFileFromWorkspace("factories.json")
def prettyPrint = JsonOutput.prettyPrint(factoryDataMap)
println("factoryDataMap: " + prettyPrint)
def factories = new HashMap(slurper.parseText(factoryDataMap))

factories.each{
	def factory = Class.forName(it.value.clazz)?.newInstance(this)
	//println("before load: " + it.value.dump())
	factory.load(it.value)
	//println("factory: " + factory.dump())
	def pipelineJob = factory.create()

    if (pipelineJob instanceof javaposse.jobdsl.dsl.jobs.WorkflowJob) {
        println("PIPELINE DATA DUMP: " + pipelineJob.dump())
    }
}

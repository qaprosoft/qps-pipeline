package com.qaprosoft.jenkins.jobdsl

// groovy script for initialization and execution all kind of jobdsl factories which are transfered from pipeline scanner script

import groovy.json.*

def slurper = new JsonSlurper()

String factoryDataMap = readFileFromWorkspace("factories.json")
def prettyPrint = JsonOutput.prettyPrint(factoryDataMap)
println("factoryDataMap: " + prettyPrint)
def factories = new HashMap(slurper.parseText(factoryDataMap))

factories.each{
	try {
		def factory = Class.forName(it.value.clazz)?.newInstance(this)
		println("before load: " + it.value.dump())
		factory.load(it.value)
		println("factory after load: " + factory.dump())
		factory.create()
	} catch (Exception e) {
		println(e.dump())
	}
}

package com.qaprosoft.jenkins.repository.jobdsl.v2

// groovy script for initialization and execution all kind of jobdsl factories which are transfered from pipeline scanner script

import groovy.json.JsonSlurper
def slurper = new JsonSlurper()

String factoryDataMap = readFileFromWorkspace("factories.json")
println("factoryDataMap: " + factoryDataMap)
def factories = new HashMap(slurper.parseText(factoryDataMap))

factories.each{
	println(it.value)
	def factory = Class.forName(it.value.clazz)?.newInstance(this)
	factory.load(it.value)
	println("factory")
	println(factory.dump())
	factory.create()
}

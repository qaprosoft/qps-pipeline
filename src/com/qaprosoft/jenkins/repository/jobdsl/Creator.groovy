package com.qaprosoft.jenkins.repository.jobdsl

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
		//println("before load: " + it.value.dump())
		factory.load(it.value)
		println("factory: " + factory.dump())
		def job = factory.create()
        println("METHODS: " + job.metaClass.methods*.name.sort().unique())

        Closure closure = parameters {
            booleanParam('my param', true, "I added custom parameter!")
        }

        job.parameters(closure)

	} catch (Exception e) {
		e.printStackTrace()
	}
}




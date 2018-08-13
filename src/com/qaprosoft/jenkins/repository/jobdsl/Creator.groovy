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
        Closure closure = parameters {
            booleanParam('my param', true, "I added custom parameter!")
        }
		def factory = Class.forName(it.value.clazz)?.newInstance(this)
		//println("before load: " + it.value.dump())
		factory.load(it.value)
//		println("factory: " + factory.dump())
		def job = factory.create()
        println("FIELDS: " + job.dump())
//        def methods = job.metaClass.methods*.name.sort().unique()
//        println("METHODS: ")
//        methods.each { method ->
//            println method
//        }
//        job.parameters(closure)
//
//        println("JOB WITH NEW PARAMETER: " + job.dump())

	} catch (Exception e) {
		e.printStackTrace()
	}
}




package com.qaprosoft.jenkins.repository.jobdsl.v2

import groovy.json.JsonSlurper
def slurper = new JsonSlurper()

String factoryDataMap = readFileFromWorkspace("factories.txt")
println("factoryDataMap: " + factoryDataMap)
def factories = new HashMap(slurper.parseText(factoryDataMap))

factories.each{
	println(it.value)
	def factory = Class.forName(it.key)?.newInstance(this)
	factory.init(it.value)
	println("factory")
	println(factory.dump())
	factory.create()
}


//def categorizedViewFactory = this.class.classLoader.loadClass('com.qaprosoft.jenkins.repository.jobdsl.factory.view.CategorizedViewFactory')?.newInstance(this)
//categorizedViewFactory.create('Automation', 'CRON2', 'API|Web')
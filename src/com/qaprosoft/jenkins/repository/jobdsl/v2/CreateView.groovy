package com.qaprosoft.jenkins.repository.jobdsl.v2

println("qwe1")
println(this.dump())

import groovy.json.JsonSlurper

println("qwe2")
println(this.dump())

def slurper = new JsonSlurper()
String factoryDataMap = readFileFromWorkspace("factories.txt")
def factories = new HashMap(slurper.parseText(factoryDataMap))


factories.each{
	println(it.value)
	it.setDsl(this)
	it.create()
}


//def categorizedViewFactory = this.class.classLoader.loadClass('com.qaprosoft.jenkins.repository.jobdsl.factory.view.CategorizedViewFactory')?.newInstance(this)
//categorizedViewFactory.create('Automation', 'CRON2', 'API|Web')
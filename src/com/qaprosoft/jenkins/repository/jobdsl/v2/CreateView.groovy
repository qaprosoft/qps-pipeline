package com.qaprosoft.jenkins.repository.jobdsl.v2

import groovy.json.JsonSlurper

println("this")
println(this.dump())

println("this.binding")
println(this.binding.dump())


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
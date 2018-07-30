package com.qaprosoft.jenkins.repository.jobdsl.v2

import groovy.json.JsonSlurper

println("this")
println(this.dump())

println("this.binding")
println(this.binding.dump())


def listViewFactory = new com.qaprosoft.jenkins.repository.jobdsl.factory.view.ViewFactory(this)
println("listViewFactory")
println(listViewFactory.dump())

def listViewFactory2 = Class.forName("com.qaprosoft.jenkins.repository.jobdsl.factory.view.ViewFactory")?.newInstance(this)
println("listViewFactory2")
println(listViewFactory2.dump())
	
def slurper = new JsonSlurper()
String factoryDataMap = readFileFromWorkspace("factories.txt")
def factories = new HashMap(slurper.parseText(factoryDataMap))


factories.each{
	println(it.value)
	it.setDsl(listViewFactory.getDsl())
	it.create()
}


//def categorizedViewFactory = this.class.classLoader.loadClass('com.qaprosoft.jenkins.repository.jobdsl.factory.view.CategorizedViewFactory')?.newInstance(this)
//categorizedViewFactory.create('Automation', 'CRON2', 'API|Web')
package com.qaprosoft.jenkins.repository.jobdsl.v2

import groovy.json.JsonSlurper
/*
import com.qaprosoft.jenkins.repository.jobdsl.factory.view.ViewType

//TODO: read parameters from pipeline and created file to determine valid factory class and params
String factoryDataMap = readFileFromWorkspace("factory_data.txt")
def slurper = new JsonSlurper()
Map<String, ViewType> factories = new HashMap(slurper.parseText(factoryDataMap))

factories.each{
	ViewType viewData = it.value
	println(it.value)
	def listViewFactory = Class.forName(viewData.factory)?.newInstance(this)
	println("listViewFactory")
	println(listViewFactory.dump())
	listViewFactory.create(viewData.folder, viewData.viewName, viewData.descFilter)
}*/


def slurper = new JsonSlurper()

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
	
String factoryDataMap = readFileFromWorkspace("factories.txt")
println("factoryDataMap: " + factoryDataMap)
def factories = new HashMap(slurper.parseText(factoryDataMap))

factories.each{
	println(it.value)
//	it.setDsl(listViewFactory.getDsl())
	it.value.create()
}


//def categorizedViewFactory = this.class.classLoader.loadClass('com.qaprosoft.jenkins.repository.jobdsl.factory.view.CategorizedViewFactory')?.newInstance(this)
//categorizedViewFactory.create('Automation', 'CRON2', 'API|Web')
package com.qaprosoft.jenkins.repository.jobdsl.v2

import groovy.json.JsonSlurper

println(this.dump())
factoryDataMap = readFileFromWorkspace("factories.txt")
factories = new HashMap(slurper.parseText(factoryDataMap))


factories.each{
	println(it.value)
	it.setDsl(this)
	it.create()
}


//def categorizedViewFactory = this.class.classLoader.loadClass('com.qaprosoft.jenkins.repository.jobdsl.factory.view.CategorizedViewFactory')?.newInstance(this)
//categorizedViewFactory.create('Automation', 'CRON2', 'API|Web')
package com.qaprosoft.jenkins.repository.jobdsl.v2

import groovy.json.JsonSlurper
import com.qaprosoft.jenkins.repository.jobdsl.factory.view.ViewType

//TODO: read parameters from pipeline and created file to determine valid factory class and params
String factoryDataMap = readFileFromWorkspace("views.json")

def slurper = new JsonSlurper()

Map<String, ViewType> factories = new HashMap(slurper.parseText(factoryDataMap))

factories.each{
    ViewType viewData = it.value
    println(it.value)
    def factory = Class.forName(viewData.factory)?.newInstance(this)
    factory.create(viewData.folder, viewData.viewName, viewData.descFilter)
}

//def categorizedViewFactory = this.class.classLoader.loadClass('com.qaprosoft.jenkins.repository.jobdsl.factory.view.CategorizedViewFactory')?.newInstance(this)
//categorizedViewFactory.create('Automation', 'CRON2', 'API|Web')
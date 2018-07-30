package com.qaprosoft.jenkins.repository.jobdsl.v2

import com.qaprosoft.jenkins.repository.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.repository.jobdsl.factory.view.CategorizedViewFactory
import groovy.json.JsonSlurper
import com.qaprosoft.jenkins.repository.jobdsl.factory.view.ViewType

//TODO: read parameters from pipeline and created file to determine valid factory class and params
String factoryDataMap = readFileFromWorkspace("factory_data.txt")

def slurper = new JsonSlurper()
def result = slurper.parseText(factoryDataMap)
Map<String, ViewType> factories = new HashMap(result)

factories.each{
    def listViewFactory = Class.forName(it.factory)?.newInstance(this)
    listViewFactory.create(it.folder, it.viewName, it.descFilter, it.jobNames)
}

//def listViewFactory = Class.forName(cronViewData.factory)?.newInstance(this)
//listViewFactory.create(cronViewData.folder, cronViewData.viewName, cronViewData.descFilter, cronViewData.jobNames)

def categorizedViewFactory = this.class.classLoader.loadClass('com.qaprosoft.jenkins.repository.jobdsl.factory.view.CategorizedViewFactory')?.newInstance(this)
categorizedViewFactory.create('Automation', 'CRON2', 'API|Web', '')
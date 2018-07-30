package com.qaprosoft.jenkins.repository.jobdsl.v2

import com.qaprosoft.jenkins.repository.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.repository.jobdsl.factory.view.CategorizedViewFactory
import groovy.json.JsonSlurper
import com.qaprosoft.jenkins.repository.jobdsl.factory.view.ViewType

//TODO: read parameters from pipeline and created file to determine valid factory class and params
String factoryDataMap = readFileFromWorkspace("factory_data.txt")

def slurper = new JsonSlurper()
def result = slurper.parseText(factoryDataMap)
println(result)
Map<String, ViewType> factories = new Map(result)
println(factories)

def listViewFactory = Class.forName("com.qaprosoft.jenkins.repository.jobdsl.factory.view.ListViewFactory")?.newInstance(this)
listViewFactory.create('Automation', 'CRON', 'cron', '')

def categorizedViewFactory = this.class.classLoader.loadClass('com.qaprosoft.jenkins.repository.jobdsl.factory.view.CategorizedViewFactory')?.newInstance(this)
categorizedViewFactory.create('Automation', 'CRON2', 'API|Web', '')
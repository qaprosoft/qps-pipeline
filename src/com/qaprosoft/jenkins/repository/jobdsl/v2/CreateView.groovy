package com.qaprosoft.jenkins.repository.jobdsl.v2

import com.qaprosoft.jenkins.repository.jobdsl.factory.view.ListViewFactory
import com.qaprosoft.jenkins.repository.jobdsl.factory.view.CategorizedViewFactory


//TODO: read parameters from pipeline and created file to determine valid factory class and params
def factoryDataMap = context.readFileFromWorkspace("factory_data.txt")
context.println(factoryDataMap)

def listViewFactory = Class.forName("com.qaprosoft.jenkins.repository.jobdsl.factory.view.ListViewFactory")?.newInstance(this)
listViewFactory.create('Automation', 'CRON', 'cron', '')

def categorizedViewFactory = this.class.classLoader.loadClass('com.qaprosoft.jenkins.repository.jobdsl.factory.view.CategorizedViewFactory')?.newInstance(this)
categorizedViewFactory.create('Automation', 'CRON2', 'API|Web', '')
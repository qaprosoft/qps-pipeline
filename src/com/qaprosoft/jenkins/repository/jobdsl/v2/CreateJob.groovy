package com.qaprosoft.jenkins.repository.jobdsl.v2

import com.qaprosoft.jenkins.repository.jobdsl.v2.Creator
import com.qaprosoft.jenkins.repository.jobdsl.factory.CreateViewFactory

def creator = new Creator(this)
creator.createJob()

def createViewFactory = new CreateViewFactory(this)
createViewFactory.listView('Automation', 'CRON', 'cron', '')

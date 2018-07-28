package com.qaprosoft.jenkins.repository.jobdsl.v2

import com.qaprosoft.jenkins.repository.jobdsl.factory.ListViewFactory
import com.qaprosoft.jenkins.repository.jobdsl.factory.CategorizedViewFactory


//TODO: read parameters from pipeline and created file to determine valid factory class and params
//def suite_name = context.readFileFromWorkspace("suite_name.txt")

def listViewFactory = new ListViewFactory(this)
listViewFactory.create('Automation', 'CRON', 'cron', '')

package com.qaprosoft.jenkins.repository.jobdsl.v2

import com.qaprosoft.jenkins.repository.jobdsl.v2.Creator
import com.qaprosoft.jenkins.repository.jobdsl.factory.ListViewFactory

def creator = new Creator(this)
creator.createJob()

def listViewFactory = new ListViewFactory(this)
listViewFactory.emptyJob("Empty job", "First factory job")
listViewFactory.jobWithParameter("Job with parameter", "First parametrized job")

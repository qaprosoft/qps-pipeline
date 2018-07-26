package com.qaprosoft.jenkins.repository.jobdsl.v2

import com.qaprosoft.jenkins.repository.jobdsl.v2.Creator
import com.qaprosoft.jenkins.repository.jobdsl.factory.BuildJobFactory
import com.qaprosoft.jenkins.repository.jobdsl.factory.ListViewFactory


def creator = new Creator(this)
creator.createJob()

def buildJobFactory = new BuildJobFactory(this)
buildJobFactory.job("Free Style Job", "Factory job")

def listViewFactory = new ListViewFactory(this)
listViewFactory.listView("MyFolder", "view", "")
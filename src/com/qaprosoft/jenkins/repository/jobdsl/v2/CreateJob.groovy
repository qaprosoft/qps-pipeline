package com.qaprosoft.jenkins.repository.jobdsl.v2

import com.qaprosoft.jenkins.repository.jobdsl.v2.Creator
import com.qaprosoft.jenkins.repository.jobdsl.factory.BuildJobFactory
import com.qaprosoft.jenkins.repository.jobdsl.factory.BuildViewFactory


def creator = new Creator(this)
creator.createJob()

def buildJobFactory = new BuildJobFactory(this)
buildJobFactory.job("Automation/Factory-Generated-Job", "Factory job")

def buildViewFactory = new BuildViewFactory(this)
buildViewFactory.listView("Automation", "listView", "", "Factory-Generated-Job")
buildViewFactory.categorizedView("Automation", "categorizedView", "", "Factory-Generated-Job")

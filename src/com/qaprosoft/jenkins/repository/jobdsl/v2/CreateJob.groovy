package com.qaprosoft.jenkins.repository.jobdsl.v2

import com.qaprosoft.jenkins.repository.jobdsl.v2.Creator
import com.qaprosoft.jenkins.repository.jobdsl.factory.BuildFactory

def creator = new Creator(this)
creator.createJob()

def buildJobFactory = new BuildFactory(this)
buildJobFactory.emptyJob("Empty job", "First factory job")
buildJobFactory.jobWithParameter("Job with parameter", "First parametrized job")

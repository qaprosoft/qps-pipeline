package com.qaprosoft.jenkins.repository.jobdsl.v2

import com.qaprosoft.jenkins.repository.jobdsl.v2.Creator
import com.qaprosoft.jenkins.repository.jobdsl.factory.PipelineFactory

def creator = new Creator(this)
creator.createJob()

def pipelineFactory = new PipelineFactory(this)
pipelineFactory.emptyJob("Empty job", "First factory job")
pipelineFactory.jobWithParameter("Job with parameter", "First parametrized job")

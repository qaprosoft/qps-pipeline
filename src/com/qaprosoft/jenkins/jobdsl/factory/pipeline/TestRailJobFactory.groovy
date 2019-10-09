package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors

@InheritConstructors
public class TestRailJobFactory extends PipelineFactory {

    public TestRailJobFactory(folder, pipelineScript, name, jobDesc) {
        this.folder = folder
        this.description = jobDesc
        this.pipelineScript = pipelineScript
        this.name = name
    }

    def create() {
        logger.info("TestRailJobFactory->create")
        def pipelineJob = super.create()
        pipelineJob.with {
            parameters {
                configure stringParam('uuid', '', "uuid")
                configure booleanParam('isRerun', false)
            }
        }
        return pipelineJob
    }

}

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
                configure stringParam('ci_run_id', '', "Zafira test run id")
                configure booleanParam('isRerun', false, 'isRerun boolean parameter')
            }
        }
        return pipelineJob
    }

}

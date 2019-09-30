package com.qaprosoft.jenkins.jobdsl.factory.pipeline

@Grab('org.testng:testng:6.8.8')
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
                configure stringParam('uuid', '', "uuid of tests")
                configure booleanParam('isRerun', '', "isRerun parameter")
            }
        }
        return pipelineJob
    }

}
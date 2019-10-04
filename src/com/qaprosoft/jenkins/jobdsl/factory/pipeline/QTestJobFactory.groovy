package com.qaprosoft.jenkins.jobdsl.factory.pipeline

@Grab('org.testng:testng:6.8.8')
import groovy.transform.InheritConstructors

@InheritConstructors
public class QTestJobFactory extends PipelineFactory {

    public QTestJobFactory(folder, pipelineScript, name, jobDesc) {
        this.folder = folder
        this.description = jobDesc
        this.pipelineScript = pipelineScript
        this.name = name
    }

    def create() {
        logger.info("QTestJobFactory->create")
        def pipelineJob = super.create()
        pipelineJob.with {
            parameters {
                configure stringParam('uuid', '', "uuid parameter")
                configure addHiddenParameter('qtest_enabled', 'qtest_enabled boolean parameter', 'true')
            }
        }
        return pipelineJob
    }

}
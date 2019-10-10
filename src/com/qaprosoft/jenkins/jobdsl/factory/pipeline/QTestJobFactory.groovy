package com.qaprosoft.jenkins.jobdsl.factory.pipeline

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
                configure stringParam('ci_run_id', '', "Zafira test run id")
                configure stringParam('os', '', 'OS name')
                configure stringParam('os_version', '', 'OS version')
                configure stringParam('browser', '', 'Browser name')
            }
        }
        return pipelineJob
    }

}

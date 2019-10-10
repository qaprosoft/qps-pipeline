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
                configure stringParam('testrail_milestone', '', 'testrail_milestone parameter')
                configure stringParam('testrail_run_name', '', 'testrail run name')
                configure stringParam('testrail_assignee', '', 'testrail_assignee parameter')
                configure stringParam('testrail_search_interval', '', 'testrail_search_interval parameter')
                configure booleanParam('include_all', false, 'include_all parameter')
                configure booleanParam('isRerun', false, 'isRerun boolean parameter')
            }
        }
        return pipelineJob
    }

}

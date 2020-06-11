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
                configure stringParam('ci_run_id', '', "Reporting test run ci_run_id")
                configure booleanParam('include_all', false, 'Register all cases from suite into the run if true or only executed for false')
                configure stringParam('milestone', '', 'Milestone name if needed')
                configure stringParam('run_name', '', 'Custom or existing TestRail run name')
                configure booleanParam('run_exists', false, 'Check if you would like to sync results into existing TestRail run')
                configure stringParam('assignee', '', 'Email of the TestRail user to assign for registered tests')
                configure stringParam('search_interval', '7', 'Interval in days to look for existing test run. Default: 7')
            }
        }
        return pipelineJob
    }

}

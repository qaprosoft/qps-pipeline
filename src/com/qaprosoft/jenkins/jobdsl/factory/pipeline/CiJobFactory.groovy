package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors

@InheritConstructors
public class CiJobFactory extends PipelineFactory {

    public CiJobFactory(folder, pipelineScript, jobName, jobDesc) {
        this.folder = folder
		this.pipelineScript = pipelineScript
		this.name = jobName
        this.description = jobDesc
    }

    def create() {

        def pipelineJob = super.create()

        pipelineJob.with {

			//TODO: think about other parameters to support DevOps CI operations
            parameters {
				stringParam('project', this.name, 'Your GitHub repository for scanning')
				//TODO: analyze howto support several gc_GIT_BRACH basing on project
				configure addExtensibleChoice('branch', "gc_GIT_BRANCH", "Select a GitHub Testing Repository Branch to run against", "master")
				booleanParam('onlyUpdated', true, '	If chosen, scan will be performed only in case of any change in *.xml suites.')
				
				choiceParam('removedConfigFilesAction', ['IGNORE', 'DELETE'], '')
				choiceParam('removedJobAction', ['IGNORE', 'DELETE'], '')
				choiceParam('removedViewAction', ['IGNORE', 'DELETE'], '')
            }

        }
        return pipelineJob
    }

}
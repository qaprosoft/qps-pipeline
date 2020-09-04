package com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook

import groovy.transform.InheritConstructors
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.PipelineFactory

@InheritConstructors
public class ScmHookJobFactory extends PipelineFactory {

	def create() {
		def pipelineJob = super.create()
		pipelineJob.with {
			properties([
			  pipelineTriggers([
			   [$class: 'GenericTrigger',
				genericVariables: [
				 [key: 'ref', value: '$.ref'],
				],

				causeString: 'Triggered on $ref',

				printContributedVariables: true,
				printPostContent: true,

				silentResponse: false,

				regexpFilterText: '$ref',
				regexpFilterExpression: 'refs/heads/master'
			   ]
			  ])
			])
		}

		return pipelineJob
	}

	protected def getGitHubAuthId(project) {
		return "https://api.github.com : ${project}-token"
	}
}
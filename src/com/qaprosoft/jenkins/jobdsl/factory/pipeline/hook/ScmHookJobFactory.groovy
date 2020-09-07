package com.qaprosoft.jenkins.jobdsl.factory.pipeline.hook

import groovy.transform.InheritConstructors
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.PipelineFactory

@InheritConstructors
class ScmHookJobFactory extends PipelineFactory {

	ScmHookJobFactory(folder, pipelineScript, jobName, jobDesc) {
		this.folder = folder
		this.pipelineScript = pipelineScript
		this.name = jobName
		this.description = description
	}

	def create() {
		def pipelineJob = super.create()
		pipelineJob.with {

			concurrentBuild(true)

			parameters {
				configure addHiddenParameter('ref', '', '')
			}

			triggers {
			  genericTrigger {
			   genericVariables {
			    genericVariable {
			     key("ref")
			     value("\$.ref")
			     expressionType("JSONPath") //Optional, defaults to JSONPath
			     regexpFilter("") //Optional, defaults to empty string
			     defaultValue("") //Optional, defaults to empty string
			    }
			   }
			   // genericRequestVariables {
			   //  genericRequestVariable {
			   //   key("requestParameterName")
			   //   regexpFilter("")
			   //  }
			   // }
			   // genericHeaderVariables {
			   //  genericHeaderVariable {
			   //   key("requestHeaderName")
			   //   regexpFilter("")
			   //  }
			   // }
			   token('abc123')
			   printContributedVariables(true)
			   printPostContent(true)
			   silentResponse(false)
			   regexpFilterText("\$.ref")
			   regexpFilterExpression("ref")
			  }
			}

			definition {
                cps {
                    script(pipelineScript)
                    sandbox()
                }
            }
		}

		return pipelineJob
	}

	protected def getGitHubAuthId(project) {
		return "https://api.github.com : ${project}-token"
	}
}
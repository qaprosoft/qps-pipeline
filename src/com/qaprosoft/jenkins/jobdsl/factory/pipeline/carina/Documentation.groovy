package com.qaprosoft.jenkins.jobdsl.factory.pipeline.carina

import com.qaprosoft.jenkins.jobdsl.factory.DslFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class Documentation extends DslFactory {

	public Documentation(folder, jobName, jobDesc) {
		this.folder = folder
		this.name = jobName
		this.description = jobDesc
	}

	def create() {
        _dslFactory.pipelineJob(name) {
			keepDependencies(true)
            jdk('(System)')
            scm {
                git {
                    remote {
                        url("git@github.com:qaprosoft/carina.git")
                        credentials("creds")
                    }
                    extensions {
                        submoduleOptions {
                            disable()
                        }
                        cleanAfterCheckout()
                    }
                }
            }
            concurrentBuild(false)
            steps {
                shell('mkdocs gh-deploy')
            }
            publishers {
                mailer('hursevich@gmail.com', false, false)
            }
		}
		return pipelineJob
	}

	protected def getOrganization() {
		return 'qaprosoft'
	}

	protected def getGitHubAuthId(project) {
		//TODO: get API GitHub URL from binding
		return "https://api.github.com : ${project}-token"
	}
}
package com.qaprosoft.jenkins.jobdsl.factory.pipeline.scm

import groovy.transform.InheritConstructors
import com.qaprosoft.jenkins.jobdsl.factory.pipeline.PipelineFactory

@InheritConstructors
class ScmJobFactory extends PipelineFactory {
    
    def scmHost
    def scmOrg
    def scmRepo
    def scmBranch
    def scmUrl

    ScmJobFactory(folder, name, description) {
        super(folder, name, description)
    }

    ScmJobFactory(foler, name, description, pipelineScript, scmHost, scmOrg, scmRepo, scmBranch, scmUrl='') {
        this.folder = folder
        this.name = name
        this.description = description
        this.scmHost = scmHost
        this.scmOrg = scmOrg
        this.scmRepo = scmRepo
        this.scmBranch = scmbBanch
        this.scmUrl = scmUrl
        this.pipelineScript = pipelineScript
    }

    def create() {
        def pipelineScmJob = super.create()

        pipelineScmJob.with {
            parameters {
                configure addHiddenParameter('SCM_HOST', '', scmHost)
                configure addHiddenParameter('SCM_ORG', '', scmOrg)
                configure addHiddenParameter('SCM_REPO', '', scmRepo)
                configure addHiddenParameter('SCM_BRANCH', '', scmBranch)
                configure addHiddenParameter('SCM_URL', '', scmUrl)
            }
        }
        return pipelineScmJob
    }
}
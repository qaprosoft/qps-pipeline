package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import static com.qaprosoft.jenkins.Utils.*
import org.testng.xml.XmlSuite
import groovy.transform.InheritConstructors

@InheritConstructors
class RegisterRepositoryJobFactory extends PipelineFactory {

    RegisterRepositoryJobFactory(folder, name, jobDesc) {
        super(foler, name, description)
    }

    def create() {
        logger.info("RegisterRepositoryJobFactory->create")
        def pipelineJob = super.create()
        def repo = ""
        if ("qaprosoft".equals(this.folder) || "".equals(this.folder)) {
            repo = "carina-demo"
        }
        def org = "qaprosoft"
        if (!this.folder.isEmpty()) {
            org = this.folder
        }

        pipelineJob.with {
            parameters {
                configure stringParam('scmHost', 'github.com', 'Source Control Management host')
                configure stringParam('scmOrg', org, 'Source Control Management organization')
                configure stringParam('scmRepo', repo, 'Repository for scanning')
                configure stringParam('scmBranch', 'master', 'SCM repository branch to run against')
                configure stringParam('scmUser', '', 'SCM user')
                configure stringParam('scmToken', '', 'CSM token with read permissions')
                
                configure addExtensibleChoice('pipelineLibrary', "gc_PIPELINE_LIBRARY", "Groovy JobDSL/Pipeline library, for example: https://github.com/qaprosoft/qps-pipeline/releases", "QPS-Pipeline")
                configure addExtensibleChoice('runnerClass', "gc_RUNNER_CLASS", "Pipeline runner class", "com.qaprosoft.jenkins.pipeline.runner.maven.TestNG")
                
                configure addHiddenParameter('zafiraFields', '', '')
                configure addHiddenParameter('userId', '', '2')
            }
        }
        return pipelineJob
    }

    //This method is needed for inserting pipeline script into an appropriate field
    String getPipelineScript() {
        return "@Library(\'QPS-Pipeline\')\nimport com.qaprosoft.jenkins.pipeline.Repository;\nnew Repository(this).register()"
    }
}
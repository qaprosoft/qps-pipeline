package com.qaprosoft.jenkins.jobdsl.factory.pipeline

@Grab('org.testng:testng:6.8.8')

import static com.qaprosoft.jenkins.Utils.*
import org.testng.xml.XmlSuite
import com.qaprosoft.jenkins.jobdsl.selenium.grid.ProxyInfo
import groovy.transform.InheritConstructors

@InheritConstructors
public class RegisterRepositoryJobFactory extends PipelineFactory {

    def pipelineLibrary
    def runnerClass

    public RegisterRepositoryJobFactory(folder, name, jobDesc, pipelineLibrary, runnerClass) {
        this.folder = folder
        this.name = name
        this.description = jobDesc
        this.pipelineLibrary = pipelineLibrary
        this.runnerClass = runnerClass
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
                configure stringParam('organization', org, 'GitHub organization')
                configure stringParam('repo', repo, 'GitHub repository for scanning')
                configure stringParam('branch', 'master', 'It is highly recommended to use master branch for each scan operation')
                configure stringParam('githubUser', '', 'GitHub user')
                configure stringParam('githubToken', '', 'GitHub token with read permissions')
                configure stringParam('pipelineLibrary', this.pipelineLibrary, 'Groovy JobDSL/Pipeline library, for example: https://github.com/qaprosoft/qps-pipeline/releases')
                configure stringParam('runnerClass', this.runnerClass, '')
                configure addHiddenParameter('zafiraFields', '', '')
                configure addHiddenParameter('userId', '', '2')
            }
        }
        return pipelineJob
    }

    String getPipelineScript() {
        return "@Library(\'QPS-Pipeline\')\nimport com.qaprosoft.jenkins.pipeline.Repository;\nnew Repository(this).register()"
    }

}
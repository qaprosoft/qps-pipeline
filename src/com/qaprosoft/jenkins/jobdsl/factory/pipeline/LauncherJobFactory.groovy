package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import com.qaprosoft.jenkins.jobdsl.selenium.grid.ProxyInfo
import groovy.transform.InheritConstructors
import org.testng.xml.XmlSuite

@Grab('org.testng:testng:6.8.8')

import static com.qaprosoft.jenkins.Utils.*

@InheritConstructors
public class LauncherJobFactory extends PipelineFactory {

    public LauncherJobFactory(folder, pipelineScript, name, jobDesc) {
        this.folder = folder
        this.description = jobDesc
        this.pipelineScript = pipelineScript
        this.name = name
    }

    def create() {
        logger.info("LauncherJobFactory->create")
        def pipelineJob = super.create()
        pipelineJob.with {
            parameters {
                configure addExtensibleChoice('branch', "gc_GIT_BRANCH", "GitHub repository branch to run against", "master")
                configure stringParam('suite', 'api', "TestNG suite file name (without \".xml\" extension)")
                configure stringParam('overrideFields', 'platform=API,thread_count=5' , "Custom parameters to run job with")
                configure addHiddenParameter('scmURL', '' , "GitHub repository https URL with token (read permissions only is enough)")
                configure addExtensibleChoice('ci_run_id', '', 'import static java.util.UUID.randomUUID\nreturn [randomUUID()]')
                configure addHiddenParameter('queue_registration', '', "false")
            }
        }
        return pipelineJob
    }

}
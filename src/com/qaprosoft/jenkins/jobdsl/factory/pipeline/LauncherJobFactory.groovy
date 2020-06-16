package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors
import org.testng.xml.XmlSuite

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
                configure stringParam('branch', 'master', "SCM repository branch to run against")
                configure stringParam('suite', 'api', "TestNG suite file name (without \".xml\" extension)")
                configure stringParam('zafiraFields', 'platform=API,thread_count=5', "Custom parameters to run job with")
                configure addHiddenParameter('scmURL', '', "GitHub repository https URL with token (read permissions only is enough)")
                configure addExtensibleChoice('ci_run_id', '', 'import static java.util.UUID.randomUUID\nreturn [randomUUID()]')
                configure addHiddenParameter('queue_registration', '', "false")
                configure addHiddenParameter('rerun_failures', '', "false")
            }
        }
        return pipelineJob
    }

}
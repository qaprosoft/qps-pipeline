package com.qaprosoft.jenkins.repository.jobdsl.factory.pipeline

import org.testng.xml.Parser
import org.testng.xml.XmlSuite

public class CronJobFactory extends PipelineFactory {

    protected String runCronPipelineScript = "@Library('QPS-Pipeline')\nimport com.qaprosoft.jenkins.repository.pipeline.v2.Runner;\nnew Runner(this).runCron()"

    def project
    def sub_project
    def suitePath

    public CronJobFactory(folder, cronJobName, project, sub_project, suitePath) {

        this.folder = folder
        this.name = cronJobName
        this.project = project
        this.sub_project = sub_project
        this.suitePath = suitePath
    }

    def create() {

        def xmlFile = new Parser(suitePath)
        xmlFile.setLoadClasses(false)

        List<XmlSuite> suiteXml = xmlFile.parseToList()
        XmlSuite currentSuite = suiteXml.get(0)

        def pipelineJob = super.create()

        pipelineJob.with {

            description("project: ${project}; type: cron")
            logRotator { numToKeep 100 }

            authenticationToken('ciStart')

            parameters {
                choiceParam('env', getEnvironments(currentSuite), 'Environment to test against.')
                configure addHiddenParameter('project', '', project)
                configure addHiddenParameter('sub_project', '', sub_project)
                configure addHiddenParameter('ci_parent_url', '', '')
                configure addHiddenParameter('ci_parent_build', '', '')

                configure addExtensibleChoice('branch', "gc_GIT_BRANCH", "Select a GitHub Testing Repository Branch to run against", "master")

                stringParam('email_list', '', 'List of Users to be emailed after the test. If empty then populate from jenkinsEmail suite property')
                configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "3")
                choiceParam('retry_count', [0, 1, 2, 3], 'Number of Times to Retry a Failed Test')
            }
            definition {
                cps {
                    script(runCronPipelineScript)
                    sandbox()
                }
            }
        }
    }

    protected List<String> getEnvironments(currentSuite) {
        def envList = getGenericSplit(currentSuite, "jenkinsEnvironments")

        if (envList.isEmpty()) {
            envList.add("DEMO")
            envList.add("STAG")
        }

        return envList
    }

}
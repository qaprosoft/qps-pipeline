package com.qaprosoft.jenkins.jobdsl.factory.pipeline

@Grab('org.testng:testng:6.8.8')

import org.testng.xml.XmlSuite
import groovy.transform.InheritConstructors

import static com.qaprosoft.jenkins.Utils.*

@InheritConstructors
public class CronJobFactory extends ScmJobFactory {

    def suitePath
    def scheduling
    def orgRepoScheduling

    public CronJobFactory(folder, cronJobName, jobDesc, pipelineScript, scmHost, scmOrg, scmRepo, scmBranch, scmUrl, suitePath, orgRepoScheduling) {
        super(folder, cronJobName, jobDesc, pipelineScript, scmHost, scmOrg, scmRepo, scmBranch, scmUrl)
        this.suitePath = suitePath
        this.orgRepoScheduling = orgRepoScheduling
    }

    def create() {
        logger.info("CronJobFactory->create")
        XmlSuite currentSuite = parseSuite(suitePath)
        def pipelineJob = super.create()

        pipelineJob.with {
            //** Properties & Parameters Area **//*
            if (scheduling != null && orgRepoScheduling) {
                triggers {
                    cron(parseSheduling(scheduling))
                }
            }
            parameters {
                extensibleChoiceParameterDefinition {
                    name('env')
                    choiceListProvider {
                        textareaChoiceListProvider {
                            choiceListText(getEnvironments(currentSuite))
                            defaultChoice(getDefaultChoiceValue(currentSuite))
                            addEditedValue(false)
                            whenToAdd('Triggered')
                        }
                    }
                    editable(true)
                    description('Environment to test against')
                }

                configure stringParam('branch', this.branch, "SCM repository branch to run against")
                configure stringParam('email_list', '', 'List of Users to be emailed after the test. If empty then populate from jenkinsEmail suite property')
                configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "5")

                configure addHiddenParameter('ci_parent_url', '', '')
                configure addHiddenParameter('ci_parent_build', '', '')
                configure addHiddenParameter('zafiraFields', '', '')
            }

        }
        return pipelineJob
    }

    def setScheduling(scheduling) {
        this.scheduling = scheduling
    }

}

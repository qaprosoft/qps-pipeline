package com.qaprosoft.jenkins.pipeline.sbt

import com.qaprosoft.Utils
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.AbstractRunner
import groovy.transform.InheritConstructors


@InheritConstructors
class SBTRunner extends AbstractRunner {

    public SBTRunner(context) {
        super(context)
        scmClient = new GitHub(context)
    }

    @Override
    public void build() {
        logger.info("SBTRunner->runJob")
        context.node("performance") {

            context.wrap([$class: 'BuildUser']) {
                try {
                    context.timestamps {

                        context.env.getEnvironment()

                        scmClient.clone()

                        def sbtHome = context.tool 'SBT'

                        def args = Configuration.get("args")

                        context.timeout(time: Integer.valueOf(Configuration.get(Configuration.Parameter.JOB_MAX_RUN_TIME)), unit: 'MINUTES') {
                            context.sh "${sbtHome}/bin/sbt ${args}"
                        }

                    }
                } catch (Exception e) {
                    logger.error(Utils.printStackTrace(e))
                    throw e
                } finally {
                    publishJenkinsReports()
                    clean()
                }
            }
        }
    }

    @Override
    public void onPush(){
        //TODO: implement in future
    }

    @Override
    public void onPullRequest(){
        //TODO: implement in future
    }

    protected void publishJenkinsReports() {
        context.stage('Results') {
            context.gatlingArchive()
            context.archiveArtifacts 'target/gatling/*/'
            context.s3CopyArtifact context.buildSelector: lastCompleted(), context.excludeFilter: '', context.filter: '*', context.flatten: false, context.optional: false, context.projectName: 'loadTesting/Gatling-load-testing', context.target: 'jenkins-mobile-artifacts/loadTestingReports'
        }
    }

    protected clean() {
        context.stage('Wipe out Workspace') {
            context.deleteDir()
        }
    }

}

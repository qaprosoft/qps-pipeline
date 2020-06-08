package com.qaprosoft.jenkins.pipeline.runner.sbt

import com.qaprosoft.jenkins.Utils
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.runner.sbt.AbstractSBTRunner
import groovy.transform.InheritConstructors


@InheritConstructors
class SBTRunner extends AbstractSBTRunner {

    public SBTRunner(context) {
        super(context)
    }

    @Override
    public void build() {
        logger.info("SBTRunner->runJob")
        context.node("performance") {

            context.wrap([$class: 'BuildUser']) {
                try {
                    context.timestamps {

                        context.env.getEnvironment()

                        getScm().clone()

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
                    uploadResultsToS3()
                    publishResultsInSlack()
                }
            }
        }
    }

    @Override
    public void onPush() {
        //TODO: implement in future
    }

    @Override
    public void onPullRequest() {
        //TODO: implement in future
    }

    protected void publishJenkinsReports() {
        def mails = Configuration.get("mails").toString()
        context.stage('Results') {
            context.gatlingArchive()
            context.zip archive: true, dir: 'target/gatling/', glob: '', zipFile: randomArchiveName
            context.archiveArtifacts 'src/test/resources/user.csv'
            context.emailext body: 'Test Text', subject: 'Test', to: mails

        }
    }


    protected void uploadResultsToS3() {
        def needToUpload = Configuration.get("needToUpload")?.toBoolean()
        if (needToUpload) {
            context.build job: 'loadTesting/Upload-Results-To-S3', wait: false
        }
    }

    protected void publishResultsInSlack() {
        def publish = Configuration.get("publishInSlack")?.toBoolean()
        if (publish) {
            context.build job: 'loadTesting/Publish-Results-To-Slack', wait: false
        }
    }
}
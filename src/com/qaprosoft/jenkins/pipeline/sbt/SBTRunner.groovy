package com.qaprosoft.jenkins.pipeline.sbt

import com.qaprosoft.Utils
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.AbstractRunner
import java.util.Date
import groovy.transform.InheritConstructors
import java.text.SimpleDateFormat


@InheritConstructors
class SBTRunner extends AbstractRunner {


    def date = new Date()
    def sdf = new SimpleDateFormat("yyyyMMddHHmmss")
    String curDate = sdf.format(date)
    String randomArchiveName = "loadTestingReports" + curDate + ".zip"

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
                    uploadResultsToS3()
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
            //    context.archiveArtifacts 'target/gatling/*/'
            context.zip archive: true, dir: 'target/gatling/', glob: '', zipFile: randomArchiveName
            //    context.s3CopyArtifact buildSelector: context.lastCompleted(), excludeFilter: '', filter: '*.zip', flatten: false, optional: false, projectName: 'loadTesting/Gatling-load-testing', target: 'jenkins-mobile-artifacts'
         //   context.emailext to: "${mails}"
            context.mail bcc: '', body: '''$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS:

Check console output at $BUILD_URL to view the results.''', cc: '', from: 'cloud@gmail.com', replyTo: '', subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS!', to: mails
        }
    }

    protected void clean() {
        context.stage('Wipe out Workspace') {
            context.deleteDir()
        }
    }

    protected void uploadResultsToS3() {
        def needToUpload = Configuration.get("needToUpload").toString().toBoolean()
        if (needToUpload) {
            context.build job: 'loadTesting/Upload-Results-To-S3'
        }
    }

}

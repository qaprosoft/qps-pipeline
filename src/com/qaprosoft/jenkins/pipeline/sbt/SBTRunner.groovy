package com.qaprosoft.jenkins.pipeline.sbt

import com.qaprosoft.Utils
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.AbstractRunner
import groovy.transform.InheritConstructors


@InheritConstructors
class SBTRunner extends AbstractRunner {

    int randomStringLength = 10
    String charset = (('a'..'z') + ('A'..'Z') + ('0'..'9')).join()
    String randomArchiveName = "happypathload"+RandomStringUtils.random(randomStringLength, charset.toCharArray())

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
        //    context.archiveArtifacts 'target/gatling/*/'
            context.zip archive: true, dir: 'target/gatling/*/', glob: '', zipFile: randomArchiveName
            context.s3CopyArtifact buildSelector: context.lastCompleted(), excludeFilter: '', filter: '*', flatten: false, optional: false, projectName: 'loadTesting/Gatling-load-testing', target: 'jenkins-mobile-artifacts/loadTestingReports'
        }
    }

    protected clean() {
        context.stage('Wipe out Workspace') {
            context.deleteDir()
        }
    }

}

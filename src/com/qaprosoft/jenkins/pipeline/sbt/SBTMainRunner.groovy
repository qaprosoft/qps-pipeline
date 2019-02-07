package com.qaprosoft.jenkins.pipeline.sbt

import com.qaprosoft.Utils
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.AbstractRunner
import java.util.Date
import groovy.transform.InheritConstructors
import java.text.SimpleDateFormat


@InheritConstructors
class SBTMainRunner extends AbstractRunner {


    def date = new Date()
    def sdf = new SimpleDateFormat("yyyyMMddHHmmss")
    String curDate=sdf.format(date)
    String randomArchiveName = "loadTestingReports" + curDate +".zip"

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

                        context.copyArtifacts filter: '*.zip', fingerprintArtifacts: true, projectName: 'loadTesting/Gatling-load-testing', selector: lastCompleted(), target: 'target/gatling'

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

    protected clean() {
        context.stage('Wipe out Workspace') {
            context.deleteDir()
        }
    }

}

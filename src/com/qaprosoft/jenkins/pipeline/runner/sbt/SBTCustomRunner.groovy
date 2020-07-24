package com.qaprosoft.jenkins.pipeline.runner.sbt

import com.qaprosoft.jenkins.Utils
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner
import java.util.Date
import groovy.transform.InheritConstructors
import java.text.SimpleDateFormat


@InheritConstructors
class SBTCustomRunner extends AbstractRunner {

    def date = new Date()
    def sdf = new SimpleDateFormat("yyyyMMddHHmmss")
    String curDate = sdf.format(date)
    String randomCompareArchiveName = "loadTestingReports" + curDate + ".zip"

    public SBTCustomRunner(context) {
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
                    publishResultsInSlack()
                    clean()
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
        context.stage('Results') {
            context.zip archive: true, dir: 'comparasionReports', glob: '', zipFile: randomCompareArchiveName
        }
    }


    protected void publishResultsInSlack() {
        def publish = Configuration.get("publishInSlack")?.toBoolean()
        if (publish) {
            context.build job: 'loadTesting/Publish-Compare-Report-Results-To-Slack', wait: false
        }
    }

}

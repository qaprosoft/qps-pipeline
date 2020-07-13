package com.qaprosoft.jenkins.pipeline.runner.sbt

import com.qaprosoft.jenkins.Utils
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.runner.sbt.AbstractSBTRunner
import groovy.transform.InheritConstructors


@InheritConstructors
class SBTSimpleRunner extends AbstractSBTRunner {

    public SBTSimpleRunner(context) {
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
}
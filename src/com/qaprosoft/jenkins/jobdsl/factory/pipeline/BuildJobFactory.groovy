package com.qaprosoft.jenkins.jobdsl.factory.pipeline

import groovy.transform.InheritConstructors
import com.qparosoft.jenkins.jobdsl.factory.pipeline.scm.ScmJobFactory

@InheritConstructors
class BuildJobFactory extends ScmJobFactory {

    def buildTool
    def isDockerRepo

    BuildJobFactory(folder, jobName, jobDesc, pipelineScript, scmHost, scmOrg, scmRepo, scmBranch, scmUrl, buildTool, isDockerRepo=false) {
        super(folder, jobName, jobDesc, pipelineScript, scmHost, scmOrg, scmRepo, scmBranch, scmUrl)
        this.buildTool = buildTool
        this.isDockerRepo = isDockerRepo
    }

    def create() {
        logger.info("BuildJobFactory->create")

        def pipelineJob = super.create()

        pipelineJob.with {

            parameters {

                // dockerBuild params
                if (isDockerRepo) {
                    configure stringParam('release_version', '', 'SemVer-compliant upcoming release or RC version (e.g. 1.13.1 or 1.13.1.RC1)')
                    configure stringParam('dockerfile', 'Dockerfile', 'Relative path to your dockerfile')
                    configure addHiddenParameter('build_tool', '', "${this.buildTool}")
                }

                switch (buildTool.toLowerCase()) {
                    case "maven":
                        configure stringParam('maven_goals', '-U clean install', 'Maven goals to build the project')
                        break
                    case "gradle":
                        configure stringParam('gradle_tasks', 'clean build', 'Gradle tasks to build the project')
                        break
                }

                configure booleanParam('fork', false, "Reuse forked repository for ${repo}.")
                configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "3")
                configure stringParam('email_list', "", 'List of Users to be emailed after the build')
            }

        }

        return pipelineJob
    }
}

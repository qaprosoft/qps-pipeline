package com.qaprosoft.jenkins.pipeline.carina

import com.qaprosoft.jenkins.pipeline.Executor
import com.qaprosoft.scm.ISCM
import com.qaprosoft.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.Configuration

class CarinaRunner {

    protected def context
    protected ISCM scmClient
    protected Configuration configuration = new Configuration(context)

    public CarinaRunner(context) {
        this.context = context
        scmClient = new GitHub(context)
    }

    public void onPush() {
        context.node("docs") {
            context.println("CarinaRunner->onPush")
            scmClient.clonePush()
            if(Executor.isUpdated(context.currentBuild, "**.md")){
                context.sh 'mkdocs gh-deploy'
            }
            def releaseName = "${context.env.getEnvironment().get("CARINA_RELEASE")}.${context.env.getEnvironment().get("BUILD_NUMBER")}-SNAPSHOT"

            executeMavenGoals("versions:set -DnewVersion=${releaseName}")
            executeMavenGoals("-Dcobertura.report.format=xml cobertura:cobertura clean deploy javadoc:javadoc")

            def body = "New CARINA build ${releaseName} is available."
            def subject = "CARINA ${releaseName}"
            def to = "itsvirko@qaprosoft.com"

            context.emailext Executor.getEmailParams(body, subject, to)
        }
    }

    protected def executeMavenGoals(goals) {
        if (context.isUnix()) {
            context.sh "mvn -B ${goals}"
        } else {
            context.bat "mvn -B ${goals}"
        }
    }


}

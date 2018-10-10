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
        context.node("maven") {
            context.println("CarinaRunner->onPush")
            def releaseName = "${context.env.getEnvironment().get("CARINA_RELEASE")}.${context.env.getEnvironment().get("BUILD_NUMBER")}-SNAPSHOT"
            def body = "CARINA build ${releaseName} "
            def subject = "CARINA ${releaseName} "
            def to = "itsvirko@qaprosoft.com"
            try {
                scmClient.clonePush()
                context.stage('Build Snapshot') {
                    executeMavenGoals("versions:set -DanewVersion=${releaseName}")
                }
                subject = subject + "is available."
                body = body + "is available."
                deployDocumentation()
            } catch (Exception e) {
                printStackTrace(e)
                subject = subject + "failed."
                body = body + "failed.<br>${e.getMessage()}<br>${e.getClass().getName()}<br>${Arrays.toString(e.getStackTrace())}"
                throw e
            } finally {
                context.emailext Executor.getEmailParams(body, subject, to)
                reportingBuildResults()
                clean()
            }
        }
    }

    protected def reportingBuildResults() {
        context.stage('Report Results') {
            executeMavenGoals("-Dcobertura.report.format=xml cobertura:cobertura clean deploy javadoc:javadoc")
            context.junit testResults: "**/target/surefire-reports/junitreports/*.xml", healthScaleFactor: 1.0
        }
    }

    protected def deployDocumentation(){
        if(Executor.isUpdated(context.currentBuild, "**.md")){
            context.stage('Deploy Documentation'){
                context.sh 'mkdocs gh-deploy'
            }
        }
    }

    protected clean() {
        context.stage('Wipe out Workspace') {
            context.deleteDir()
        }
    }

    protected def executeMavenGoals(goals) {
        if (context.isUnix()) {
            context.sh "mvn -B ${goals}"
        } else {
            context.bat "mvn -B ${goals}"
        }
    }

    protected void printStackTrace(Exception e) {
        context.println("exception: " + e.getMessage())
        context.println("exception class: " + e.getClass().getName())
        context.println("stacktrace: " + Arrays.toString(e.getStackTrace()))
    }
}

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
            //context.deleteDir()
            context.println "CARINA_RELEASE: " + context.env.getEnvironment().get("CARINA_RELEASE")
            executeMavenGoals("versions:set -DnewVersion=${context.env.getEnvironment().get("CARINA_RELEASE")}.${context.env.getEnvironment().get("BUILD_NUMBER")}-SNAPSHOT")
            executeMavenGoals("-Dcobertura.report.format=xml cobertura:cobertura clean deploy javadoc:javadoc")
            /*                context.withCredentials([context.usernamePassword(credentialsId:'gpg_token', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
                            context.echo "USERNAME: ${context.env.USERNAME}"
                            context.echo "PASSWORD: ${context.env.PASSWORD}"
                            executeMavenGoals("-Dgpg.passphrase=${context.env.PASSWORD} -Dcobertura.report.format=xml cobertura:cobertura clean deploy javadoc:javadoc")
                        }
        */
            // handle each push/merge operation
            // execute logic inside this method only if $REPO_HOME/Jenkinsfile was updated
            context.println("TODO: implement snapshot build generation and emailing build number...")
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

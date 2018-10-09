package com.qaprosoft.jenkins.pipeline.carina

import com.qaprosoft.jenkins.pipeline.Executor
import com.qaprosoft.scm.ISCM
import com.qaprosoft.scm.github.GitHub
import groovy.transform.InheritConstructors

@InheritConstructors
class CarinaRunner {

    protected def context
    protected ISCM scmClient

    public CarinaRunner(context) {
        this.context = context
        scmClient = new GitHub(context)
    }

    public void onPush() {
        context.println("CarinaRunner->onPush")
        scmClient.clone(false)
        if(Executor.isUpdated(context.currentBuild, "**.md")){
            context.node("docs") {
                generateDocumentation()
            }
        }
        // handle each push/merge operation
        // execute logic inside this method only if $REPO_HOME/Jenkinsfile was updated
        context.println("TODO: implement snapshot build generation and emailing build number...")
    }

    public def generateDocumentation() {
        context.sh 'mkdocs gh-deploy'
    }
}

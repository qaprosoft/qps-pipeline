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
                context.sh 'mkdocs gh-deploy --clean'
//                context.sh 'git update-ref -d refs/remotes/origin/gh-pages'
            }
            context.deleteDir()
            // handle each push/merge operation
            // execute logic inside this method only if $REPO_HOME/Jenkinsfile was updated
            context.println("TODO: implement snapshot build generation and emailing build number...")
        }
    }
}

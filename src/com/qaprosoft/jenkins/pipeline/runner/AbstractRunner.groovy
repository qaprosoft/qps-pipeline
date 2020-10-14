package com.qaprosoft.jenkins.pipeline.runner

import com.qaprosoft.jenkins.BaseObject
import com.qaprosoft.jenkins.pipeline.integration.sonar.SonarClient
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.tools.scm.gitlab.Gitlab
import com.qaprosoft.jenkins.pipeline.tools.scm.bitbucket.BitBucket
import java.nio.file.Paths

import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

public abstract class AbstractRunner extends BaseObject {
    SonarClient sc
    
    // organization folder name of the current job/runner
    protected String organization = ""

    public AbstractRunner(context) {
        super(context)

        def host = Configration.get("scmHost")
        def org = Configration.get("scmOrg")
        def repo = Configration.get("repo")
        def branch = Configration.get("branch")

        switch (host) {
            case 'github':
                this.setScm(new GitHub(context, host, org, repo, branch))
                break
            case 'gitlab':
                this.setScm(new Gitlab(context, host, org, repo, branch))
                break
            case 'bitbucket':
                this.setScm(new BitBucket(context, host, org, repo, branch))
        }

        initOrganization()
        setDisplayNameTemplate('#${BUILD_NUMBER}|${branch}')
    }

    //Methods
    abstract public void build()

    //Events
    abstract public void onPush()

    abstract public void onPullRequest()
    
    @NonCPS
    public def setSshClient() {
        sc.setSshClient()
        super.setSshClient()
    }

    // Execute custom pipeline/jobdsl steps from Jenkinsfile     
    protected void jenkinsFileScan() {
        def isCustomPipelineEnabled = getToken(Configuration.CREDS_CUSTOM_PIPELINE)

        if (!isCustomPipelineEnabled) {
            logger.warn("Custom pipeline execution is not enabled")
            return
        }

        if (!context.fileExists('Jenkinsfile')) {
            logger.warn("Jenkinsfile doesn't exist in your repository")
            return
        }

        context.stage('Jenkinsfile Stage') {
            context.script {
                context.jobDsl targets: 'Jenkinsfile'
            }
        }
    }

    /*
     * Get organization folder value
     * @return organization String
     */

    protected def getOrgFolder() {
        return this.organization
    }

    /*
     * Determined current organization folder by job name
     */

    @NonCPS
    protected void initOrganization() {
        String jobName = context.env.getEnvironment().get("JOB_NAME")
        //Configuration.get(Configuration.Parameter.JOB_NAME)
        int nameCount = Paths.get(jobName).getNameCount()

        def orgFolderName = ""
        if (nameCount == 1 && (jobName.contains("qtest-updater") || jobName.contains("testrail-updater"))) {
            // testrail-updater - i.e. stage
            orgFolderName = ""
        } else if (nameCount == 2 && (jobName.contains("qtest-updater") || jobName.contains("testrail-updater"))) {
            // stage/testrail-updater - i.e. stage
            orgFolderName = Paths.get(jobName).getName(0).toString()
        } else if (nameCount == 2) {
            // carina-demo/API_Demo_Test - i.e. empty orgFolderName
            orgFolderName = ""
        } else if (nameCount == 3) { //TODO: need to test use-case with views!
            // qaprosoft/carina-demo/API_Demo_Test - i.e. orgFolderName=qaprosoft
            orgFolderName = Paths.get(jobName).getName(0).toString()
        } else {
            throw new RuntimeException("Invalid job organization structure: '${jobName}'!")
        }

        this.organization = orgFolderName
    }

    /*
     * Get token key from Jenkins credentials based on organization
     *
     * @param tokenName Jenkins credentials id
     * @return token value
     */

    protected def getToken(tokenName) {
        def tokenValue = ""

        if (!isParamEmpty(this.organization)) {
            tokenName = "${this.organization}" + "-" + tokenName
        }

        if (getCredentials(tokenName)) {
            context.withCredentials([context.usernamePassword(credentialsId: tokenName, usernameVariable: 'KEY', passwordVariable: 'VALUE')]) {
                tokenValue = context.env.VALUE
            }
        }
        logger.debug("tokenName: ${tokenName}; tokenValue: ${tokenValue}")
        return tokenValue
    }

    /*
     * Get username and password from Jenkins credentials based on organization
     * 
     * @param tokenName Jenkins credentials id
     * @return name and password
     */

    protected def getUserCreds(tokenName) {
        def name = ""
        def password = ""

        if (!isParamEmpty(this.organization)) {
            tokenName = "${this.organization}" + "-" + tokenName
        }

        if (getCredentials(tokenName)) {
            context.withCredentials([context.usernamePassword(credentialsId: tokenName, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                name = context.env.USERNAME
                password = context.env.PASSWORD
            }
        }
        logger.debug("tokenName: ${tokenName}; name: ${name}; password: ${password}")
        return [name, password]
    }

    /*
     * set DslClasspath to support custom JobDSL logic
     */

    protected void setDslClasspath(additionalClasspath) {
        factoryRunner.setDslClasspath(additionalClasspath)
    }


}

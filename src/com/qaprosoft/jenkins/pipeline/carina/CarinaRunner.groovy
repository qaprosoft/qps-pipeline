package com.qaprosoft.jenkins.pipeline.carina

import com.qaprosoft.jenkins.pipeline.Executor
import com.qaprosoft.scm.ISCM
import com.qaprosoft.scm.github.GitHub
import com.qaprosoft.jenkins.pipeline.Configuration
import hudson.plugins.sonar.SonarGlobalConfiguration

class CarinaRunner {

    protected def context
    protected ISCM scmClient
    protected def releaseName
    protected def jobBuildUrl
    protected def emailSubject
    protected def emailRecipients
    protected Configuration configuration = new Configuration(context)

    public CarinaRunner(context) {
        this.context = context
        scmClient = new GitHub(context)
        releaseName = "${context.env.getEnvironment().get("CARINA_RELEASE")}.${context.env.getEnvironment().get("BUILD_NUMBER")}-SNAPSHOT"
        jobBuildUrl = Configuration.get(Configuration.Parameter.JOB_URL) + Configuration.get(Configuration.Parameter.BUILD_NUMBER)
        emailSubject = "CARINA ${releaseName} "
        emailRecipients = Configuration.get(Configuration.Parameter.ADMIN_EMAILS)
    }

    public void onPush() {
        context.println("CarinaRunner->onPush")
        context.node("maven") {
             try {
                scmClient.clonePush()
                deployDocumentation()
                compile()
                performSonarQubeScan()
                if(Executor.isSnapshotRequired(context.currentBuild, "build-snapshot")){
                    buildSnapshot()
                    reportingBuildResults()
                }
                proceedSuccessfulBuild()
            } catch (Exception e) {
                printStackTrace(e)
                proceedFailure()
                throw e
            } finally {
                clean()
            }
        }
    }

    public void onPullRequest() {
        context.println("CarinaRunner->onPullRequest")
        context.node("maven") {
            try {
                scmClient.clonePR()
                compile()
                performSonarQubeScan()
                if (Configuration.get("ghprbPullTitle").contains("build-snapshot")){
                    buildSnapshot()
                    reportingBuildResults()
                }
                proceedSuccessfulBuild()
            } catch (Exception e) {
                printStackTrace(e)
                proceedFailure()
                throw e
            } finally {
                clean()
            }
        }
    }

    protected def buildSnapshot() {
        context.stage('Build Snapshot') {
            executeMavenGoals("versions:set -DnewVersion=${releaseName}")
            executeMavenGoals("-Dcobertura.report.format=xml cobertura:cobertura clean deploy javadoc:javadoc")
        }
    }

    protected def reportingBuildResults() {
        context.stage('Report Results') {
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

    protected def proceedFailure() {
        def currentBuild = context.currentBuild
        currentBuild.result = 'FAILURE'
        def bodyHeader = "<p>Unable to finish build due to the unrecognized failure: ${jobBuildUrl}</p>"
        def failureLog = ""
        if (currentBuild.rawBuild.log.contains("COMPILATION ERROR : ")) {
            bodyHeader = "<p>Failed due to the compilation failure. ${jobBuildUrl}</p>"
            emailSubject = emailSubject + "COMPILATION FAILURE"
            failureLog = Executor.getLogDetailsForEmail(currentBuild, "ERROR")
        } else if (currentBuild.rawBuild.log.contains("BUILD FAILURE")) {
            bodyHeader = "<p>Failed due to the build failure. ${jobBuildUrl}</p>"
            emailSubject = emailSubject + "BUILD FAILURE"
            failureLog = Executor.getLogDetailsForEmail(currentBuild, "ERROR")
        } else  if (currentBuild.rawBuild.log.contains("Aborted by ")) {
            currentBuild.result = 'ABORTED'
            bodyHeader = "<p>Unable to finish build due to the abort by " + Executor.getAbortCause(currentBuild) + " ${jobBuildUrl}</p>"
            emailSubject = emailSubject + "ABORTED"
        } else  if (currentBuild.rawBuild.log.contains("Cancelling nested steps due to timeout")) {
            currentBuild.result = 'ABORTED'
            bodyHeader = "<p>Unable to finish build due to the abort by timeout ${jobBuildUrl}</p>"
            emailSubject = emailSubject + "TIMED OUT"
        }

        def body = bodyHeader + """<br>Rebuild: ${jobBuildUrl}/rebuild/parameterized<br>Console: ${jobBuildUrl}/console<br>${failureLog}"""
        context.emailext Executor.getEmailParams(body, emailSubject, emailRecipients)
    }

    protected def proceedSuccessfulBuild() {
        //TODO: replace http with https when ci uses secure protocol
        def body = "<p>http://ci.qaprosoft.com/nexus/content/repositories/snapshots/com/qaprosoft/carina-core/${releaseName}/</p><br><br>This is autogenerated email.<br>Please, do not reply."
        emailSubject = emailSubject + "is available."
        context.emailext Executor.getEmailParams(body, emailSubject, emailRecipients)
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

    protected void compile(){
        context.stage('Maven Compile') {
            executeMavenGoals("clean compile test-compile -f pom.xml -Dmaven.test.failure.ignore=true")
        }
    }

    protected void performSonarQubeScan(){
        context.stage('Sonar Scanner') {
            def sonarQubeEnv = ''
            Jenkins.getInstance().getDescriptorByType(SonarGlobalConfiguration.class).getInstallations().each { installation ->
                sonarQubeEnv = installation.getName()
            }
            if(sonarQubeEnv.isEmpty()){
                context.println "There is no SonarQube server configured. Please, configure Jenkins for performing SonarQube scan."
                return
            }
            context.withSonarQubeEnv(sonarQubeEnv) {
                context.sh "mvn \
				 -f pom.xml \
				 clean package sonar:sonar -DskipTests \
				 -Dsonar.github.endpoint=${Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_API_URL)}")} \
				 -Dsonar.analysis.mode=preview  \
				 -Dsonar.github.repository=${Configuration.get(Configuration.Parameter.GITHUB_ORGANIZATION)}/${Configuration.get("project")} \
				 -Dsonar.projectKey=${Configuration.get("project")} \
				 -Dsonar.projectName=${Configuration.get("project")} \
				 -Dsonar.projectVersion=1.${Configuration.get(Configuration.Parameter.BUILD_NUMBER)} \
				 -Dsonar.github.oauth=${Configuration.get(Configuration.Parameter.GITHUB_OAUTH_TOKEN)} \
				 -Dsonar.sources=. \
				 -Dsonar.tests=. \
				 -Dsonar.inclusions=**/src/main/java/** \
				 -Dsonar.test.inclusions=**/src/test/java/** \
				 -Dsonar.java.source=1.8"
            }
        }
    }
}

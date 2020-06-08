package com.qaprosoft.jenkins.pipeline.tools.maven.sonar

import com.qaprosoft.jenkins.BaseObject
import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.Configuration
import hudson.plugins.sonar.SonarGlobalConfiguration
import com.qaprosoft.jenkins.pipeline.tools.maven.Maven
import com.qaprosoft.jenkins.pipeline.tools.scm.ISCM
import com.qaprosoft.jenkins.pipeline.tools.scm.github.GitHub

import static com.qaprosoft.jenkins.Utils.*

@Mixin(Maven)public class Sonar extends BaseObject {
    private static final String SONARQUBE = ".sonarqube"
    private static boolean isSonarAvailable = false

    protected static def githubToken

    public Sonar(context) {
        super(context)
    }

    public void scan(isPullRequest=false) {
        //TODO: verify preliminary if "maven" nodes available
        context.node("maven") {
            context.stage('Sonar Scanner') {

                if (isPullRequest) {
                    getScm().clonePR()
                } else {
                    // it should be non shallow clone anyway to support full static code analysis
                    getScm().clonePush()
                }

                def sonarQubeEnv = getSonarEnv()
                def sonarConfigFileExists = context.fileExists "${SONARQUBE}"
                if (!sonarQubeEnv.isEmpty() && sonarConfigFileExists) {
                    this.isSonarAvailable = true
                } else {
                    logger.warn("Sonarqube is not configured correctly! Follow Sonar integration documentation to enable it.")
                }

                if (isPullRequest && isParamEmpty(this.githubToken)) {
                    this.isSonarAvailable = false
                    logger.warn("Sonarqube Github OAuth token is not configured correctly! Follow Sonar integration documentation to setup PullRequest checker.")
                }

                def pomFiles = getProjectPomFiles()
                pomFiles.each {
                    logger.debug("pomFile: " + it)
                    //do compile and scanner for all high level pom.xml files
                    // [VD] don't remove -U otherwise latest dependencies are not downloaded
                    def goals = "-U clean compile test -f ${it}"
                    def extraGoals = ""
                    extraGoals += Configuration.get(Configuration.Parameter.JACOCO_ENABLE).toBoolean() ? "jacoco:report-aggregate" : ""
                    if (isPullRequest) {
                        // no need to run unit tests for PR analysis
                        extraGoals += " -DskipTests"
                    } else {
                        //run unit tests to detect code coverage but don't fail the build in case of any failure
                        //TODO: for build process we can't use below goal!
                        extraGoals += " -Dmaven.test.failure.ignore=true"
                    }
                    context.mavenBuild("${goals} ${extraGoals}")

                    if (!this.isSonarAvailable) {
                        return
                    }
                    def jacocoEnable = Configuration.get(Configuration.Parameter.JACOCO_ENABLE).toBoolean()
                    def (jacocoReportPath, jacocoReportPaths) = getJacocoReportPaths(jacocoEnable)

                    context.env.sonarHome = context.tool name: 'sonar-ci-scanner', type: 'hudson.plugins.sonar.SonarRunnerInstallation'
                    context.withSonarQubeEnv('sonar-ci') {
                        // execute sonar scanner
                        context.sh scannerScript(isPullRequest, jacocoReportPaths, jacocoReportPath)
                    }
                }
            }
        }
    }

    public void setToken(token) {
        logger.debug("set sonar github token: " + token)
        this.githubToken = token
    }

    private def scannerScript(isPullRequest, jacocoReportPaths, jacocoReportPath) {
        //TODO: [VD] find a way for easier env getter. how about making Configuration syncable with current env as well...
        def sonarHome = context.env.getEnvironment().get("sonarHome")
        logger.debug("sonarHome: " + sonarHome)

        def BUILD_NUMBER = Configuration.get("BUILD_NUMBER")
        //TODO: simplify just to get log level from global var
        def SONAR_LOG_LEVEL = context.env.getEnvironment().get("QPS_PIPELINE_LOG_LEVEL").equals(Logger.LogLevel.DEBUG.name()) ?  "DEBUG" : "INFO"

        def script = "${sonarHome}/bin/sonar-scanner \
                  -Dsonar.projectVersion=${BUILD_NUMBER} \
                  -Dproject.settings=${SONARQUBE} \
                  -Dsonar.log.level=${SONAR_LOG_LEVEL} ${jacocoReportPaths} ${jacocoReportPath}"

        if (isPullRequest) {
            script += " -Dsonar.github.endpoint=${Configuration.resolveVars("${Configuration.get(Configuration.Parameter.GITHUB_API_URL)}")} \
                -Dsonar.github.pullRequest=${Configuration.get("ghprbPullId")} \
                -Dsonar.github.repository=${Configuration.get("ghprbGhRepository")} \
                -Dsonar.github.oauth=${this.githubToken} \
                -Dsonar.sourceEncoding=UTF-8 \
                -Dsonar.analysis.mode=preview"
        }
        return script
    }

    private String getSonarEnv() {
        def sonarQubeEnv = ''
        Jenkins.getInstance().getDescriptorByType(SonarGlobalConfiguration.class).getInstallations().each { installation ->
            sonarQubeEnv = installation.getName()
        }
        return sonarQubeEnv
    }

    private def getJacocoReportPaths(boolean jacocoEnable) {
        def jacocoReportPath = ""
        def jacocoReportPaths = ""

        if (jacocoEnable) {
            def jacocoItExec = 'jacoco-it.exec'

            def jacocoBucket = Configuration.get(Configuration.Parameter.JACOCO_BUCKET)
            def jacocoRegion = Configuration.get(Configuration.Parameter.JACOCO_REGION)

            // download combined integration testing coverage report: jacoco-it.exec
            // TODO: test if aws cli is installed on regular jenkins slaves as we are going to run it on each onPush event starting from 5.0
            context.withAWS(region: "${jacocoRegion}", credentials:'aws-jacoco-token') {
                def copyOutput = context.sh script: "aws s3 cp s3://${jacocoBucket}/${jacocoItExec} /tmp/${jacocoItExec}", returnStdout: true
                logger.info("copyOutput: " + copyOutput)
            }


            if (context.fileExists("/tmp/${jacocoItExec}")) {
                jacocoReportPath = "-Dsonar.jacoco.reportPath=/target/jacoco.exec" //this for unit tests code coverage
                jacocoReportPaths = "-Dsonar.jacoco.reportPaths=/tmp/${jacocoItExec}" // this one is for integration testing coverage
            }
            
            logger.debug("jacocoReportPath: " + jacocoReportPath)
            logger.debug("jacocoReportPaths: " + jacocoReportPaths)
        }
    
        return [jacocoReportPath, jacocoReportPaths]
    }
}

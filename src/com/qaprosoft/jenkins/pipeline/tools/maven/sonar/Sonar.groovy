package com.qaprosoft.jenkins.pipeline.tools.maven.sonar

import groovy.transform.InheritConstructors
import com.qaprosoft.jenkins.BaseObject
import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.Configuration

import static com.qaprosoft.jenkins.Utils.*

@InheritConstructors
class Sonar extends BaseObject {

    private static final String SONARQUBE = ".sonarqube"

    protected static def githubToken

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

                def LOG_LEVEL = configuration.getGlobalProperty('QPS_PIPELINE_LOG_LEVEL').equals(Logger.LogLevel.DEBUG.name()) ? 'DEBUG' : 'INFO'
                def SONAR_URL = getHostUrl()

                def jacocoEnable = configuration.get(Configuration.Parameter.JACOCO_ENABLE).toBoolean()
                def (jacocoReportPath, jacocoReportPaths) = getJacocoReportPaths(jacocoEnable)
                
            
                for (pomFile in context.getPomFiles()) {
                    logger.debug("pomFile: " + pomFile)
                    //do compile and scanner for all high level pom.xml file
                    // [VD] don't remove -U otherwise latest dependencies are not downloaded
                    def goals = "-U clean compile test -f ${pomFile} sonar:sonar -Dsonar.host.url=${SONAR_URL} -Dsonar.log.level=${LOG_LEVEL} ${jacocoReportPaths} ${jacocoReportPath}"
                    def extraGoals = jacocoEnable ? 'jacoco:report-aggregate' : ''
                    if (isPullRequest) {
                        // no need to run unit tests for PR analysis
                        extraGoals += " -DskipTests \
                                -Dproject.settings=${SONARQUBE} \
                                -Dsonar.pullrequest.key=${Configuration.get("ghprbPullId")}\
                                -Dsonar.pullrequest.branch=${Configuration.get("ghprbSourceBranch")} \
                                -Dsonar.pullrequest.base=${Configuration.get("ghprbTargetBranch")} \
                                -Dsonar.pullrequest.github.endpoint=https://api.github.com \
                                -Dsonar.pullrequest.github.repository=${Configuration.get("ghprbGhRepository")}"
                        logger.info("extraGoals: " + extraGoals)
                    } else {
                        //run unit tests to detect code coverage but don't fail the build in case of any failure
                        //TODO: for build process we can't use below goal!
                        extraGoals += " -Dmaven.test.failure.ignore=true"
                    }
                    context.mavenBuild("${goals} ${extraGoals}")
                }
            }
        }
    }

    public void setToken(token) {
        logger.debug("set sonar github token: " + token)
        this.githubToken = token
    }

    private def getHostUrl() {
        return "http://${Configuration.get('INFRA_HOST')}/sonarqube"
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
            context.withAWS(region: "${jacocoRegion}", credentials: 'aws-jacoco-token') {
                def copyOutput = context.sh script: "aws s3 cp s3://${jacocoBucket}/${jacocoItExec} /tmp/${jacocoItExec}", returnStdout: true
                logger.info("copyOutput: " + copyOutput)
            }

            if (context.fileExists("/tmp/${jacocoItExec}")) {
                jacocoReportPath = "-Dsonar.jacoco.reportPath=/target/jacoco.exec" //this for unit tests code coverage
                jacocoReportPaths = "-Dsonar.jacoco.reportPaths=/tmp/${jacocoItExec}"
                // this one is for integration testing coverage
            }

            logger.debug("jacocoReportPath: " + jacocoReportPath)
            logger.debug("jacocoReportPaths: " + jacocoReportPaths)
        }

        return [jacocoReportPath, jacocoReportPaths]
    }
}

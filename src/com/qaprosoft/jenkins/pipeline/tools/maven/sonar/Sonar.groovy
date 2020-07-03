package com.qaprosoft.jenkins.pipeline.tools.maven.sonar

import groovy.transform.InheritConstructors
import com.qaprosoft.jenkins.BaseObject
import com.qaprosoft.jenkins.pipeline.Configuration

@InheritConstructors
class Sonar extends BaseObject {

    private SonarClient sc

    Sonar(context) {
        super(context)
        sc = new SonarClient(context)
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

                def jacocoEnable = configuration.get(Configuration.Parameter.JACOCO_ENABLE)?.toBoolean()
                def (jacocoReportPath, jacocoReportPaths) = getJacocoReportPaths(jacocoEnable)

                def sonarGoals = " sonar:sonar \
                                 -Dsonar.host.url=${this.sc.getServiceUrl()} \
                                 -Dsonar.log.level=${this.logger.pipelineLogLevel} \
                                 -Dsonar.branch.name=${Configuration.get("branch")}"

                def isSonarAvailable = sc.isAvailable()

                if (!isSonarAvailable) {
                    logger.warn("The sonarqube ${this.sc.getServiceUrl()} server is not available, sonarqube scan will be skipped!")
                    sonarGoals = ""
                }
                            
                for (pomFile in context.getPomFiles()) {
                    logger.debug("pomFile: " + pomFile)
                    //do compile and scanner for all high level pom.xml files
                    // [VD] don't remove -U otherwise latest dependencies are not downloaded
                    def goals = "-U clean compile test -f ${pomFile}"
                    def extraGoals = jacocoEnable ? 'jacoco:report-aggregate ${jacocoReportPaths} ${jacocoReportPath}' : ''

                    if (isPullRequest) {
                        // no need to run unit tests for PR analysis
                        goals += " -DskipTests"

                        if (isSonarAvailable) {
                            // such param should be remove to decorate pr
                            sonarGoals.minus("-Dsonar.branch.name=${Configuration.get("branch")}")
                            // goals needed to decorete pr with sonar analysis
                            sonarGoals += " -Dsonar.verbose=true \
                                    -Dsonar.pullrequest.key=${Configuration.get("ghprbPullId")} \
                                    -Dsonar.pullrequest.branch=${Configuration.get("ghprbSourceBranch")} \
                                    -Dsonar.pullrequest.base=${Configuration.get("ghprbTargetBranch")} \
                                    -Dsonar.pullrequest.github.repository=${Configuration.get("ghprbGhRepository")}"
                        
                        } 
                        
                    } else {
                        //run unit tests to detect code coverage but don't fail the build in case of any failure
                        //TODO: for build process we can't use below goal!
                        extraGoals += " -Dmaven.test.failure.ignore=true"
                    }

                    context.mavenBuild("${goals} ${extraGoals} ${sonarGoals}")
                }
            }
        }
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
                jacocoReportPaths = "-Dsonar.jacoco.reportPaths=/tmp/${jacocoItExec}" // this one is for integration testing coverage
            }

            logger.debug("jacocoReportPath: " + jacocoReportPath)
            logger.debug("jacocoReportPaths: " + jacocoReportPaths)
        }

        return [jacocoReportPath, jacocoReportPaths]
    }
}

package com.qaprosoft.jenkins.pipeline.integration.sonar

import com.qaprosoft.jenkins.pipeline.integration.HttpClient
import com.qaprosoft.jenkins.pipeline.Configuration

import static com.qaprosoft.jenkins.Utils.*

class SonarClient extends HttpClient {

    private String serviceUrl

    SonarClient(context) {
        super(context)
        serviceUrl = context.env.getEnvironment().get("SONAR_URL")
    }

    public String getGoals(isPullRequest=false) {
        def goals = ""
        def scmProvider = Configuration.get(Configuration.Parameter.GITHUB_HOST)

        if (isParamEmpty(serviceUrl)) {
            logger.warn("The url for the sonarqube server is not configured, sonarqube scan will be skipped!")
            return goals
        }

        if (!isAvailable()) {
            logger.warn("The sonarqube ${this.serviceUrl} server is not available, sonarqube scan will be skipped!")
            return goals
        }

        goals = " -Dsonar.host.url=${this.serviceUrl} -Dsonar.log.level=${this.logger.pipelineLogLevel}"

        if (isPullRequest) {
            // goals needed to decorete pr with sonar analysis
            if (scmProvider.contains("github")) {
                goals += " -Dsonar.pullrequest.github.repository=${Configuration.get("ghprbGhRepository")} \
                          -Dsonar.pullrequest.provider=Github"
            } else if (scmProvider.contains("bitbucket")) {
                goals += " -Dsonar.pullrequest.bitbucket.repositorySlug=${Configuration.get("ghprbGhRepository")} \
                          -Dsonar.pullrequest.provider=BitbucketServer"
            } else if (scmProvider.contains("gitlab")) {
                goals += " -Dsonar.pullrequest.gitlab.repositorySlug=${Configuration.get("ghprbGhRepository")} \
                          -Dsonar.pullrequest.provider=GitlabServer"
            }

            goals += " -Dsonar.pullrequest.key=${Configuration.get("ghprbPullId")} \
                    -Dsonar.pullrequest.branch=${Configuration.get("ghprbSourceBranch")} \
                    -Dsonar.pullrequest.base=${Configuration.get("ghprbTargetBranch")}"
        } else {
            goals += " -Dsonar.projectVersion=${Configuration.get("BUILD_NUMBER")} -Dsonar.branch.name=${Configuration.get("branch")}"
        }

        // jacoco goals are valuable only when sonar is intergated as of now!
        goals += getJacocoGoals()
        return goals
    }

    private boolean isAvailable() {
        def parameters = [contentType        : 'APPLICATION_JSON',
            httpMode           : 'GET',
            validResponseCodes : '200',
            url                : serviceUrl + '/api/system/status']
        return "UP".equals(sendRequestFormatted(parameters)?.get("status"))
    }

    private String getJacocoGoals() {
        def goals = ""
        def isJacocoEnabled = Configuration.get(Configuration.Parameter.JACOCO_ENABLE)?.toBoolean()

        if (isJacocoEnabled) {
            def (jacocoReportPath, jacocoReportPaths) = getJacocoReportPaths()
            goals = " jacoco:report-aggregate ${jacocoReportPaths} ${jacocoReportPath}"
        }

        return goals
    }

    private def getJacocoReportPaths() {
        def jacocoReportPath = ""
        def jacocoReportPaths = ""

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

        return [jacocoReportPath, jacocoReportPaths]
    }

}
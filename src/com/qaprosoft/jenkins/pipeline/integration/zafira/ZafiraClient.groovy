package com.qaprosoft.jenkins.pipeline.integration.zafira

import com.qaprosoft.jenkins.pipeline.integration.HttpClient
import groovy.json.JsonBuilder
import static com.qaprosoft.jenkins.Utils.*
import com.qaprosoft.jenkins.pipeline.Configuration

class ZafiraClient extends HttpClient {

    private String serviceURL
    private String refreshToken
    private String authToken
    private long tokenExpTime

    public ZafiraClient(context) {
        super(context)
        serviceURL = Configuration.get(Configuration.Parameter.ZAFIRA_SERVICE_URL)
        refreshToken = Configuration.get(Configuration.Parameter.ZAFIRA_ACCESS_TOKEN)
    }

    public def queueZafiraTestRun(uuid) {
        if (isTokenExpired()) {
            getZafiraAuthToken(refreshToken)
            if (isParamEmpty(authToken))
                return
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder jobUrl: replaceTrailingSlash(Configuration.get(Configuration.Parameter.JOB_URL)),
                buildNumber: Configuration.get(Configuration.Parameter.BUILD_NUMBER),
                branch: Configuration.get("branch"),
                env: Configuration.get("env"),
                ciRunId: uuid,
                ciParentUrl: replaceTrailingSlash(Configuration.get("ci_parent_url")),
                ciParentBuild: Configuration.get("ci_parent_build"),
                project: Configuration.get("zafira_project")

        logger.info("REQUEST: " + jsonBuilder.toPrettyString())
        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: requestBody,
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "/api/tests/runs/queue"]
        return sendRequestFormatted(parameters)
    }

    public def smartRerun() {
        if (isTokenExpired()) {
            getZafiraAuthToken(refreshToken)
            if (isParamEmpty(authToken))
                return
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder owner: Configuration.get("ci_user_id"),
                upstreamJobId: Configuration.get("ci_job_id"),
                upstreamJobBuildNumber: Configuration.get("ci_parent_build"),
                scmUrl: Configuration.get("scm_url"),
                hashcode: Configuration.get("hashcode")

        logger.info("REQUEST: " + jsonBuilder.toPrettyString())
        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: requestBody,
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "/api/tests/runs/rerun/jobs?doRebuild=${Configuration.get("doRebuild")}&rerunFailures=${Configuration.get("rerunFailures")}",
                          timeout: 300000]
        return sendRequestFormatted(parameters)
    }

    public def abortTestRun(uuid, failureReason) {
        if (isTokenExpired()) {
            getZafiraAuthToken(refreshToken)
            if (isParamEmpty(authToken))
                return
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder comment: failureReason

        logger.debug("REQUEST: " + jsonBuilder.toPrettyString())
        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: requestBody,
                          validResponseCodes: "200:500",
                          url: this.serviceURL + "/api/tests/runs/abort?ciRunId=${uuid}"]
        return sendRequestFormatted(parameters)
    }

    public def sendEmail(uuid, emailList, filter) {
        if (isTokenExpired()) {
            getZafiraAuthToken(refreshToken)
            if (isParamEmpty(authToken))
                return
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder recipients: emailList

        logger.debug("REQUEST: " + jsonBuilder.toPrettyString())
        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: requestBody,
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "/api/tests/runs/${uuid}/email?filter=${filter}"]
        return sendRequest(parameters)
    }

    public def sendSlackNotification(uuid, channels) {
        if (isTokenExpired()) {
            getZafiraAuthToken(refreshToken)
            if (isParamEmpty(authToken))
                return
        }
        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'GET',
                          validResponseCodes: "200",
                          url: this.serviceURL + "/api/slack/testrun/${uuid}/finish?channels=${channels}"]
        return sendRequest(parameters)
    }

    public def exportTagData(uuid, tagName) {
        if (isTokenExpired()) {
            getZafiraAuthToken(refreshToken)
            if (isParamEmpty(authToken))
                return
        }
        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'GET',
                          validResponseCodes: "200",
                          url: this.serviceURL + "/api/tags/${uuid}/integration?integrationTag=${tagName}"]
        return sendRequestFormatted(parameters)
    }

    public def sendFailureEmail(uuid, emailList, suiteOwner, suiteRunner) {
        if (isTokenExpired()) {
            getZafiraAuthToken(refreshToken)
            if (isParamEmpty(authToken))
                return
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder recipients: emailList

        logger.debug("REQUEST: " + jsonBuilder.toPrettyString())

        String requestBody = jsonBuilder.toString()
        jsonBuilder = null
        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: requestBody,
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "/api/tests/runs/${uuid}/emailFailure?suiteOwner=${suiteOwner}&suiteRunner=${suiteRunner}"]
        return sendRequest(parameters)
    }

    public def exportZafiraReport(uuid) {
        if (isTokenExpired()) {
            getZafiraAuthToken(refreshToken)
            if (isParamEmpty(authToken))
                return
        }
        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'GET',
                          validResponseCodes: "200:500",
                          url: this.serviceURL + "/api/tests/runs/${uuid}/export"]

        return sendRequest(parameters)
    }

    public def getTestRunByCiRunId(uuid) {
        if (isTokenExpired()) {
            getZafiraAuthToken(refreshToken)
            if (isParamEmpty(authToken))
                return
        }
        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'GET',
                          validResponseCodes: "200:404",
                          url: this.serviceURL + "/api/tests/runs?ciRunId=${uuid}"]

        return sendRequestFormatted(parameters)
    }


    public def createLauncher(jobParameters, jobUrl, repo) {
        if (isTokenExpired()) {
            getZafiraAuthToken(refreshToken)
            if (isParamEmpty(authToken))
                return
        }
        jobParameters = new JsonBuilder(jobParameters).toPrettyString()
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder jobParameters: jobParameters,
                jobUrl: jobUrl,
                repo: repo

        logger.debug("REQUEST: " + jsonBuilder.toPrettyString())
        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: requestBody,
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "/api/launchers/create"]
        return sendRequestFormatted(parameters)
    }

    public def createJob(jobUrl) {
        if (isTokenExpired()) {
            getZafiraAuthToken(refreshToken)
            if (isParamEmpty(authToken))
                return
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder jobUrlValue: jobUrl

        logger.debug("REQUEST: " + jsonBuilder.toPrettyString())
        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: requestBody,
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "/api/jobs/url"]
        return sendRequestFormatted(parameters)
    }

    protected boolean isTokenExpired() {
        return authToken == null || System.currentTimeMillis() > tokenExpTime
    }

    /** Generates authToken using refreshToken*/
    protected void getZafiraAuthToken(refreshToken) {
        logger.debug("refreshToken: " + refreshToken)
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder refreshToken: refreshToken

        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          validResponseCodes: "200:404",
                          requestBody: requestBody,
                          url: this.serviceURL + "/api/auth/refresh"]
        logger.debug("parameters: " + parameters)
        Map properties = (Map)sendRequestFormatted(parameters)
        logger.debug("properties: " + properties)
        if (isParamEmpty(properties)) {
            logger.info("Unable to get auth token, check Zafira integration properties")
            return
        }
        authToken = properties.type + " " + properties.accessToken
        logger.debug("authToken: " + authToken)
        tokenExpTime = System.currentTimeMillis() + 290 * 60 * 1000
    }

}
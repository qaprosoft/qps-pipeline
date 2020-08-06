package com.qaprosoft.jenkins.pipeline.integration.zafira

import com.qaprosoft.jenkins.pipeline.integration.HttpClient
import groovy.json.JsonBuilder
import static com.qaprosoft.jenkins.Utils.*
import com.qaprosoft.jenkins.pipeline.Configuration

/*
 * Prerequisites: valid REPORTING_SERVICE_URL and REPORTING_ACCESS_TOKEN already defined in Configuration
 */

class ZafiraClient extends HttpClient {

    private String serviceURL
    private String refreshToken
    private String authToken
    private long tokenExpTime

    public ZafiraClient(context) {
        super(context)
        this.serviceURL = Configuration.get(Configuration.Parameter.REPORTING_SERVICE_URL)
        this.refreshToken = Configuration.get(Configuration.Parameter.REPORTING_ACCESS_TOKEN)
    }

    public def queueZafiraTestRun(uuid) {
        if (!isZafiraConnected()) {
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

        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          requestBody       : requestBody,
                          validResponseCodes: "200:401",
                          url               : this.serviceURL + "/api/reporting/api/tests/runs/queue"]
        return sendRequestFormatted(parameters)
    }

    public def smartRerun() {
        if (!isZafiraConnected()) {
            return
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder owner: Configuration.get("owner"),
                cause: Configuration.get("cause"),
                upstreamJobId: Configuration.get("upstreamJobId"),
                upstreamJobBuildNumber: Configuration.get("upstreamJobBuildNumber"),
                scmURL: Configuration.get("scmURL"),
                hashcode: Configuration.get("hashcode")

        logger.info("REQUEST: " + jsonBuilder.toPrettyString())
        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          requestBody       : requestBody,
                          validResponseCodes: "200:401",
                          url               : this.serviceURL + "/api/reporting/api/tests/runs/rerun/jobs?doRebuild=${Configuration.get("doRebuild")}&rerunFailures=${Configuration.get("rerunFailures")}",
                          timeout           : 300000]
        return sendRequestFormatted(parameters)
    }

    public def abortTestRun(uuid, failureReason) {
        if (!isZafiraConnected()) {
            return
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder comment: failureReason

        logger.debug("REQUEST: " + jsonBuilder.toPrettyString())
        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          requestBody       : requestBody,
                          validResponseCodes: "200:500",
                          url               : this.serviceURL + "/api/reporting/api/tests/runs/abort?ciRunId=${uuid}"]
        return sendRequestFormatted(parameters)
    }

    public def sendEmail(uuid, emailList, filter) {
        if (!isZafiraConnected()) {
            return
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder recipients: emailList

        logger.debug("REQUEST: " + jsonBuilder.toPrettyString())
        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          requestBody       : requestBody,
                          validResponseCodes: "200:401",
                          url               : this.serviceURL + "/api/reporting/api/tests/runs/${uuid}/email?filter=${filter}"]
        return sendRequest(parameters)
    }

    public def sendSlackNotification(uuid, channels) {
        if (!isZafiraConnected()) {
            return
        }
        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200",
                          url               : this.serviceURL + "/api/reporting/api/slack/testrun/${uuid}/finish?channels=${channels}"]
        return sendRequest(parameters)
    }

    public def exportTagData(uuid, tagName) {
        if (!isZafiraConnected()) {
            return
        }
        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200",
                          url               : this.serviceURL + "/api/reporting/api/tags/${uuid}/integration?integrationTag=${tagName}"]
        return sendRequestFormatted(parameters)
    }

    public def sendFailureEmail(uuid, emailList, suiteOwner, suiteRunner) {
        if (!isZafiraConnected()) {
            return
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder recipients: emailList

        logger.debug("REQUEST: " + jsonBuilder.toPrettyString())

        String requestBody = jsonBuilder.toString()
        jsonBuilder = null
        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          requestBody       : requestBody,
                          validResponseCodes: "200:401",
                          url               : this.serviceURL + "/api/reporting/api/tests/runs/${uuid}/emailFailure?suiteOwner=${suiteOwner}&suiteRunner=${suiteRunner}"]
        return sendRequest(parameters)
    }

    public def exportZafiraReport(uuid) {
        if (!isZafiraConnected()) {
            return
        }
        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200:500",
                          url               : this.serviceURL + "/api/reporting/api/tests/runs/${uuid}/export"]

        return sendRequest(parameters)
    }

    public def getTestRunByCiRunId(uuid) {
        if (!isZafiraConnected()) {
            return
        }
        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200:404",
                          url               : this.serviceURL + "/api/reporting/api/tests/runs?ciRunId=${uuid}"]

        return sendRequestFormatted(parameters)
    }


    public def createLaunchers(jenkinsJobsScanResult) {
        if (!isZafiraConnected()) {
            return
        }

        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder jenkinsJobsScanResult

        logger.info("REQUEST: " + jsonBuilder.toPrettyString())
        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        logger.debug("token value: ${authToken}")
        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          requestBody       : requestBody,
                          validResponseCodes: "200:404",
                          url               : this.serviceURL + "/api/reporting/api/launchers/create"]
        return sendRequestFormatted(parameters)
    }

    public def createJob(jobUrl) {
        if (!isZafiraConnected()) {
            return
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder jobUrlValue: jobUrl

        logger.debug("REQUEST: " + jsonBuilder.toPrettyString())
        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          requestBody       : requestBody,
                          validResponseCodes: "200:401",
                          url               : this.serviceURL + "/api/reporting/api/jobs/url"]
        return sendRequestFormatted(parameters)
    }

    protected boolean isTokenExpired() {
        return authToken == null || System.currentTimeMillis() > tokenExpTime
    }

    /** Verify if ZafiraConnected and refresh authToken if needed. Return false if connection can't be established or disabled **/
    protected boolean isZafiraConnected() {
        if (!isTokenExpired()) {
            logger.debug("zafira connected")
            return true
        }

        if (isParamEmpty(this.refreshToken) || isParamEmpty(this.serviceURL) || Configuration.mustOverride.equals(this.serviceURL)) {
            logger.debug("zafira is not connected!")
            logger.debug("refreshToken: ${this.refreshToken}; serviceURL: ${this.serviceURL};")
            return false
        }

        logger.debug("refreshToken: " + refreshToken)
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder refreshToken: this.refreshToken

        String requestBody = jsonBuilder.toString()
        jsonBuilder = null

        def parameters = [contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          validResponseCodes: "200:404",
                          requestBody       : requestBody,
                          url               : this.serviceURL + "/api/iam/v1/auth/refresh"]

        logger.debug("parameters: " + parameters)
        Map properties = (Map) sendRequestFormatted(parameters)
        logger.debug("properties: " + properties)
        if (isParamEmpty(properties)) {
            // #669: no sense to start tests if zafira is configured and not available! 
            logger.info("properties: " + properties)
            throw new RuntimeException("Unable to get auth token, check Zafira integration!")
        }
        this.authToken = properties.authTokenType + " " + properties.authToken
        logger.debug("authToken: " + authToken)
        this.tokenExpTime = System.currentTimeMillis() + 470 * 60 * 1000 //8 hours - interval '10 minutes'
        return true
    }

}


package com.qaprosoft.integration.zafira

import com.qaprosoft.integration.HttpClient
import groovy.json.JsonBuilder

import static com.qaprosoft.jenkins.pipeline.Executor.*
import com.qaprosoft.jenkins.pipeline.Configuration

class ZafiraClient extends HttpClient{

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
		}
		JsonBuilder jsonBuilder = new JsonBuilder()
		jsonBuilder jobName: Configuration.get(Configuration.Parameter.JOB_BASE_NAME),
				buildNumber: Configuration.get(Configuration.Parameter.BUILD_NUMBER),
				branch: Configuration.get("branch"),
				env: Configuration.get("env"),
				ciRunId: uuid,
				ciParentUrl: Configuration.get("ci_parent_url"),
				ciParentBuild: Configuration.get("ci_parent_build"),
				project: Configuration.get("zafira_project")
		def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
						  contentType: 'APPLICATION_JSON',
						  httpMode: 'POST',
						  requestBody: "${jsonBuilder}",
						  validResponseCodes: "200:401",
						  url: this.serviceURL + "/api/tests/runs/queue"]
		return sendRequestFormatted(parameters)
	}

	public def smartRerun() {
//		if (isTokenExpired()) {
//			getZafiraAuthToken(refreshToken)
//		}
		logger.info("owner: " + Configuration.get("ci_user_id"))
		logger.info("upstreamJobId: " + Configuration.get("ci_job_id"))
		logger.info("upstreamJobBuildNumber: " + Configuration.get("ci_parent_build"))
		logger.info("scmUrl: " + Configuration.get("scm_url"))
		logger.info("hashcode: " + Configuration.get("hashcode"))

        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder owner: Configuration.get("ci_user_id"),
                upstreamJobId: Configuration.get("ci_job_id"),
                upstreamJobBuildNumber: Configuration.get("ci_parent_build"),
                scmUrl: Configuration.get("scm_url"),
                hashcode: Configuration.get("hashcode")
        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: "${jsonBuilder}",
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "/api/tests/runs/rerun/jobs?doRebuild=${Configuration.get("doRebuild")}&rerunFailures=${Configuration.get("rerunFailures")}",
                          timeout: 300000]
        return sendRequestFormatted(parameters)
	}

	public def abortTestRun(uuid, failureReason) {
		if (isTokenExpired()) {
			getZafiraAuthToken(refreshToken)
		}
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder comment: failureReason
        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: "${jsonBuilder}",
						  validResponseCodes: "200:500",
						  url: this.serviceURL + "/api/tests/runs/abort?ciRunId=${uuid}"]
        return sendRequestFormatted(parameters)
    }

    public def sendEmail(uuid, emailList, filter) {
		if (isTokenExpired()) {
			getZafiraAuthToken(refreshToken)
		}
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder recipients: emailList
		def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
						  contentType: 'APPLICATION_JSON',
						  httpMode: 'POST',
                          requestBody: "${jsonBuilder}",
						  validResponseCodes: "200:401",
						  url: this.serviceURL + "/api/tests/runs/${uuid}/email?filter=${filter}"]
		return sendRequest(parameters)
    }

	public def exportTagData(uuid, tagName) {
		if (isTokenExpired()) {
			getZafiraAuthToken(refreshToken)
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
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder recipients: emailList
        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: "${jsonBuilder}",
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "/api/tests/runs/${uuid}/emailFailure?suiteOwner=${suiteOwner}&suiteRunner=${suiteRunner}"]
        return sendRequest(parameters)
    }

	public def exportZafiraReport(uuid) {
        if (isTokenExpired()) {
            getZafiraAuthToken(refreshToken)
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
        }
		def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
						  contentType: 'APPLICATION_JSON',
						  httpMode: 'GET',
						  validResponseCodes: "200:404",
						  url: this.serviceURL + "/api/tests/runs?ciRunId=${uuid}"]

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
		def parameters = [contentType: 'APPLICATION_JSON',
						  httpMode: 'POST',
                          requestBody: "${jsonBuilder}",
						  url: this.serviceURL + "/api/auth/refresh"]
        Map properties = (Map)sendRequestFormatted(parameters)

		authToken = properties.type + " " + properties.accessToken
		tokenExpTime = System.currentTimeMillis() + 290 * 60 * 1000
	}

}
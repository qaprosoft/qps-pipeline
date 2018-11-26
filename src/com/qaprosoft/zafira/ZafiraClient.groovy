package com.qaprosoft.zafira

import com.qaprosoft.Logger
import groovy.json.JsonBuilder

import static com.qaprosoft.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*
import com.qaprosoft.jenkins.pipeline.Configuration

class ZafiraClient {

	private String serviceURL
	private String refreshToken
	private String authToken
	private long tokenExpTime
	private def context
	private Logger logger

    public ZafiraClient(context) {
        this.context = context
        serviceURL = Configuration.get(Configuration.Parameter.ZAFIRA_SERVICE_URL)
        refreshToken = Configuration.get(Configuration.Parameter.ZAFIRA_ACCESS_TOKEN)
        logger = new Logger(context)
    }

	public def queueZafiraTestRun(String uuid) {
        if(isParamEmpty(Configuration.get("queue_registration")) || Configuration.get("queue_registration").toBoolean()) {
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

            def response = sendRequestFormatted(parameters)
            logger.info("Queued TestRun: " + formatJson(response))
        }
    }

	public def smartRerun() {
		if (isTokenExpired()) {
			getZafiraAuthToken(refreshToken)
		}
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

        def response = sendRequestFormatted(parameters)
        logger.info("Results : " + response.size())
        logger.info("Tests for rerun: " + formatJson(response))
	}

	public def abortTestRun(String uuid, currentBuild) {
		currentBuild.result = BuildResult.FAILURE
		def failureReason = "undefined failure"

		String buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
		String jobBuildUrl = Configuration.get(Configuration.Parameter.JOB_URL) + buildNumber
		String jobName = Configuration.get(Configuration.Parameter.JOB_NAME)
		String env = Configuration.get("env")

		def bodyHeader = "<p>Unable to execute tests due to the unrecognized failure: ${jobBuildUrl}</p>"
		def subject = getFailureSubject("UNRECOGNIZED FAILURE", jobName, env, buildNumber)
		def failureLog = ""

		if (currentBuild.rawBuild.log.contains("COMPILATION ERROR : ")) {
			bodyHeader = "<p>Unable to execute tests due to the compilation failure. ${jobBuildUrl}</p>"
			subject = getFailureSubject("COMPILATION FAILURE", jobName, env, buildNumber)
			failureLog = getLogDetailsForEmail(currentBuild, "ERROR")
			failureReason = URLEncoder.encode(failureLog, "UTF-8")
		} else  if (currentBuild.rawBuild.log.contains("Cancelling nested steps due to timeout")) {
			currentBuild.result = BuildResult.ABORTED
			bodyHeader = "<p>Unable to continue tests due to the abort by timeout ${jobBuildUrl}</p>"
			subject = getFailureSubject("TIMED OUT", jobName, env, buildNumber)
			failureReason = "Aborted by timeout"
		} else if (currentBuild.rawBuild.log.contains("BUILD FAILURE")) {
			bodyHeader = "<p>Unable to execute tests due to the build failure. ${jobBuildUrl}</p>"
			subject = getFailureSubject("BUILD FAILURE", jobName, env, buildNumber)
			failureLog = getLogDetailsForEmail(currentBuild, "ERROR")
			failureReason = URLEncoder.encode("BUILD FAILURE:\n" + failureLog, "UTF-8")
		} else  if (currentBuild.rawBuild.log.contains("Aborted by ")) {
			currentBuild.result = BuildResult.ABORTED
			bodyHeader = "<p>Unable to continue tests due to the abort by " + getAbortCause(currentBuild) + " ${jobBuildUrl}</p>"
			subject = getFailureSubject("ABORTED", jobName, env, buildNumber)
			failureReason = "Aborted by " + getAbortCause(currentBuild)
		}

		if (isTokenExpired()) {
			getZafiraAuthToken(refreshToken)
		}
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder comment: failureReason
        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: "${jsonBuilder}",
						  validResponseCodes: "200:401",
						  url: this.serviceURL + "/api/tests/runs/abort?ciRunId=${uuid}"]

        def response = sendRequestFormatted(parameters)
        def emailList = Configuration.get(Configuration.Parameter.ADMIN_EMAILS)
        if(response && response.status == 200){
            sendFailureEmail(uuid, emailList)
        } else {
            //Explicitly send email via Jenkins (emailext) as nothing is registered in Zafira
            def body = bodyHeader + """<br>
                       Rebuild: ${jobBuildUrl}/rebuild/parameterized<br>
                  ZafiraReport: ${jobBuildUrl}/ZafiraReport<br>
		               Console: ${jobBuildUrl}/console<br>${failureLog.replace("\n", "<br>")}"""
            context.emailext getEmailParams(body, subject, emailList)
        }
    }

    public def sendEmail(String uuid, String emailList, String filter) {
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
		return sendRequestFormatted(parameters)
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

	public def sendFailureEmail(String uuid, String emailList) {
        def suiteOwner = true
        def suiteRunner = false
        if(Configuration.get("suiteOwner")){
            suiteOwner = Configuration.get("suiteOwner")
        }
        if(Configuration.get("suiteRunner")){
            suiteOwner = Configuration.get("suiteRunner")
        }
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
        return sendRequestFormatted(parameters)
    }

	public def exportZafiraReport(String uuid) {
        if (isTokenExpired()) {
            getZafiraAuthToken(refreshToken)
        }
		def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
						  contentType: 'APPLICATION_JSON',
						  httpMode: 'GET',
						  validResponseCodes: "200:401",
						  url: this.serviceURL + "/api/tests/runs/${uuid}/export"]

		return sendRequest(parameters)
	}

	public def getTestRunByCiRunId(String uuid) {
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
	protected void getZafiraAuthToken(String refreshToken) {
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

	/** Sends httpRequest using passed parameters */
	protected def sendRequestFormatted(requestParams) {
		def response = sendRequest(requestParams)
		if(response){
			return getObjectResponse(response)
		}
	}

	protected def sendRequest(requestParams) {
		def response = null
		/** Catches exceptions in every http call */
		try {
			response = context.httpRequest requestParams
		} catch (Exception e) {
			logger.error(printStackTrace(e))
		}
		if(!response || response.status > 200){
			return
		}
		return response.content
	}
}
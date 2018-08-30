package com.qaprosoft.zafira

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.qaprosoft.jenkins.pipeline.Configurator

class ZafiraClient {

	private String serviceURL
	private String refreshToken
	private String authToken
	private boolean developMode
	private def context

	public ZafiraClient(context) {
		this.context = context
		initZafiraClient()
	}

	@NonCPS
	/** Inits ZafiraClient using values which are present in Configurator */
	public void initZafiraClient() {
		this.serviceURL = Configurator.get(Configurator.Parameter.ZAFIRA_SERVICE_URL)
		context.println "zafiraUrl: " + serviceURL
		this.refreshToken = Configurator.get(Configurator.Parameter.ZAFIRA_ACCESS_TOKEN)
		developMode = Configurator.get("develop") ? Configurator.get("develop").toBoolean() : false
	}

	public void queueZafiraTestRun(String uuid) {
		if(developMode){
			return
		}
		def parameters = [customHeaders: [[name: 'Authorization',
										   value: "${authToken}"]],
						  contentType: 'APPLICATION_JSON',
						  httpMode: 'POST',
						  requestBody: "{\"jobName\": \"${Configurator.get(Configurator.Parameter.JOB_BASE_NAME)}\", \
                                         \"buildNumber\": \"${Configurator.get(Configurator.Parameter.BUILD_NUMBER)}\", \
                                         \"branch\": \"${Configurator.get("branch")}\", \
                                         \"env\": \"${Configurator.get("env")}\", \
                                         \"ciRunId\": \"${uuid}\", \
                                         \"ciParentUrl\": \"${Configurator.get("ci_parent_url")}\", \
                                         \"ciParentBuild\": \"${Configurator.get("ci_parent_build")}\"}",
						  validResponseCodes: "200:401",
						  url: this.serviceURL + "/api/tests/runs/queue"]

		def response = sendRequest(parameters)
		response = checkStatus(response, parameters)
		if (response == 401) {
			return
		}
        String formattedJSON = JsonOutput.prettyPrint(response.content)
        context.println("Queued TestRun: ${formattedJSON}")
    }

	public void smartRerun() {
		if(developMode){
			return
		}
		def parameters = [customHeaders: [[name: 'Authorization',
										  value: "${authToken}"]],
						 contentType: 'APPLICATION_JSON',
						 httpMode: 'POST',
						 requestBody: "{\"owner\": \"${Configurator.get("ci_user_id")}\", \
                                        \"upstreamJobId\": \"${Configurator.get("ci_job_id")}\", \
                                        \"upstreamJobBuildNumber\": \"${Configurator.get("ci_parent_build")}\", \
                                        \"scmUrl\": \"${Configurator.get("scm_url")}\", \
                                        \"hashcode\": \"${Configurator.get("hashcode")}\"}",
						  validResponseCodes: "200:401",
						  url: this.serviceURL + "/api/tests/runs/rerun/jobs?doRebuild=${Configurator.get("doRebuild")}&rerunFailures=${Configurator.get("rerunFailures")}",
						 timeout: 300000]

		def response = sendRequest(parameters)
		response = checkStatus(response, parameters)
		if (response == 401) {
			return
		}

		def responseJson = new JsonSlurper().parseText(response.content)

		context.println("Results : ${responseJson.size()}")
		context.println("Tests for rerun : ${responseJson}")
	}

	public void abortZafiraTestRun(String uuid, String comment) {
		if(developMode){
			return
		}
        def parameters = [customHeaders: [[name: 'Authorization',
                                           value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: "{\"comment\": \"${comment}\"}",
						  validResponseCodes: "200:401",
						  url: this.serviceURL + "/api/tests/runs/abort?ciRunId=${uuid}"]

        def response = sendRequest(parameters)
		checkStatus(response, parameters)
	}

    public void sendTestRunResultsEmail(String uuid, String emailList, String filter) {
		if(developMode){
			return
		}
		def parameters = [customHeaders: [[name: 'Authorization',
										   value: "${authToken}"]],
						  contentType: 'APPLICATION_JSON',
						  httpMode: 'POST',
						  requestBody: "{\"recipients\": \"${emailList}\"}",
						  validResponseCodes: "200:401",
						  url: this.serviceURL + "/api/tests/runs/${uuid}/email?filter=${filter}"]
		def response = sendRequest(parameters)
		checkStatus(response, parameters)
    }

	public String exportZafiraReport(String uuid) {
		if(developMode){
			return ""
		}
		def parameters = [customHeaders: [[name: 'Authorization',
										   value: "${authToken}"]],
						  contentType: 'APPLICATION_JSON',
						  httpMode: 'GET',
						  validResponseCodes: "200:401",
						  url: this.serviceURL + "/api/tests/runs/${uuid}/export"]

		def response = sendRequest(parameters)
		response = checkStatus(response, parameters)
		if (response == 401) {
			return ""
		}
		//context.println("exportZafiraReport response: ${response.content}")
		return response.content
	}

	protected void printStackTrace(Exception ex) {
		context.println("exception: " + ex.getMessage())
		context.println("exception class: " + ex.getClass().getName())
		context.println("stacktrace: " + Arrays.toString(ex.getStackTrace()))
	}

	/** Checks if auth token exists and if it doesn't generates it anew using refreshToken */
	protected def sendRequest(requestParams) {
		getAuthToken()
		replaceToken(requestParams)
		def response = null
		/** Catches exceptions in every http call */
		try {
			response = context.httpRequest requestParams
		} catch (Exception ex) {
			printStackTrace(ex)
		}
		return response
	}

	protected def getAuthToken() {
		if(authToken == null){
			getZafiraAuthToken(refreshToken)
		}
	}

	/** Replaces token value just in case it was incorrect before requestParams were passed to the sendRequest method */
	protected def replaceToken(requestParams) {
		for (header in requestParams.get("customHeaders")) {
			if(header.name == "Authorization"){
				header.value = authToken
				break
			}
		}
	}

	/** Checks if response got an exception (null in this case) or unauthorized response (401) */
	protected def checkStatus(response, parameters) {
		if(response != null && response.status == 401) {
			authToken = null
			response = sendRequest(parameters)
		}
		return response
	}

	/** Generates authToken using refreshToken*/
	protected void getZafiraAuthToken(String refreshToken) {
		//context.println "refreshToken: " + refreshToken
		def response = context.httpRequest contentType: 'APPLICATION_JSON',
				httpMode: 'POST',
				requestBody: "{\"refreshToken\": \"${refreshToken}\"}",
				url: this.serviceURL + "/api/auth/refresh"

		// reread new accessToken and auth type
		def properties = (Map) new JsonSlurper().parseText(response.getContent())

		//new accessToken in response is authToken
		def accessToken = properties.get("accessToken")
		def type = properties.get("type")

		authToken = type + " " + accessToken
	}
}
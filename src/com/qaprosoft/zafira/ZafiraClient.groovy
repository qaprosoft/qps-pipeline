package com.qaprosoft.zafira

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.qaprosoft.jenkins.pipeline.Configurator

class ZafiraClient {

	private String serviceURL
	private String token
	private def context
	private boolean isAvailable

	public ZafiraClient(context, String url, Boolean developMode) {
		this.context = context
		serviceURL = Configurator.get(Configurator.Parameter.ZAFIRA_SERVICE_URL)
		refreshToken = Configurator.get(Configurator.Parameter.ZAFIRA_ACCESS_TOKEN)
		developMode = Configurator.get("develop") ? Configurator.get("develop").toBoolean() : false
	}

	public void queueZafiraTestRun(String uuid) {
		if(developMode){
			return
		}
		if (isTokenExpired()) {
			getZafiraAuthToken(refreshToken)
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
		if(!response){
			return
		}
        String formattedJSON = JsonOutput.prettyPrint(response.content)
        context.println("Queued TestRun: ${formattedJSON}")
    }

	public void smartRerun() {
		if(developMode){
			return
		}
		if (isTokenExpired()) {
			getZafiraAuthToken(refreshToken)
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
		if(!response){
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
		if (isTokenExpired()) {
			getZafiraAuthToken(refreshToken)
		}
        def parameters = [customHeaders: [[name: 'Authorization',
                                           value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: "{\"comment\": \"${comment}\"}",
						  validResponseCodes: "200:401",
						  url: this.serviceURL + "/api/tests/runs/abort?ciRunId=${uuid}"]

        sendRequest(parameters)
	}

    public void sendTestRunResultsEmail(String uuid, String emailList, String filter) {
		if(developMode){
			return
		}
		if (isTokenExpired()) {
			getZafiraAuthToken(refreshToken)
		}
		def parameters = [customHeaders: [[name: 'Authorization',
										   value: "${authToken}"]],
						  contentType: 'APPLICATION_JSON',
						  httpMode: 'POST',
						  requestBody: "{\"recipients\": \"${emailList}\"}",
						  validResponseCodes: "200:401",
						  url: this.serviceURL + "/api/tests/runs/${uuid}/email?filter=${filter}"]
		sendRequest(parameters)
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
		if(!response){
			return ""
		}
		//context.println("exportZafiraReport response: ${response.content}")
		return response.content
	}

	/** Sends httpRequest using passed parameters */
	protected def sendRequest(requestParams) {
		def response = null
		/** Catches exceptions in every http call */
		try {
			response = context.httpRequest requestParams
		} catch (Exception ex) {
			printStackTrace(ex)
		}
		return response
	}

	protected boolean isTokenExpired() {
		return authToken == null || System.currentTimeMillis() > tokenExpTime
	}

	/** Generates authToken using refreshToken*/
	protected void getZafiraAuthToken(String refreshToken) {
		//context.println "refreshToken: " + refreshToken
		def parameters = [contentType: 'APPLICATION_JSON',
						  httpMode: 'POST',
						  requestBody: "{\"refreshToken\": \"${refreshToken}\"}",
						  url: this.serviceURL + "/api/auth/refresh"]
		def response = sendRequest(parameters)
		def properties = (Map) new JsonSlurper().parseText(response.getContent())
		authToken = properties.get("type") + " " + properties.get("accessToken")
		tokenExpTime = System.currentTimeMillis() + 290 * 60 * 1000
	}

	protected void printStackTrace(Exception ex) {
		context.println("exception: " + ex.getMessage())
		context.println("exception class: " + ex.getClass().getName())
		context.println("stacktrace: " + Arrays.toString(ex.getStackTrace()))
	}
}
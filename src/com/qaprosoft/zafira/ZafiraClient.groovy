package com.qaprosoft.zafira

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.qaprosoft.jenkins.pipeline.Configurator

class ZafiraClient {

	private String serviceURL
	private String refreshToken
	private static String authToken
	private def context
	private boolean isAvailable = true

	public ZafiraClient(context) {
		this.context = context
		initZafiraClient()
	}

	@NonCPS
	public void initZafiraClient() {
		this.serviceURL = Configurator.get(Configurator.Parameter.ZAFIRA_SERVICE_URL)
		context.println "zafiraUrl: " + serviceURL
		this.refreshToken = Configurator.get(Configurator.Parameter.ZAFIRA_ACCESS_TOKEN)
	}

	protected def getAuthToken() {
		if(authToken == null){
			getZafiraAuthToken(refreshToken)
		}
	}

    protected def replaceToken(requestParams) {
        for (header in requestParams.get("customHeaders")) {
            if(header.name == "Authorization"){
                header.value = authToken
                break
            }
        }
    }

	protected def sendRequest(requestParams) {
		getAuthToken()
        replaceToken(requestParams)
		def response = context.httpRequest requestParams
		return response
	}

	public void getZafiraAuthToken(String refreshToken) {

		context.println "refreshToken: " + refreshToken

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

	public void queueZafiraTestRun(String uuid) {

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
						  url: this.serviceURL + "/api/tests/runs/queue"]

		def response = sendRequest(parameters)
		if(response.status == 401) {
			authToken = null
			response = sendRequest(parameters)
		}
        String formattedJSON = JsonOutput.prettyPrint(response.content)
        context.println("Queued TestRun: ${formattedJSON}")
    }

	public void smartRerun() {

		def parameters = [customHeaders: [[name: 'Authorization',
										  value: "${authToken}"]],
						 contentType: 'APPLICATION_JSON',
						 httpMode: 'POST',
						 requestBody: "{\"owner\": \"${Configurator.get("ci_user_id")}\", \
                                        \"upstreamJobId\": \"${Configurator.get("ci_job_id")}\", \
                                        \"upstreamJobBuildNumber\": \"${Configurator.get("ci_parent_build")}\", \
                                        \"scmUrl\": \"${Configurator.get("scm_url")}\", \
                                        \"hashcode\": \"${Configurator.get("hashcode")}\"}",
						 url: this.serviceURL + "/api/tests/runs/rerun/jobs?doRebuild=${Configurator.get("doRebuild")}&rerunFailures=${Configurator.get("rerunFailures")}",
						 timeout: 300000]

		context.println "request body: " + parameters.get("requestBody")

		def response = sendRequest(parameters)
		if(response.status == 401) {
			authToken = null
			response = sendRequest(parameters)
		}

		def responseJson = new JsonSlurper().parseText(response.content)

		context.println("Results : ${responseJson.size()}")
		context.println("Tests for rerun : ${responseJson}")
	}

	public void abortZafiraTestRun(String uuid, String comment) {
		getAuthToken()
		context.httpRequest customHeaders: [[name: 'Authorization', \
            value: "${authToken}"]], \
	    contentType: 'APPLICATION_JSON', \
	    httpMode: 'POST', \
	    requestBody: "{\"comment\": \"${comment}\"}", \
            url: this.serviceURL + "/api/tests/runs/abort?ciRunId=${uuid}"

	}

    void sendTestRunResultsEmail(String uuid, String emailList, String filter) {
		getAuthToken()

        context.httpRequest customHeaders: [[name: 'Authorization',  \
             value: "${authToken}"]],  \
	     contentType: 'APPLICATION_JSON',  \
	     httpMode: 'POST',  \
	     requestBody: "{\"recipients\": \"${emailList}\"}",  \
             url: this.serviceURL + "/api/tests/runs/${uuid}/email?filter=${filter}"
    }

	public String exportZafiraReport(String uuid) {

		getAuthToken()

		def response = context.httpRequest customHeaders: [[name: 'Authorization', \
			value: "${authToken}"]], \
		contentType: 'APPLICATION_JSON', \
		httpMode: 'GET', \
			url: this.serviceURL + "/api/tests/runs/${uuid}/export"
			
		//context.println("exportZafiraReport response: ${response.content}")
		return response.content
	}
}
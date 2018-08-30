package com.qaprosoft.zafira

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.qaprosoft.jenkins.pipeline.Configurator

class ZafiraClient {

	private String serviceURL
	private String refreshToken
	private String authToken
	private def context
	private boolean isAvailable
	private boolean developMode

	public ZafiraClient(context) {
		this.context = context
	}


	public void initZafiraClient() {
		this.serviceURL = Configurator.get(Configurator.Parameter.ZAFIRA_SERVICE_URL)
		context.println "zafiraUrl: " + serviceURL
		this.refreshToken = Configurator.get(Configurator.Parameter.ZAFIRA_ACCESS_TOKEN)
		this.developMode = Configurator.get("develop") ? Configurator.get("develop").toBoolean() : false
		getZafiraAuthToken(refreshToken)
	}

	public boolean isAvailable() {
		boolean isAvailable = true

		return isAvailable
	}

	public void getZafiraAuthToken(String refreshToken) {
		if (developMode) {
			return
		}
		context.println "refreshToken: " + refreshToken
		def response = context.httpRequest contentType: 'APPLICATION_JSON', \
			httpMode: 'POST', \
			requestBody: "{\"refreshToken\": \"${refreshToken}\"}", \
			url: this.serviceURL + "/api/auth/refresh"

		// reread new accessToken and auth type
		def properties = (Map) new JsonSlurper().parseText(response.getContent())
		//new accessToken in response is authToken
		def accessToken = properties.get("accessToken")
		def type = properties.get("type")

		this.authToken = type + " " + accessToken
		//context.println("${this.authToken}")
	}

	public void queueZafiraTestRun(String uuid) {
		if (!isAvailable) {
			return
		}
		String jobName = Configurator.get(Configurator.Parameter.JOB_BASE_NAME)
		String buildNumber = Configurator.get(Configurator.Parameter.BUILD_NUMBER)

		String branch = Configurator.get("branch")
		String _env = Configurator.get("env")

		String ciParentUrl = Configurator.get("ci_parent_url")
		String ciParentBuild = Configurator.get("ci_parent_build")

        def response = context.httpRequest customHeaders: [[name: 'Authorization', \
            value: "${authToken}"]], \
	    contentType: 'APPLICATION_JSON', \
	    httpMode: 'POST', \
	    requestBody: "{\"jobName\": \"${jobName}\", \"buildNumber\": \"${buildNumber}\", \"branch\": \"${branch}\", \"env\": \"${_env}\", \"ciRunId\": \"${uuid}\", \"ciParentUrl\": \"${ciParentUrl}\", \"ciParentBuild\": \"${ciParentBuild}\"}", \
            url: this.serviceURL + "/api/tests/runs/queue"
			
        String formattedJSON = JsonOutput.prettyPrint(response.content)
        context.println("Queued TestRun: ${formattedJSON}")
    }

	public void smartRerun() {
		if (!isAvailable) {
			return
		}
		String upstreamJobId = Configurator.get("ci_job_id")
		String upstreamJobBuildNumber = Configurator.get("ci_parent_build")
		String scmUrl = Configurator.get("scm_url")
		String ciUserId = Configurator.get("ci_user_id")
		String hashcode = Configurator.get("hashcode")
		String doRebuild = Configurator.get("doRebuild")
		String rerunFailures = Configurator.get("rerunFailures")

		def response = context.httpRequest customHeaders: [[name: 'Authorization',   \
              value: "${authToken}"]],   \
	      contentType: 'APPLICATION_JSON',   \
	      httpMode: 'POST',   \
	      requestBody: "{\"owner\": \"${ciUserId}\", \"upstreamJobId\": \"${upstreamJobId}\", \"upstreamJobBuildNumber\": \"${upstreamJobBuildNumber}\", " +
				"\"scmUrl\": \"${scmUrl}\", \"hashcode\": \"${hashcode}\"}",   \
                  url: this.serviceURL + "/api/tests/runs/rerun/jobs?doRebuild=${doRebuild}&rerunFailures=${rerunFailures}",   \
                  timeout: 300000

		def responseJson = new JsonSlurper().parseText(response.content)

		context.println("Results : ${responseJson.size()}")
		context.println("Tests for rerun : ${responseJson}")
	}

	public void abortZafiraTestRun(String uuid, String comment) {
		if (!isAvailable) {
			return 
		}

		context.httpRequest customHeaders: [[name: 'Authorization', \
            value: "${authToken}"]], \
	    contentType: 'APPLICATION_JSON', \
	    httpMode: 'POST', \
	    requestBody: "{\"comment\": \"${comment}\"}", \
            url: this.serviceURL + "/api/tests/runs/abort?ciRunId=${uuid}"

	}

    void sendTestRunResultsEmail(String uuid, String emailList, String filter) {
        if (!isAvailable) {
            return
        }

        context.httpRequest customHeaders: [[name: 'Authorization',  \
             value: "${authToken}"]],  \
	     contentType: 'APPLICATION_JSON',  \
	     httpMode: 'POST',  \
	     requestBody: "{\"recipients\": \"${emailList}\"}",  \
             url: this.serviceURL + "/api/tests/runs/${uuid}/email?filter=${filter}"
    }

	public String exportZafiraReport(String uuid) {
		if (!isAvailable) {
			return ""
		}

		def response = context.httpRequest customHeaders: [[name: 'Authorization', \
			value: "${authToken}"]], \
		contentType: 'APPLICATION_JSON', \
		httpMode: 'GET', \
			url: this.serviceURL + "/api/tests/runs/${uuid}/export"
			
		//context.println("exportZafiraReport response: ${response.content}")
		return response.content
	}
}
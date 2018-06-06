package com.qaprosoft.zafira

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class ZafiraClient {
	private String serviceURL;
	private String token;
	private def context;
	private boolean isAvailable;

	public ZafiraClient(context, String url, Boolean developMode) {
		this.context = context;
		this.serviceURL = url;
		context.echo "zafiraUrl: ${serviceURL}"
		
		if (developMode) {
			isAvailable = false
		} else {
  			//TODO: execute ping call to zafira "/api/status"
            isAvailable = true
		}

	}
	
	public boolean isAvailable() {
		return this.isAvailable
	}

	public String getZafiraAuthToken(String accessToken) {
		if (!isAvailable) {
			return ""
		}
		context.echo "accessToken: ${accessToken}"
		def response = context.httpRequest \
	    	contentType: 'APPLICATION_JSON', \
			httpMode: 'POST', \
			requestBody: "{\"refreshToken\": \"${accessToken}\"}", \
			url: this.serviceURL + "/api/auth/refresh"

		// reread new accessToken and auth type
		def properties = (Map) new JsonSlurper().parseText(response.getContent())

		//new accessToken in response is authToken
		def authToken = properties.get("accessToken")
		def type = properties.get("type")

		this.token = "${type} ${authToken}"
		context.echo "${this.token}"
		return this.token
	}

	public void queueZafiraTestRun(String uuid, jobVars, jobParams) {
		if (!isAvailable) {
			return
		}
		String jobName = jobVars.get("JOB_BASE_NAME")
		String buildNumber = jobVars.get("BUILD_NUMBER")

		String branch = jobParams.get("branch")
		String _env = jobParams.get("env")

		String ciParentUrl = jobParams.get("ci_parent_url")
		String ciParentBuild = jobParams.get("ci_parent_build")

        def response = context.httpRequest customHeaders: [[name: 'Authorization', \
            value: "${token}"]], \
	    contentType: 'APPLICATION_JSON', \
	    httpMode: 'POST', \
	    requestBody: "{\"jobName\": \"${jobName}\", \"buildNumber\": \"${buildNumber}\", \"branch\": \"${branch}\", \"env\": \"${_env}\", \"ciRunId\": \"${uuid}\", \"ciParentUrl\": \"${ciParentUrl}\", \"ciParentBuild\": \"${ciParentBuild}\"}", \
            url: this.serviceURL + "/api/tests/runs/queue"
			
        String formattedJSON = JsonOutput.prettyPrint(response.content)
        context.echo "Queued TestRun: ${formattedJSON}"
    }

	public void smartRerun(jobParams) {
		if (!isAvailable) {
			return
		}
        String ciParentUrl = jobParams.get("ci_parent_url")
		String ciParentBuild = jobParams.get("ci_parent_build")
        String gitUrl = jobParams.get("git_url")
		String ciUserId = jobParams.get("ci_user_id")
		String failurePercent = jobParams.get("failurePercent")
		String hashcode = jobParams.get("hashcode")
		String doRebuild = jobParams.get("doRebuild")
		String rerunFailures = jobParams.get("rerunFailures")

		context.echo "Rebuild parameters:"
		context.echo "ci_parent_url : ${ciParentUrl}"
		context.echo "ci_parent_build : ${ciParentBuild}"
		context.echo "git_url : ${gitUrl}"
		context.echo "ci_user_id : ${ciUserId}"
		context.echo "failurePercent : ${failurePercent}"
		context.echo "hashcode : ${hashcode}"
		context.echo "doRebuild : ${doRebuild}"
		context.echo "rerunFailures : ${rerunFailures}"

		def response = context.httpRequest customHeaders: [[name: 'Authorization', \
            value: "${token}"]], \
	    contentType: 'APPLICATION_JSON', \
	    httpMode: 'POST', \
	    requestBody: "{\"owner\": \"${ciUserId}\", \"upstreamJobUrl\": \"${ciParentUrl}\", \"upstreamJobBuildNumber\": \"${ciParentBuild}\", " +
                "\"scmUrl\": \"${gitUrl}\", \"hashcode\": \"${hashcode}\", \"failurePercent\": \"${failurePercent}\"}", \
                url: this.serviceURL + "/api/tests/runs/rerun/jobs?doRebuild=${doRebuild}&rerunFailures=${rerunFailures}"

		def responseJson = new JsonSlurper().parseText(response.content)
        context.echo "Tests for rerun : ${responseJson}"
	}

	public void abortZafiraTestRun(String uuid, String comment) {
		if (!isAvailable) {
			return 
		}
		context.httpRequest customHeaders: [[name: 'Authorization', \
            value: "${token}"]], \
	    contentType: 'APPLICATION_JSON', \
	    httpMode: 'POST', \
	    requestBody: "{\"comment\": \"${comment}\"}", \
            url: this.serviceURL + "/api/tests/runs/abort?ciRunId=${uuid}"
	}
}
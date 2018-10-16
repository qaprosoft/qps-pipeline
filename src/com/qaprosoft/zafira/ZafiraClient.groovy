package com.qaprosoft.zafira

import com.qaprosoft.jenkins.pipeline.Executor
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.qaprosoft.jenkins.pipeline.Configuration

class ZafiraClient {

	private String serviceURL
	private String refreshToken
	private String authToken
	private long tokenExpTime
	private def context

	public ZafiraClient(context) {
		this.context = context
		serviceURL = Configuration.get(Configuration.Parameter.ZAFIRA_SERVICE_URL)
		refreshToken = Configuration.get(Configuration.Parameter.ZAFIRA_ACCESS_TOKEN)
	}

	public void queueZafiraTestRun(String uuid) {
        if(Configuration.get(Configuration.Parameter.QUEUE_REGISTRATION).toBoolean()) {
            if (isTokenExpired()) {
                getZafiraAuthToken(refreshToken)
            }
            def parameters = [customHeaders     : [[name: 'Authorization', value: "${authToken}"]],
                              contentType       : 'APPLICATION_JSON',
                              httpMode          : 'POST',
                              requestBody       : "{\"jobName\": \"${Configuration.get(Configuration.Parameter.JOB_BASE_NAME)}\", \
                                         \"buildNumber\": \"${Configuration.get(Configuration.Parameter.BUILD_NUMBER)}\", \
                                         \"branch\": \"${Configuration.get("branch")}\", \
                                         \"env\": \"${Configuration.get("env")}\", \
                                         \"ciRunId\": \"${uuid}\", \
                                         \"ciParentUrl\": \"${Configuration.get("ci_parent_url")}\", \
                                         \"ciParentBuild\": \"${Configuration.get("ci_parent_build")}\"}",
                              validResponseCodes: "200:401",
                              url               : this.serviceURL + "/api/tests/runs/queue"]

            def response = sendRequest(parameters)
            if (!response) {
                return
            }
            String formattedJSON = JsonOutput.prettyPrint(response.content)
            context.println "Queued TestRun: " + formattedJSON
        }
    }

	public void smartRerun() {
		if (isTokenExpired()) {
			getZafiraAuthToken(refreshToken)
		}
		def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
						 contentType: 'APPLICATION_JSON',
						 httpMode: 'POST',
						 requestBody: "{\"owner\": \"${Configuration.get("ci_user_id")}\", \
                                        \"upstreamJobId\": \"${Configuration.get("ci_job_id")}\", \
                                        \"upstreamJobBuildNumber\": \"${Configuration.get("ci_parent_build")}\", \
                                        \"scmUrl\": \"${Configuration.get("scm_url")}\", \
                                        \"hashcode\": \"${Configuration.get("hashcode")}\"}",
						  validResponseCodes: "200:401",
						  url: this.serviceURL + "/api/tests/runs/rerun/jobs?doRebuild=${Configuration.get("doRebuild")}&rerunFailures=${Configuration.get("rerunFailures")}",
						 timeout: 300000]

		def response = sendRequest(parameters)
		if(!response){
			return
		}
    
		def responseJson = new JsonSlurper().parseText(response.content)
		context.println "Results : " + responseJson.size()
		context.println "Tests for rerun: " + responseJson
	}

	public void abortTestRun(String uuid, currentBuild) {
		currentBuild.result = 'FAILURE'
		def failureReason = "undefined failure"

		String buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
		String jobBuildUrl = Configuration.get(Configuration.Parameter.JOB_URL) + buildNumber
		String jobName = Configuration.get(Configuration.Parameter.JOB_NAME)
		String env = Configuration.get("env")

		def bodyHeader = "<p>Unable to execute tests due to the unrecognized failure: ${jobBuildUrl}</p>"
		def subject = Executor.getFailureSubject("UNRECOGNIZED FAILURE", jobName, env, buildNumber)
		def failureLog = ""

		if (currentBuild.rawBuild.log.contains("COMPILATION ERROR : ")) {
			bodyHeader = "<p>Unable to execute tests due to the compilation failure. ${jobBuildUrl}</p>"
			subject = Executor.getFailureSubject("COMPILATION FAILURE", jobName, env, buildNumber)
			failureLog = Executor.getLogDetailsForEmail(currentBuild, "ERROR")
			failureReason = URLEncoder.encode(failureLog, "UTF-8")
		} else  if (currentBuild.rawBuild.log.contains("Cancelling nested steps due to timeout")) {
			currentBuild.result = 'ABORTED'
			bodyHeader = "<p>Unable to continue tests due to the abort by timeout ${jobBuildUrl}</p>"
			subject = Executor.getFailureSubject("TIMED OUT", jobName, env, buildNumber)
			failureReason = "Aborted by timeout"
		} else if (currentBuild.rawBuild.log.contains("BUILD FAILURE")) {
			bodyHeader = "<p>Unable to execute tests due to the build failure. ${jobBuildUrl}</p>"
			subject = Executor.getFailureSubject("BUILD FAILURE", jobName, env, buildNumber)
			failureLog = Executor.getLogDetailsForEmail(currentBuild, "ERROR")
			failureReason = URLEncoder.encode("BUILD FAILURE:\n" + failureLog, "UTF-8")
		} else  if (currentBuild.rawBuild.log.contains("Aborted by ")) {
			currentBuild.result = 'ABORTED'
			bodyHeader = "<p>Unable to continue tests due to the abort by " + Executor.getAbortCause(currentBuild) + " ${jobBuildUrl}</p>"
			subject = Executor.getFailureSubject("ABORTED", jobName, env, buildNumber)
			failureReason = "Aborted by " + Executor.getAbortCause(currentBuild)
		}

		if (isTokenExpired()) {
			getZafiraAuthToken(refreshToken)
		}
        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: "{\"comment\": \"${failureReason}\"}",
						  validResponseCodes: "200:404",
						  url: this.serviceURL + "/api/tests/runs/abort?ciRunId=${uuid}"]

        def response = sendRequest(parameters)
        def emailList = Configuration.get(Configuration.Parameter.ADMIN_EMAILS)
        //TODO: append to emailList suitOwner and suiteRunner
        //TODO: think about separate endpoint for negative email
        if(response.status == 200){
            sendEmail(uuid, emailList, "all")
//            sendEmailFailure(uuid, emailList)
        } else {
            //Explicitly send email via Jenkins (emailext) as nothing is registered in Zafira
            def body = bodyHeader + """<br>Rebuild: ${jobBuildUrl}/rebuild/parameterized<br>
		${zafiraReport}: ${jobBuildUrl}/${zafiraReport}<br>
				Console: ${jobBuildUrl}/console<br>${failureLog}"""
            context.emailext Executor.getEmailParams(body, subject, emailList)

        }
    }

    public void sendEmail(String uuid, String emailList, String filter) {

		if (isTokenExpired()) {
			getZafiraAuthToken(refreshToken)
		}
		def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
						  contentType: 'APPLICATION_JSON',
						  httpMode: 'POST',
						  requestBody: "{\"recipients\": \"${emailList}\"}",
						  validResponseCodes: "200:401",
						  url: this.serviceURL + "/api/tests/runs/${uuid}/email?filter=${filter}"]
		sendRequest(parameters)
    }

//    public void sendEmailFailure(String uuid, String emailList) {
//        //TODO: determine runner/owner sending process
//        if (isTokenExpired()) {
//            getZafiraAuthToken(refreshToken)
//        }
//        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
//                          contentType: 'APPLICATION_JSON',
//                          httpMode: 'POST',
//                          requestBody: "{\"recipients\": \"${emailList}\"}",
//                          validResponseCodes: "200:401",
//                          url: this.serviceURL + "/api/tests/runs/${uuid}/emailfailure"]
//        sendRequest(parameters)
//    }

	public String exportZafiraReport(String uuid) {
		def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
						  contentType: 'APPLICATION_JSON',
						  httpMode: 'GET',
						  validResponseCodes: "200:401",
						  url: this.serviceURL + "/api/tests/runs/${uuid}/export"]

		def response = sendRequest(parameters)
		if(!response){
			return ""
		}
		//context.println "exportZafiraReport response: " + response.content
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
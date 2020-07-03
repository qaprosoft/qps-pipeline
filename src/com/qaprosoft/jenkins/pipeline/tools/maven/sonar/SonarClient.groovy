package com.qaprosoft.jenkins.pipeline.tools.maven.sonar

import com.qaprosoft.jenkins.pipeline.integration.HttpClient

class SonarClient extends HttpClient {

	private String serviceUrl

	SonarClient(context) {
		super(context)
		serviceUrl = context.env.getEnvironment().get("SONAR_URL")
	}

	protected def getServerStatus() {
		def parameters = [	contentType			: 'APPLICATION_JSON',
							httpMode			: 'GET',
							validResponseCodes  : '200',
							url 				: serviceUrl + '/api/system/status' ]
		return sendRequestFormatted(parameters)
	}

	protected boolean isAvailable() {
		return "UP".equals(getServerStatus()?.get("status"))
	}
	
}
package com.qaprosoft.jenkins.pipeline.tools.maven.sonar

import com.qaprosoft.jenkins.pipeline.integration.HttpClient
import com.qaprosoft.jenkins.pipeline.Configuration

class SonarClient extends HttpClient {

	private String serviceUrl

	SonarClient(context) {
		super(context)
	}

	protected def getServerStatus() {
		def parameters = [	contentType			: 'APPLICATION_JSON',
							httpMode			: 'GET',
							validResponseCodes  : '200',
							url 				: serviceUrl + '/api/system/status' ]
		return sendRequestFormatted(parameters)
	}

	protected boolean isAvailable() {
		return "UP".equals(getServerStatus().get("status"))
	}

	public void setServiceUrl(String url) {
		serviceUrl = url
	}

	public String getServiceUrl() {
		return serviceUrl
	}
}
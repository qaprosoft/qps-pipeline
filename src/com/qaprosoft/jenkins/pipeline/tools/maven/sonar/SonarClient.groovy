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
		def status = getServerStatus().find() {it.status}
		return status.equals('UP') ? true : false
	}

	public void setServiceUrl(String url) {
		serviceUrl = url
	}

	public String getServiceUrl() {
		return serviceUrl
	}
}
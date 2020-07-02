package com.qaprosoft.jenkins.pipeline.tools.maven.sonar

import com.qaprosoft.jenkins.pipeline.integration.HttpClient

class SonarClient extends HttpClient {

	String serviceUrl

	SonarClient(context) {
		super(context)
	}

	protected def getServerStatus() {
		def parameters = [	contentType			: 'APLICATION_JSON',
							httpMode			: 'GET',
							validResponseCodes  : '200',
							url 				: serviceUrl + '/api/system/status' ]
		return sendRequestFormatted(parameters)
	}

}
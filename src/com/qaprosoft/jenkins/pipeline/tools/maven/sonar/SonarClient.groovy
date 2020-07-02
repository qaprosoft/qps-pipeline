package com.qaprosoft.jenkins.pipeline.tools.maven.sonar

import com.qaprosoft.jenkins.pipeline.integration.HttpClient
import com.qaprosoft.jenkins.pipeline.Configuration

class SonarClient extends HttpClient {

	private String serviceUrl

	SonarClient(context) {
		super(context)
		serviceUrl = Configuration.get('SONAR_URL')
		logger.info("sonar_url: " + serviceUrl)
	}

	protected def getServerStatus() {
		def parameters = [	contentType			: 'APLICATION_JSON',
							httpMode			: 'GET',
							validResponseCodes  : '200',
							url 				: serviceUrl + 'api/system/status' ]
		return sendRequestFormatted(parameters)
	}

}
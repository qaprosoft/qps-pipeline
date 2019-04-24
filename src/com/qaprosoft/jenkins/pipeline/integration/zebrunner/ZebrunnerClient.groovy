package com.qaprosoft.jenkins.pipeline.integration.zebrunner

import com.qaprosoft.jenkins.pipeline.integration.HttpClient
import groovy.json.JsonBuilder

class ZebrunnerClient extends HttpClient{

    public ZebrunnerClient(context) {
        super(context)
    }

	public def sendInitResult(integrationParameters, tenancyName, authToken, callbackURL, initialized) {
		JsonBuilder jsonBuilder = new JsonBuilder()
		jsonBuilder tenancyName: tenancyName,
				authToken: authToken,
				initialized: initialized,
				integrationParameters: integrationParameters
		logger.info("REQUEST: " + jsonBuilder.toPrettyString())
		def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
						  contentType: 'APPLICATION_JSON',
						  httpMode: 'POST',
						  requestBody: "${jsonBuilder}",
						  url: callbackURL]
		return sendRequestFormatted(parameters)
	}
}
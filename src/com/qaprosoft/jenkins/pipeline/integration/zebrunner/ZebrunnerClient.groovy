package com.qaprosoft.jenkins.pipeline.integration.zebrunner

import com.qaprosoft.jenkins.pipeline.integration.HttpClient
import groovy.json.JsonBuilder

class ZebrunnerClient extends HttpClient {

    public ZebrunnerClient(context) {
        super(context)
    }

    public def sendInitResult(integrationParameters, tenancyName, accessToken, callbackURL, initialized) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder tenancyName: tenancyName,
                accessToken: accessToken,
                initialized: initialized,
                integrationParameters: integrationParameters
        logger.info("REQUEST: " + jsonBuilder.toPrettyString())
        def parameters = [customHeaders: [[name: 'Authorization', value: "${accessToken}"]],
                          contentType  : 'APPLICATION_JSON',
                          httpMode     : 'POST',
                          requestBody  : "${jsonBuilder}",
                          url          : callbackURL]
        return sendRequestFormatted(parameters)
    }
}
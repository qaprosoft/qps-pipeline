package com.qaprosoft.jenkins.pipeline.integration

import com.qaprosoft.jenkins.BaseObject
import groovy.transform.InheritConstructors

import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

@InheritConstructors
abstract class HttpClient extends BaseObject {

    /** Sends httpRequest using passed parameters */
    protected def sendRequestFormatted(requestParams) {
        def response = sendRequest(requestParams)
        if (response) {
            return getObjectResponse(response)
        }
    }

    protected def sendRequest(requestParams) {
        def response = null
        /** Catches exceptions in every http call */
        try {
            response = context.httpRequest requestParams
        } catch (Exception e) {
            logger.error(printStackTrace(e))
        }
        if (!response || response.status >= 400) {
            // [VD] as we don't remember the reason of making build as FAILURE we commented these lines
//            if (!requestParams.url.contains("queue")) {
//                context.currentBuild.result = BuildResult.FAILURE
//            }
            return
        }
        return response.content
    }

}

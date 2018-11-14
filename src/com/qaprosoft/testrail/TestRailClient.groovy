package com.qaprosoft.testrail

import com.qaprosoft.Utils
import com.qaprosoft.Logger
import com.qaprosoft.jenkins.pipeline.Configuration
import groovy.json.JsonBuilder

class TestRailClient {

    private String serviceURL
    private String authToken
    private def context
    private Logger logger

    public TestRailClient(context) {
        this.context = context
        serviceURL = "https://uacf.testrail.com?/api/v2/"
        logger = new Logger(context)
    }

    public def getRuns(int projectId) {

        def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${user}:${password}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'GET',
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "get_runs/${projectId}"]

        def response = sendRequest(parameters)
        if(!response){
            return ""
        }
        return response.content
    }

    public String addTestRunCustomCases(suite_id, name, assignedto_id, projectID, case_ids) {

        def builder = new JsonBuilder()
        builder suite_id: "${suite_id}",
                name: "${name}",
                assignedto_id: "${assignedto_id}",
                include_all: false,
                case_ids: "${case_ids}"

        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: "${builder.toString()}",
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "add_run/${projectID}"]

        def response = sendRequest(parameters)
        if(!response){
            return ""
        }
        return response.content
    }

    public String addTestRun(suite_id, name, assignedto_id, projectID, insludeAllCases) {

        def builder = new JsonBuilder()
        builder suite_id: "${suite_id}",
                name: "${name}",
                assignedto_id: "${assignedto_id}",
                include_all: "${insludeAllCases}"

        def stringJson = builder.toString()

        def parameters = [customHeaders: [[name: 'Authorization', value: "${authToken}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: "${builder.toString()}",
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "add_run/${projectID}"]

        def response = sendRequest(parameters)
        if(!response){
            return ""
        }
        return response.content
    }

    /** Sends httpRequest using passed parameters */
    protected def sendRequest(requestParams) {
        def response = null
        /** Catches exceptions in every http call */
        try {
            response = context.httpRequest requestParams
        } catch (Exception e) {
            logger.error(Utils.printStackTrace(e))
        }
        return response
    }
}

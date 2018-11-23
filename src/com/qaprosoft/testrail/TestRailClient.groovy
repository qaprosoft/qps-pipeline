package com.qaprosoft.testrail

import static com.qaprosoft.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*
import com.qaprosoft.Logger
import groovy.json.JsonBuilder

class TestRailClient {

    private String serviceURL
    private def context
    private Logger logger

    public TestRailClient(context) {
        this.context = context
        serviceURL = "https://uacf.testrail.com?/api/v2/"
        logger = new Logger(context)
    }

    public def getRuns(createdAfter, createdBy, milestoneId, projectId, suiteId) {
        context.withCredentials([context.usernamePassword(credentialsId:'testrail_creds', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${context.env.USERNAME}:${context.env.PASSWORD}")}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'GET',
                              validResponseCodes: "200:401",
                              url: this.serviceURL + "get_runs/${projectId}&created_after=${createdAfter}&created_by=${createdBy}&milestone_id=${milestoneId}&suite_id=${suiteId}"]
            return sendRequestFormatted(parameters)
        }
    }

    public def getRuns(createdAfter, createdBy, projectId, suiteId) {
        context.withCredentials([context.usernamePassword(credentialsId:'testrail_creds', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${context.env.USERNAME}:${context.env.PASSWORD}")}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'GET',
                              validResponseCodes: "200:401",
                              url: this.serviceURL + "get_runs/${projectId}&created_after=${createdAfter}&created_by=${createdBy}&suite_id=${suiteId}"]
            return sendRequestFormatted(parameters)
        }
    }

    public def addTestRun(suiteId, testRunName, milestoneId, assignedToId, includeAll, caseIds, projectID) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder suite_id: suiteId,
                name: testRunName,
                milestone_id: milestoneId,
                assignedto_id: assignedToId,
                include_all: includeAll,
                case_ids: caseIds
        context.withCredentials([context.usernamePassword(credentialsId:'testrail_creds', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${context.env.USERNAME}:${context.env.PASSWORD}")}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'POST',
                              requestBody: "${jsonBuilder}",
                              validResponseCodes: "200",
                              url: this.serviceURL + "add_run/${projectID}"]
            return sendRequestFormatted(parameters)
        }
    }

    public def addTestRun(suiteId, testRunName, assignedToId, includeAll, caseIds, projectID) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder suite_id: suiteId,
                name: testRunName,
                assignedto_id: assignedToId,
                include_all: includeAll,
                case_ids: caseIds
        context.withCredentials([context.usernamePassword(credentialsId:'testrail_creds', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${context.env.USERNAME}:${context.env.PASSWORD}")}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'POST',
                              requestBody: "${jsonBuilder}",
                              validResponseCodes: "200",
                              url: this.serviceURL + "add_run/${projectID}"]
            return sendRequestFormatted(parameters)
        }
    }

    public def getUserIdByEmail(userEmail) {
        context.withCredentials([context.usernamePassword(credentialsId:'testrail_creds', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            if(isParamEmpty(userEmail)){
                userEmail = context.env.USERNAME
            }
            def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${context.env.USERNAME}:${context.env.PASSWORD}")}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'GET',
                              validResponseCodes: "200:401",
                              url: this.serviceURL + "get_user_by_email&email=${userEmail}"]
            return sendRequestFormatted(parameters)
        }
    }

    public def getMilestones(projectId) {
        context.withCredentials([context.usernamePassword(credentialsId:'testrail_creds', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${context.env.USERNAME}:${context.env.PASSWORD}")}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'GET',
                              validResponseCodes: "200:401",
                              url: this.serviceURL + "get_milestones/${projectId}"]
            return sendRequestFormatted(parameters)
        }
    }

    public def addMilestone(projectId, milestoneName) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder name: milestoneName
        context.withCredentials([context.usernamePassword(credentialsId:'testrail_creds', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${context.env.USERNAME}:${context.env.PASSWORD}")}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'POST',
                              requestBody: "${jsonBuilder}",
                              validResponseCodes: "200:401",
                              url: this.serviceURL + "add_milestone/${projectId}"]
            return sendRequestFormatted(parameters)
        }
    }

    public def addResultsForCases(testRunId, results) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder results: results
        logger.info("REQUEST:\n" + formatJson(results))
        context.withCredentials([context.usernamePassword(credentialsId:'testrail_creds', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${context.env.USERNAME}:${context.env.PASSWORD}")}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'POST',
                              requestBody: "${jsonBuilder}",
                              validResponseCodes: "200:401",
                              url: this.serviceURL + "add_results_for_cases/${testRunId}"]
            return sendRequestFormatted(parameters)
        }
    }

    /** Sends httpRequest using passed parameters */
    protected def sendRequestFormatted(requestParams) {
        def response = sendRequest(requestParams)
        if(response){
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
        if(!response || response.status > 200){
            return
        }
        return response.content
    }

}

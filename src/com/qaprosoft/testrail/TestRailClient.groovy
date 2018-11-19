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

    public def getRuns(projectId, suiteId) {
        context.withCredentials([context.usernamePassword(credentialsId:'testrail_creds', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${context.env.USERNAME}:${context.env.PASSWORD}")}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'GET',
                              validResponseCodes: "200:401",
                              url: this.serviceURL + "get_runs/${projectId}&suite_id=${suiteId}"]
            def response = sendRequest(parameters)

            if(!response){
                return
            }
            return getObjectResponse(response.content)
        }
    }

    public def addTestRun(suite_id, testRunName, milestone_id, assignedto_id, include_all, case_ids, projectID) {

        def builder = new JsonBuilder()
        builder suite_id: suite_id,
                name: testRunName,
                milestone_id: milestone_id,
                assignedto_id: assignedto_id,
                include_all: include_all,
                case_ids: case_ids

        context.withCredentials([context.usernamePassword(credentialsId:'testrail_creds', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${context.env.USERNAME}:${context.env.PASSWORD}")}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'POST',
                              requestBody: "${builder.toString()}",
                              validResponseCodes: "200",
                              url: this.serviceURL + "add_run/${projectID}"]

            def response = sendRequest(parameters)
            if(!response){
                return
            }
            return getObjectResponse(response.content)
        }
    }

    public def getUserIdByEmail(userEmail) {
        context.withCredentials([context.usernamePassword(credentialsId:'testrail_creds', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${context.env.USERNAME}:${context.env.PASSWORD}")}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'GET',
                              validResponseCodes: "200:401",
                              url: this.serviceURL + "get_user_by_email&email=${userEmail}"]
            def response = sendRequest(parameters)

            if(!response){
                return
            }
            return getObjectResponse(response.content)
        }
    }

    public def getMilestones(projectId) {
        context.withCredentials([context.usernamePassword(credentialsId:'testrail_creds', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${context.env.USERNAME}:${context.env.PASSWORD}")}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'GET',
                              validResponseCodes: "200:401",
                              url: this.serviceURL + "get_milestones/${projectId}"]
            def response = sendRequest(parameters)

            if(!response){
                return
            }
            return getObjectResponse(response.content)
        }
    }

    public def addMilestone(projectId, milestoneName) {
        def builder = new JsonBuilder()
        builder name: milestoneName

        context.withCredentials([context.usernamePassword(credentialsId:'testrail_creds', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${context.env.USERNAME}:${context.env.PASSWORD}")}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'POST',
                              requestBody: "${builder.toString()}",
                              validResponseCodes: "200:401",
                              url: this.serviceURL + "add_milestone/${projectId}"]
            def response = sendRequest(parameters)

            if(!response){
                return
            }
            return getObjectResponse(response.content)
        }
    }

    /** Sends httpRequest using passed parameters */
    protected def sendRequest(requestParams) {
        def response = null
        /** Catches exceptions in every http call */
        try {
            response = context.httpRequest requestParams
        } catch (Exception e) {
            logger.error(printStackTrace(e))
        }
        return response
    }
}

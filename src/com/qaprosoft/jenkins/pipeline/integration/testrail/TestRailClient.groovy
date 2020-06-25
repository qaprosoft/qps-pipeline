package com.qaprosoft.jenkins.pipeline.integration.testrail

import com.qaprosoft.jenkins.pipeline.integration.HttpClient

import static com.qaprosoft.jenkins.Utils.*
import groovy.json.JsonBuilder
import com.qaprosoft.jenkins.pipeline.Configuration

/*
 * Prerequisites: valid TESTRAIL_SERVICE_URL, TESTRAIL_USERNAME and TESTRAIL_PASSWORD already defined in Configuration
 */

class TestRailClient extends HttpClient {

    private String serviceURL
    private String username
    private String password
    private boolean isAvailable

    public TestRailClient(context) {
        super(context)

        this.serviceURL = Configuration.get(Configuration.Parameter.TESTRAIL_SERVICE_URL)
        this.username = Configuration.get(Configuration.Parameter.TESTRAIL_USERNAME)
        this.password = Configuration.get(Configuration.Parameter.TESTRAIL_PASSWORD)

        this.isAvailable = !serviceURL.isEmpty() && !this.username.isEmpty() && !this.password.isEmpty()
    }

    public boolean isAvailable() {
        return isAvailable
    }

    public def getRuns(createdAfter, createdBy, milestoneId, projectId, suiteId) {
        def requestArgs = "get_runs/${projectId}&created_after=${createdAfter}&suite_id=${suiteId}"
        if (!isParamEmpty(createdBy)) {
            requestArgs = requestArgs + "&created_by=${createdBy}"
        }
        if (!isParamEmpty(milestoneId)) {
            requestArgs = requestArgs + "&milestone_id=${milestoneId}"
        }
        def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${this.username}:${this.password}")}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'GET',
                          validResponseCodes: "200:401",
                          url: this.serviceURL + requestArgs]
        return sendRequestFormatted(parameters)
    }

    public def getTests(runId) {
        def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${this.username}:${this.password}")}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'GET',
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "get_tests/${runId}"]
        return sendRequestFormatted(parameters)
    }

    public def getCases(projectId, suiteId) {
        def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${this.username}:${this.password}")}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'GET',
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "get_cases/${projectId}&suite_id=${suiteId}"]
        return sendRequestFormatted(parameters)
    }


    public def addTestRun(suiteId, testRunName, milestoneId, assignedToId, includeAll, caseIds, projectID) {
        includeAll = includeAll.toBoolean()
        // default request body without milestone id
        JsonBuilder jsonBuilder = new JsonBuilder()
        if (!includeAll) {
            jsonBuilder suite_id: suiteId,
                    name: testRunName,
                    assignedto_id: assignedToId,
                    include_all: includeAll,
                    case_ids: caseIds
        } else {
            // no need to specify cases if all of them should be registered in test run
            jsonBuilder suite_id: suiteId,
            name: testRunName,
            assignedto_id: assignedToId,
            include_all: includeAll
        }
        
        if (!isParamEmpty(milestoneId)) {
            // insert milestone id into the request body
            jsonBuilder = new JsonBuilder()
            jsonBuilder suite_id: suiteId,
                    name: testRunName,
                    milestone_id: milestoneId,
                    assignedto_id: assignedToId,
                    include_all: includeAll,
                    case_ids: caseIds
        }
        logger.debug("TEST_RUN_TO_ADD:\n" + jsonBuilder.toPrettyString())
        def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${this.username}:${this.password}")}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: "${jsonBuilder}",
                          validResponseCodes: "200",
                          url: this.serviceURL + "add_run/${projectID}"]
        return sendRequestFormatted(parameters)
    }

    public def getUserIdByEmail(userEmail) {
        if (isParamEmpty(userEmail)) {
            userEmail = context.env.USERNAME
        }
        def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${this.username}:${this.password}")}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'GET',
                          validResponseCodes: "200:404",
                          url: this.serviceURL + "get_user_by_email&email=${userEmail}"]
        return sendRequestFormatted(parameters)
    }

    public def getMilestones(projectId) {
        def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${this.username}:${this.password}")}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'GET',
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "get_milestones/${projectId}"]
        return sendRequestFormatted(parameters)
    }

    public def addMilestone(projectId, milestoneName) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder name: milestoneName
        def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${this.username}:${this.password}")}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: "${jsonBuilder}",
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "add_milestone/${projectId}"]
        return sendRequestFormatted(parameters)
    }

    public def addResultsForTests(testRunId, results) {
        if (isParamEmpty(results)) {
            return results
        }
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder results: results
//        logger.debug("REQUEST:\n" + formatJson(jsonBuilder))
        def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${this.username}:${this.password}")}"]],
                          contentType: 'APPLICATION_JSON',
                          httpMode: 'POST',
                          requestBody: "${jsonBuilder}",
                          validResponseCodes: "200:401",
                          url: this.serviceURL + "add_results/${testRunId}"]
        return sendRequestFormatted(parameters)
    }
}

package com.qaprosoft.integration.qtest

import com.qaprosoft.integration.HttpClient
import com.qaprosoft.jenkins.pipeline.Configuration
import groovy.json.JsonBuilder

class QTestClient extends HttpClient{

    private String serviceURL
    private boolean isAvailable

    public QTestClient(context) {
        super(context)
        this.serviceURL = Configuration.get(Configuration.Parameter.QTEST_SERVICE_URL)
        this.isAvailable = !serviceURL.isEmpty()
    }

    public boolean isAvailable() {
        return isAvailable
    }

    public def getCycles(projectId) {
        context.withCredentials([context.string(credentialsId:'qtest_token', variable: 'TOKEN')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'GET',
                              validResponseCodes: "200",
                              url: this.serviceURL + "projects/${projectId}/test-cycles"]
            return sendRequestFormatted(parameters)
        }
    }

    public def addTestSuite(projectId, cycleId, name) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder name: name
        context.withCredentials([context.string(credentialsId:'qtest_token', variable: 'TOKEN')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'POST',
                              requestBody: "${jsonBuilder}",
                              validResponseCodes: "200",
                              url: this.serviceURL + "projects/${projectId}/test-suites?parentId=${cycleId}&parentType=test-cycle"]
            return sendRequestFormatted(parameters)
        }
    }

    public def addTestRun(projectId, suiteId, name) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder name: name,
                test_case: [id: 1, test_steps: [[id: 1]]]
        context.withCredentials([context.string(credentialsId:'qtest_token', variable: 'TOKEN')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'POST',
                              requestBody: "${jsonBuilder}",
                              validResponseCodes: "200:201",
                              url: this.serviceURL + "projects/${projectId}/test-runs?parentId=${suiteId}&parentType=test-suite"]
            return sendRequestFormatted(parameters)
        }
    }

    public def uploadResults(status, projectId, testRunId) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        def req = jsonBuilder status: status
        logger.info("REQUEST2: " + req)
        context.withCredentials([context.string(credentialsId:'qtest_token', variable: 'TOKEN')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'POST',
                              requestBody: "${jsonBuilder}",
                              validResponseCodes: "200",
                              url: this.serviceURL + "projects/${projectId}/test-runs/${testRunId}/auto-test-logs"]
            return sendRequestFormatted(parameters)
        }
    }

}

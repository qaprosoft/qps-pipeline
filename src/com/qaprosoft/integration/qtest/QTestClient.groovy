package com.qaprosoft.integration.qtest

import com.qaprosoft.integration.HttpClient
import com.qaprosoft.integration.zafira.StatusMapper
import com.qaprosoft.jenkins.pipeline.Configuration
import groovy.json.JsonBuilder

import static com.qaprosoft.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

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
        Map step = new HashMap<>()
        step.put("id", 1)
        def stepArray = []
        stepArray.add(step)

        Map caseMap = new HashMap<>()
        caseMap.put("id", 1)
        caseMap.put("test_steps", stepArray)

        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder name: name,
                test_case: caseMap
        context.withCredentials([context.string(credentialsId:'qtest_token', variable: 'TOKEN')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'POST',
                              requestBody: "${jsonBuilder}",
                              validResponseCodes: "200",
                              url: this.serviceURL + "projects/${projectId}/test-runs?parentId=${suiteId}&parentType=test-suite"]
            return sendRequestFormatted(parameters)
        }
    }
}

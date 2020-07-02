package com.qaprosoft.jenkins.pipeline.integration.qtest

import com.qaprosoft.jenkins.pipeline.integration.HttpClient
import com.qaprosoft.jenkins.pipeline.Configuration
import groovy.json.JsonBuilder
import static com.qaprosoft.jenkins.pipeline.Executor.*

/*
 * Prerequisites: valid QTEST_SERVICE_URL and QTEST_ACCESS_TOKEN already defined in Configuration 
 */

class QTestClient extends HttpClient {

    private String serviceURL
    private String token
    private boolean isAvailable

    public QTestClient(context) {
        super(context)
        this.serviceURL = Configuration.get(Configuration.Parameter.QTEST_SERVICE_URL)
        this.token = Configuration.get(Configuration.Parameter.QTEST_ACCESS_TOKEN)
        this.isAvailable = !serviceURL.isEmpty() && !token.isEmpty()
    }

    public boolean isAvailable() {
        return isAvailable
    }

    public def getCycles(projectId) {
        def parameters = [customHeaders     : [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200",
                          url               : this.serviceURL + "projects/${projectId}/test-cycles"]
        return sendRequestFormatted(parameters)
    }

    public def getSubCycles(parentCycleId, projectId) {
        def parameters = [customHeaders     : [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200",
                          url               : this.serviceURL + "projects/${projectId}/test-cycles?parentId=${parentCycleId}&parentType=test-cycle"]
        return sendRequestFormatted(parameters)
    }

    public def getTestSuites(projectId, parentCycleId) {
        def parameters = [customHeaders     : [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200",
                          url               : this.serviceURL + "projects/${projectId}/test-suites?parentId=${parentCycleId}&parentType=test-cycle"]
        return sendRequestFormatted(parameters)
    }

    public def getTestRuns(projectId, parentSuiteId) {
        def parameters = [customHeaders     : [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200",
                          url               : this.serviceURL + "projects/${projectId}/test-runs?parentId=${parentSuiteId}&parentType=test-suite"]
        return sendRequestFormatted(parameters)
    }

    public def getTestRunsSubHierarchy(projectId) {
        def parameters = [customHeaders     : [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200",
                          url               : this.serviceURL + "projects/${projectId}/test-runs/subhierarchy"]
        return sendRequestFormatted(parameters)
    }

    public def getTestCase(projectId, testCaseId) {
        def parameters = [customHeaders     : [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200",
                          url               : this.serviceURL + "projects/${projectId}/test-cases/${testCaseId}"]
        return sendRequestFormatted(parameters)
    }

    public def getModule(projectId, moduleId) {
        def parameters = [customHeaders     : [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200:404",
                          url               : this.serviceURL + "projects/${projectId}/modules/${moduleId}"]
        return sendRequestFormatted(parameters)
    }

    public def getLog(projectId, testRunId) {
        def parameters = [customHeaders     : [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'GET',
                          validResponseCodes: "200",
                          url               : this.serviceURL + "projects/${projectId}/test-runs/${testRunId}/test-logs/last-run"]
        return sendRequestFormatted(parameters)
    }

    public def addTestCycle(projectId, parentCycleId, name) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder name: name
        logger.debug("REQUEST_PARAMS: " + jsonBuilder.toString())
        def parameters = [customHeaders     : [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          requestBody       : "${jsonBuilder}",
                          validResponseCodes: "200",
                          url               : this.serviceURL + "projects/${projectId}/test-cycles?parentId=${parentCycleId}&parentType=test-cycle"]
        return sendRequestFormatted(parameters)
    }

    public def addTestSuite(projectId, parentcCycleId, name) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder name: name
        logger.debug("REQUEST_PARAMS: " + jsonBuilder.toString())
        def parameters = [customHeaders     : [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          requestBody       : "${jsonBuilder}",
                          validResponseCodes: "200",
                          url               : this.serviceURL + "projects/${projectId}/test-suites?parentId=${parentcCycleId}&parentType=test-cycle"]
        return sendRequestFormatted(parameters)
    }

    public def addTestRun(projectId, parentSuiteId, testCaseId, name) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder name: name,
                test_case: [id: testCaseId]
        logger.debug("REQUEST_PARAMS: " + jsonBuilder.toString())
        def parameters = [customHeaders     : [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          requestBody       : "${jsonBuilder}",
                          validResponseCodes: "200:201",
                          url               : this.serviceURL + "projects/${projectId}/test-runs?parentId=${parentSuiteId}&parentType=test-suite"]
        return sendRequestFormatted(parameters)
    }

    public def uploadResults(status, startedAt, finishedAt, testRunId, testRunName, projectId, note) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder exe_start_date: startedAt,
                exe_end_date: finishedAt,
                name: testRunName,
                status: status,
                note: note

        logger.debug("REQUEST_PARAMS: " + formatJson(jsonBuilder))
        def parameters = [customHeaders     : [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'POST',
                          requestBody       : "${jsonBuilder}",
                          validResponseCodes: "200",
                          url               : this.serviceURL + "projects/${projectId}/test-runs/${testRunId}/auto-test-logs"]
        return sendRequestFormatted(parameters)
    }

    public def updateResults(status, startedAt, finishedAt, testRunId, projectId, logsId) {
        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder exe_start_date: startedAt,
                exe_end_date: finishedAt,
                status: status

        logger.debug("REQUEST_PARAMS: " + formatJson(jsonBuilder))
        def parameters = [customHeaders     : [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                          contentType       : 'APPLICATION_JSON',
                          httpMode          : 'PUT',
                          requestBody       : "${jsonBuilder}",
                          validResponseCodes: "200",
                          url               : this.serviceURL + "projects/${projectId}/test-runs/${testRunId}/auto-test-logs/${logsId}"]
        return sendRequestFormatted(parameters)
    }
}

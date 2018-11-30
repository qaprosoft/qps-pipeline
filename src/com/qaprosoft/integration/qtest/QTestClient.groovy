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
        jsonBuilder parentId: cycleId,
                parentType: "test-cycle",
                name: name
        context.withCredentials([context.string(credentialsId:'qtest_token', variable: 'TOKEN')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "bearer ${context.env.TOKEN}"]],
                              contentType: 'APPLICATION_JSON',
                              httpMode: 'POST',
                              requestBody: "${jsonBuilder}",
                              validResponseCodes: "200",
                              url: this.serviceURL + "projects/${projectId}/test-suites"]
            return sendRequestFormatted(parameters)
        }
    }

    public def getToken() {
        context.withCredentials([context.usernamePassword(credentialsId:'qtest_creds', usernameVariable:'USERNAME', passwordVariable:'PASSWORD')]) {
            def parameters = [customHeaders: [[name: 'Authorization', value: "Basic ${encodeToBase64("${Configuration.get("SITE_NAME")}:")}"]],
                              contentType: 'APPLICATION_FORM',
                              httpMode: 'POST',
                              validResponseCodes: "200:401",
                              url: "https://${Configuration.get("SITE_NAME")}/#/oath/token?grant_type=password&username=${context.env.USERNAME}&password=${context.env.PASSWORD}"]
            return sendRequestFormatted(parameters)
        }
    }
}

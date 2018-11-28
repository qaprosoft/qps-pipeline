package com.qaprosoft.integration.qtest

import com.qaprosoft.Logger
import com.qaprosoft.integration.HttpClient
import com.qaprosoft.jenkins.pipeline.Configuration

class QTestClient extends HttpClient{

    private String serviceURL
    private def context
    private boolean isAvailable

    public QTestClient(context) {
        super(context)
        this.serviceURL = Configuration.get(Configuration.Parameter.TESTRAIL_SERVICE_URL)
        this.isAvailable = !serviceURL.isEmpty()
    }

    public boolean isAvailable() {
        return isAvailable
    }


}

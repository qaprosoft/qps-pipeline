package com.qaprosoft.integration.qtest

import com.qaprosoft.Logger
import com.qaprosoft.integration.zafira.IntegrationTag
import com.qaprosoft.integration.zafira.ZafiraClient
import static com.qaprosoft.jenkins.pipeline.Executor.*

class QTestUpdater {

    private def context
    private ZafiraClient zc
    private QTestClient qTestClient
    private Logger logger
    private integration

    public QTestUpdater(context) {
        this.context = context
        zc = new ZafiraClient(context)
        qTestClient = new QTestClient(context)
        logger = new Logger(context)
    }

    public void updateTestRun(uuid, isRerun) {
        if (!qTestClient.isAvailable()) {
            // do nothing
            return
        }
        // export all tag related metadata from Zafira
        integration = zc.exportTagData(uuid, IntegrationTag.QTEST_TESTCASE_UUID)
        logger.info("INTEGRATION_INFO:\n" + formatJson(integration))

        if(isEmpty(integration, "Nothing to update in QTest.")){
            return
        }
        // convert uuid to project_id, suite_id and testcases related maps
        integration = parseTagData()
        if(isEmpty(integration.projectId, "Unable to detect QTest project_id!\n" + formatJson(integration))){
            return
        }
        def cycleId = getCycleId()
        if(isEmpty(cycleId, "No dedicated QTest cycle detected.")){
            return
        }
        def suiteId = getTestSuiteId(cycleId)
        if(isParamEmpty(suiteId)){
            def testSuite = qTestClient.addTestSuite(integration.projectId, cycleId, integration.platform)
            logger.info("SUITE: " + formatJson(testSuite))
            if(isEmpty(testSuite, "Unable to register QTest testSuite.")){
                return
            }
            suiteId = testSuite.id
        }
        integration.caseResultMap.values().each { testCase ->
            def testRun
            if(!isRerun){
                testRun = qTestClient.addTestRun(integration.projectId, suiteId, testCase.case_id, integration.testRunName)
                if(isEmpty(testRun, "Unable to add QTest testRun.")){
                    return
                }
                def results = qTestClient.uploadResults(testCase.status, new Date(integration.startedAt),  new Date(integration.finishedAt), testRun.id, testRun.name,  integration.projectId)
                if(isEmpty(results, "Unable to add results for QTest TestRun.")){
                    return
                }
                logger.debug("UPLOADED_RESULTS: " + formatJson(results))
            } else {
                testRun = getTestRun(suiteId, testCase.case_id)
//                logger.debug("TEST_RUN: " + formatJson(testRun))
                if(isEmpty(testRun, "Unable to get QTest testRun.")){
                    return
                }
                def log = qTestClient.getLog(integration.projectId, testRun.id)
                if(isEmpty(log, "Unable to get QTest testRun logs.")){
                    return
                }
                logger.debug("STATUS: " + testCase.status)
                qTestClient.updateResults(testCase.status, new Date(integration.startedAt),  new Date(integration.finishedAt), testRun.id, integration.projectId, log.id)
            }
        }
    }

     protected def getCycleId(){
        def cycles = qTestClient.getCycles(integration.projectId)
        for(cycle in cycles){
            if(cycle.name == integration.customParams.cycle_name){
                return cycle.id
            }
        }
    }

    protected def getTestSuiteId(cycleId){
        def suites = qTestClient.getTestSuites(integration.projectId, cycleId)
        for(suite in suites){
            if(suite.name == integration.platform){
                return suite.id
            }
        }
    }

    protected def getTestRun(suiteId, caseId){
        def runs = qTestClient.getTestRuns(integration.projectId, suiteId)
        for(run in runs){
            if(run.name.equals(integration.testRunName) && run.test_case.id == Integer.valueOf(caseId)){
                return run
            }
        }
    }

    protected def parseTagData(){
        Map testCaseResultMap = new HashMap<>()
        integration.integrationInfo.each { integrationInfoItem ->
            String[] tagInfoArray = integrationInfoItem.tagValue.split("-")
            Map testCase = new HashMap()
            if (!testCaseResultMap.get(tagInfoArray[1])) {
                if (!integration.projectId) {
                    integration.projectId = tagInfoArray[0]
                }
                testCase.case_id = tagInfoArray[1]
                testCase.status = integrationInfoItem.status
            } else {
                testCase = testCaseResultMap.get(tagInfoArray[1])
            }
            testCaseResultMap.put(tagInfoArray[1], testCase)
        }
        integration.caseResultMap = testCaseResultMap
        return integration
    }

    protected boolean isEmpty(value, message){
        if(isParamEmpty(value)){
            logger.error(message)
            return true
        }
    }
}

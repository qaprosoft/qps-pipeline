package com.qaprosoft.integration.qtest

import com.qaprosoft.Logger
import com.qaprosoft.integration.zafira.IntegrationTag
import com.qaprosoft.integration.zafira.ZafiraClient
import static com.qaprosoft.jenkins.pipeline.Executor.*

class QTestUpdater {

    // Make sure that in Automation Settings input statuses configured as PASSED, FAILED, SKIPPED!
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
        logger.debug("INTEGRATION_INFO:\n" + formatJson(integration))

        if(isParamEmpty(integration)){
            logger.debug("Nothing to update in QTest.")
            return
        }
        // convert uuid to project_id, suite_id and testcases related maps
        integration = parseTagData()

        if(isParamEmpty(integration.projectId)){
            logger.error("Unable to detect QTest project_id!\n" + formatJson(integration))
            return
        }
        def projectId = integration.projectId
        def cycleId = getCycleId(projectId)
        if(isParamEmpty(cycleId)){
            logger.error("No dedicated QTest cycle detected.")
            return
        }
        def suiteId = getTestSuiteId(projectId, cycleId)
        if(isParamEmpty(suiteId)){
            def testSuite = qTestClient.addTestSuite(projectId, cycleId, integration.platform)
            logger.info("SUITE: " + formatJson(testSuite))
            if(isParamEmpty(testSuite)){
                logger.error("Unable to register QTest testSuite.")
                return
            }
            suiteId = testSuite.id
        }
        integration.caseResultMap.values().each { testCase ->
            def testRun
            if(!isRerun){
                testRun = qTestClient.addTestRun(projectId, suiteId, testCase.case_id, integration.testRunName)
                if(isParamEmpty(testRun)){
                    logger.error("Unable to add QTest testRun.")
                    return
                }
                def results = qTestClient.uploadResults(testCase.status, new Date(integration.startedAt),  new Date(integration.finishedAt), testRun.id, testRun.name,  projectId)
                if(isParamEmpty(results)){
                    logger.error("Unable to add results for QTest TestRun.")
                    return
                }
                logger.debug("UPLOADED_RESULTS: " + formatJson(results))
            } else {
                testRun = getTestRun(projectId, suiteId, testCase.case_id)
                logger.debug("TEST_RUN: " + formatJson(testRun))
                if(isParamEmpty(testRun)){
                    logger.error("Unable to get QTest testRun.")
                    return
                }
                def log = qTestClient.getLog(projectId, testRun.id)
                if(isParamEmpty(log)){
                    logger.error("Unable to get QTest testRun logs.")
                    return
                }
                logger.debug("STATUS: " + testCase.status)
                qTestClient.updateResults(testCase.status, new Date(integration.startedAt),  new Date(integration.finishedAt), testRun.id, projectId, log.id)
            }
        }
    }

     protected def getCycleId(projectId){
        def cycles = qTestClient.getCycles(projectId)
        for(cycle in cycles){
            if(cycle.name == integration.customParams.cycle_name){
                return cycle.id
            }
        }
    }

    protected def getTestSuiteId(projectId, cycleId){
        def suites = qTestClient.getTestSuites(projectId, cycleId)
        for(suite in suites){
            if(suite.name == integration.platform){
                return suite.id
            }
        }
    }

    protected def getTestRun(projectId, suiteId, caseId){
        def runs = qTestClient.getTestRuns(projectId, suiteId)
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
}

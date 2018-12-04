package com.qaprosoft.integration.qtest

import com.qaprosoft.Logger
import com.qaprosoft.integration.zafira.IntegrationTag
import com.qaprosoft.integration.zafira.StatusMapper
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
        logger.debug("INTEGRATION_INFO:\n" + formatJson(integration))

        if(isEmpty(integration, "Nothing to update in TestRail.")){
            return
        }
        // convert uuid to project_id, suite_id and testcases related maps
        integration = parseTagData()

        if(isEmpty(integration.projectId, "Unable to detect TestRail project_id!\n" + formatJson(integration))){
            return
        }

        def cycleId = getCycleId()

        if(isEmpty(cycleId, "No dedicated cycle detected.")){
            return
        }

        def suiteId = getTestSuiteId(cycleId)

        if(isParamEmpty(suiteId)){
            def testSuite = qTestClient.addTestSuite(integration.projectId, cycleId, integration.env)
            logger.info("SUITE: " + formatJson(testSuite))
            if(isEmpty(testSuite, "Unable to register testSuite.")){
                return
            }
            suiteId = testSuite.id
        }

        integration.caseResultMap.values().each { testCase ->
            def testRun
            if(!isRerun){
                testRun = qTestClient.addTestRun(integration.projectId, suiteId, testCase.case_id, integration.testRunName)
                if(isEmpty(testRun, "Unable to add testRun.")){
                    return
                }
            } else {
                testRun = getTestRun(suiteId, testCase.case_id)
                logger.info("TEST_RUN: " + formatJson(testRun))
                if(isEmpty(testRun, "Unable to get testRun.")){
                    return
                }
            }
            def results = qTestClient.uploadResults(testCase.status, new Date(integration.startedAt),  new Date(integration.finishedAt), testRun.id, testRun.name,  integration.projectId)
            if(isEmpty(results, "Unable to add results for TestRun.")){
                return
            }
            logger.info("UPLOADED_RESULTS: " + formatJson(results))
        }

//        integration.assignedToId = getAssignedToId()
//
//        // get all cases from TestRail by project and suite and compare with exported from Zafira
//        // only cases available in both maps should be registered later
//        parseCases()
//
//        def testRun = null
//        if(isRerun){
//            testRun = getTestRunId()
//            if (isParamEmpty(testRun)) {
//                logger.error("Unable to detect existing run in TestRail for rebuild!")
//            }
//        }
//
//        if(isParamEmpty(testRun)){
//            testRun = addTestRun(includeAll)
//        }
//        addResults(testRun.id)
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
            if(suite.name == integration.env){
                return suite.id
            }
        }
    }

    protected def getTestRun(suiteId, caseId){
        def runs = qTestClient.getTestRuns(integration.projectId, suiteId)
        for(run in runs){
            if(run.name == integration.testRunName && run.test_case.id == caseId){
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

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

        if(isParamEmpty(integration)){
            logger.debug("Nothing to update in TestRail.")
            return
        }

        // convert uuid to project_id, suite_id and testcases related maps
        integration = parseTagData()

        if(isParamEmpty(integration.projectId)){
            logger.error("Unable to detect TestRail project_id!\n" + formatJson(integration))
            return
        }

        def cycleId = getCycleId()
        def testSuite = qTestClient.addTestSuite(integration.projectId, cycleId, integration.env)
        logger.info("SUITE: " + formatJson(testSuite))
        def testRun = qTestClient.addTestRun(integration.projectId, testSuite.id, integration.testRunName)
        def results = qTestClient.uploadResults(integration.caseResultMap.get("1").status, integration.startedAt, integration.finishedAt, testRun.id, testRun.name, integration.projectId)
        logger.info("UPLOADED_RESULTS: " + results)
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

    protected def uploadResults(status, startedAt, finishedAt, testRunId, testRunName, projectId) {
        qTestClient.uploadResults(status, integration.startedAt, integration.finishedAt, )
    }

    protected def getCycleId(){
        def cycles = qTestClient.getCycles(integration.projectId)
        for(cycle in cycles){
            if(cycle.name == integration.customParams.cycle_name){
                return cycle.id
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
                testCase.status = StatusMapper.getQTestStatus(integrationInfoItem.status)
            } else {
                testCase = testCaseResultMap.get(tagInfoArray[1])
            }
            testCaseResultMap.put(tagInfoArray[1], testCase)
        }
        integration.caseResultMap = testCaseResultMap
        return integration
    }
}

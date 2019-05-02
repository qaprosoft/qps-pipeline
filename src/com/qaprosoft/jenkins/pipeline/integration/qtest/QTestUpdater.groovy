package com.qaprosoft.jenkins.pipeline.integration.qtest

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.integration.zafira.IntegrationTag
import com.qaprosoft.jenkins.pipeline.integration.zafira.ZafiraClient
import com.qaprosoft.jenkins.pipeline.Configuration
import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

class QTestUpdater {

    // Make sure that in Automation Settings input statuses configured as PASSED, FAILED, SKIPPED!
    private def context
    private ZafiraClient zc
    private QTestClient qTestClient
    private Logger logger

    public QTestUpdater(context) {
        this.context = context
        zc = new ZafiraClient(context)
        qTestClient = new QTestClient(context)
        logger = new Logger(context)
    }

    public void updateTestRun(uuid) {
        if (!Configuration.get(Configuration.Parameter.QTEST_ENABLE).toBoolean() || !qTestClient.isAvailable()) {
            // do nothing
            return
        }
        // export all tag related metadata from Zafira
        def integration = zc.exportTagData(uuid, IntegrationTag.QTEST_TESTCASE_UUID)
        logger.debug("INTEGRATION_INFO:\n" + formatJson(integration))

        if (isParamEmpty(integration)){
            logger.debug("Nothing to update in QTest.")
            return
        }
        // convert uuid to project_id, suite_id and testcases related maps
        integration = parseTagData(integration)

        if (isParamEmpty(integration.projectId)){
            logger.error("Unable to detect QTest project_id!\n" + formatJson(integration))
            return
        }
        def projectId = integration.projectId
        def testRunName = integration.testRunName
        def cycleName = integration.customParams.cycle_name
        def startedAt = integration.startedAt
        def finishedAt = integration.finishedAt
        def cycleId = getCycleId(projectId, cycleName)
        if (isParamEmpty(cycleId)){
            logger.error("No dedicated QTest cycle detected.")
            return
        }
        if (projectId.toInteger() == 10){
            cycleId = getSubCycleId(cycleId, projectId)
        }
        def env = integration.env
        def suiteId = getTestSuiteId(projectId, cycleId, env)
        if (isParamEmpty(suiteId)){
            def testSuite = qTestClient.addTestSuite(projectId, cycleId, env)
            logger.info("SUITE: " + formatJson(testSuite))
            if (isParamEmpty(testSuite)){
                logger.error("Unable to register QTest testSuite.")
                return
            }
            suiteId = testSuite.id
        }
        integration.testCasesMap.values().each { testCase ->
            def testRun
            def testCaseName = getTestCaseName(projectId, testCase.case_id)
            if (!isParamEmpty(testCaseName)){
                testRunName = testCaseName
            }

            testRun = getTestRun(projectId, suiteId, testCase.case_id, testRunName)
            logger.debug("TEST_RUN: " + formatJson(testRun))
            if (isParamEmpty(testRun)) {
                logger.error("Unable to get QTest testRun.")
                logger.info("Adding new QTest testRun...")
                testRun = qTestClient.addTestRun(projectId, suiteId, testCase.case_id, testRunName)
                if (isParamEmpty(testRun)) {
                    logger.error("Unable to add QTest testRun.")
                    return
                }
            }
            def testLogsNote = testCase.testURL + "\n\n" + testCase.comment
            def results = qTestClient.uploadResults(testCase.status, new Date(startedAt),  new Date(finishedAt), testRun.id, testRun.name,  projectId, testLogsNote)
            if (isParamEmpty(results)){
                logger.error("Unable to add results for QTest TestRun.")
                return
            }
            logger.debug("UPLOADED_RESULTS: " + formatJson(results))
        }
    }

    protected def getCycleId(projectId, cycleName){
        def cycles = qTestClient.getCycles(projectId)
        for(cycle in cycles){
            if (cycle.name == cycleName){
                return cycle.id
            }
        }
    }

    protected def getSubCycleId(cycleId, projectId){
        def subCycleId = null
        def os = Configuration.get("capabilities.os")
        def os_version = Configuration.get("capabilities.os_version")
        if (!isParamEmpty(os) && !isParamEmpty(os_version)){
            def subCycleName = os + "-" + os_version + "-" + Configuration.get("capabilities.browser")
            def subCycles = qTestClient.getSubCycles(cycleId, projectId)
            for(subCycle in subCycles){
                if (subCycle.name == subCycleName){
                    subCycleId = subCycle.id
                }
            }
            if (isParamEmpty(subCycleId)){
                def newSubCycle = qTestClient.addTestCycle(projectId, cycleId, subCycleName)
                if (isParamEmpty(newSubCycle)){
                    logger.error("Unable to add new cycle.")
                    return
                }
                subCycleId = newSubCycle.id
            }
        } else {
            subCycleId = cycleId
        }
        return subCycleId
    }

    protected def getTestSuiteId(projectId, cycleId, platform){
        def suites = qTestClient.getTestSuites(projectId, cycleId)
        for(suite in suites){
            if (suite.name == platform){
                return suite.id
            }
        }
    }

    protected def getTestRun(projectId, suiteId, caseId, testRunName){
        def runs = qTestClient.getTestRuns(projectId, suiteId)
        for(run in runs){
            if (run.name.equals(testRunName) && run.test_case.id == Integer.valueOf(caseId)){
                return run
            }
        }
    }

    protected def getTestCaseName(projectId, caseId){
        def testCaseName = null
        def testCase = qTestClient.getTestCase(projectId, caseId)
        if (isParamEmpty(testCase)){
            logger.error("Unable to get QTest testCase.")
        } else {
            testCaseName = testCase.name
        }
        return testCaseName
    }

    protected def parseTagData(integration){
        def parsedIntegrationInfo = integration
        Map testCasesMap = new HashMap<>()
        integration.testInfo.each { testInfo ->
            String[] tagInfoArray = testInfo.tagValue.split("-")
            def projectId = tagInfoArray[0]
            def testCaseId = tagInfoArray[1]
            if (isParamEmpty(testCasesMap.get(testCaseId))) {
                if (isParamEmpty(parsedIntegrationInfo.projectId)) {
                    parsedIntegrationInfo.projectId = projectId
                }
                Map testCase = new HashMap()
                testCase.case_id = testCaseId.trim()
                testCase.status = testInfo.status
                testCase.testURL = "${integration.zafiraServiceUrl}/#!/tests/runs/${integration.testRunId}/info/${testInfo.id}"
                testCasesMap.put(testCaseId, testCase)
            }
        }
        parsedIntegrationInfo.testCasesMap = testCasesMap
        return parsedIntegrationInfo
    }
}

package com.qaprosoft.jenkins.pipeline.integration.testrail


import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.integration.zafira.StatusMapper
import com.qaprosoft.jenkins.pipeline.Configuration

import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*
import com.qaprosoft.jenkins.pipeline.integration.zafira.ZafiraClient
import com.qaprosoft.jenkins.pipeline.integration.zafira.IntegrationTag

class TestRailUpdater {

    private def context
    private ZafiraClient zc
    private TestRailClient trc
    private Logger logger

    public TestRailUpdater(context) {
        this.context = context
        zc = new ZafiraClient(context)
        trc = new TestRailClient(context)
        logger = new Logger(context)
    }

    public void updateTestRun(uuid, isRerun) {
		if (!Configuration.get(Configuration.Parameter.TESTRAIL_ENABLE).toBoolean() || !trc.isAvailable()) {
			// do nothing
			return
		}
		
        // export all tag related metadata from Zafira
        def integration = zc.exportTagData(uuid, IntegrationTag.TESTRAIL_TESTCASE_UUID)
        logger.debug("INTEGRATION_INFO:\n" + formatJson(integration))

        if (isParamEmpty(integration)){
            logger.debug("Nothing to update in TestRail.")
            return
        }

        // convert uuid to project_id, suite_id and testcases related maps
        integration = parseTagData(integration)

        if (isParamEmpty(integration.projectId)){
            logger.error("Unable to detect TestRail project_id!\n" + formatJson(integration))
            return
        }
        def includeAll = !isParamEmpty(Configuration.get("include_all"))?Configuration.get("include_all"):true
        def projectId = integration.projectId
        def suiteId = integration.suiteId
        Map customParams = integration.customParams
        Map caseResultMap = integration.testCasesMap
        Map testResultMap = new HashMap<>()
        def milestoneId = getMilestoneId(projectId, customParams)
        def assignedToId
        def createdBy = getAssignedToId(null)
        if (isParamEmpty(customParams.assignee)){
            assignedToId = createdBy
        } else {
            assignedToId = getAssignedToId(customParams.assignee)
        }
        def testRunName
        if (!isParamEmpty(customParams.testrail_run_name)){
            testRunName = customParams.testrail_run_name
            createdBy = null
            isRerun = true
        } else {
            testRunName = integration.testRunName
        }
        def createdAfter = integration.createdAfter

        // get all cases from TestRail by project and suite and compare with exported from Zafira
        // only cases available in both maps should be registered later
        def testRailCaseIds = parseCases(projectId, suiteId)
        def filteredCaseResultMap = filterCaseResultMap(caseResultMap, testRailCaseIds)

        def testRailRunId = null
        if (isRerun){
            testRailRunId = getTestRailRunId(testRunName, createdBy, milestoneId, projectId, suiteId, createdAfter, Configuration.get("testrail_search_interval"))
        }

        if (isParamEmpty(testRailRunId)){
            def newTestRailRun = addTestRailRun(testRunName, suiteId, projectId, milestoneId, assignedToId, includeAll, filteredCaseResultMap)
            if (isParamEmpty(newTestRailRun)) {
                logger.error("Unable to add test run to TestRail!")
                return
            }
            testRailRunId = newTestRailRun.id
        }
        testResultMap = filterTests(testRailRunId, assignedToId, testRailCaseIds, testResultMap, filteredCaseResultMap)
        addResults(testRailRunId, testResultMap)
    }

    protected def getTestRailRunId(testRunName, createdBy, milestoneId, projectId, suiteId, createdAfter, searchInterval){
		// "- 60 * 60 * 24 * defaultSearchInterval" - an interval to support adding results into manually created TestRail runs
        int defaultSearchInterval = 7
        if (!isParamEmpty(searchInterval)){
            defaultSearchInterval = searchInterval.toInteger()
        }
        def testRuns = trc.getRuns(Math.round(createdAfter/1000) - 60 * 60 * 24 * defaultSearchInterval, createdBy, milestoneId, projectId, suiteId)
//        logger.debug("TEST_RUNS:\n" + formatJson(testRuns))
		def testRunId = null
        for(Map testRun in testRuns){
//            logger.debug("TEST_RUN: " + formatJson(testRun))
            String correctedName = testRun.name.trim().replaceAll(" +", " ")
            if (correctedName.equals(testRunName)){
                testRunId = testRun.id
                break
            }
        }
        if (isParamEmpty(testRunId)) {
            logger.error("Unable to detect run in TestRail!")
        }
		return testRunId
    }

    protected def getMilestoneId(projectId, customParams){
        if (isParamEmpty(customParams.milestone)) {
            logger.error("No milestone name discovered!")
            return
        }
        def milestoneName = customParams.milestone
        def milestoneId = null
        def milestones = trc.getMilestones(projectId)
        milestones.each { Map milestone ->
            if (milestone.name == milestoneName) {
                milestoneId = milestone.id
            }
        }
        if (isParamEmpty(milestoneId)){
            def newMilestone = trc.addMilestone(projectId, milestoneName)
            if (!isParamEmpty(newMilestone)){
                milestoneId = newMilestone.id
            }
        }
        return milestoneId
    }

    protected def getAssignedToId(testRailAssignee){
        def assignedToId = trc.getUserIdByEmail(testRailAssignee)
        if (isParamEmpty(assignedToId)){
            logger.debug("No users with such email found!")
            return
        }
        return assignedToId.id
    }

    protected def parseCases(projectId, suiteId){
        Set testRailCaseIds = new HashSet()
        def cases = trc.getCases(projectId, suiteId)
//        logger.debug("SUITE_CASES: " + formatJson(cases))
        cases.each { testCase ->
            testRailCaseIds.add(testCase.id)
        }
//        logger.debug("VALID_CASES: " + formatJson(validTestCases))
        return testRailCaseIds
    }

    protected def filterCaseResultMap(caseResultMap, testRailCaseIds){
        def filteredCaseResultMap = caseResultMap
        caseResultMap.each { testCase ->
            boolean isValid = false
            for(testRailCaseId in testRailCaseIds){
                if (testRailCaseId.toString().equals(testCase.value.case_id)){
                    isValid = true
                    break
                }
            }
            if (!isValid){
                filteredCaseResultMap.remove(testCase.value.case_id)
                logger.error("Removed non-existing case: ${testCase.value.case_id}.\nPlease adjust your test code using valid platfrom/language/locale filters for TestRail cases registration.")
            }
        }
        return filteredCaseResultMap
    }

    protected def filterTests(testRunId, assignedToId, testRailCaseIds, testResultMap, caseResultMap){
        Map filteredTestResultMap = testResultMap
        def tests = trc.getTests(testRunId)
//        logger.debug("TESTS_MAP:\n" + formatJson(tests))
        tests.each { test ->
            for(testRailCaseId in testRailCaseIds){
                if (testRailCaseId == test.case_id){
                    Map resultToAdd = new HashMap()
                    resultToAdd.test_id = test.id
                    String testCaseId = test.case_id.toString()
                    def testCase = caseResultMap.get(testCaseId)
                    if (!isParamEmpty(testCase)){
                        resultToAdd.status_id = testCase.status_id
                        resultToAdd.comment = testCase.testURL + "\n\n" + testCase.comment
                        resultToAdd.defects = testCase.defects
                        resultToAdd.assignedto_id = assignedToId
                        if (resultToAdd.status_id != 3) {
                            filteredTestResultMap.put(resultToAdd.test_id, resultToAdd)
                        }
                        break
                    }
                }
            }
        }
        return filteredTestResultMap
    }

    protected def addTestRailRun(testRunName, suiteId, projectId, milestoneId, assignedToId, includeAll, caseResultMap){
        def testRun = trc.addTestRun(suiteId, testRunName, milestoneId, assignedToId, includeAll, caseResultMap.keySet(), projectId)
        logger.debug("ADDED TESTRUN:\n" + formatJson(testRun))
        return testRun
    }

    protected def addResults(testRunId, testResultMap){
        def response = trc.addResultsForTests(testRunId, testResultMap.values())
//        logger.debug("ADD_RESULTS_TESTS_RESPONSE: " + formatJson(response))
    }
    
    protected def parseTagData(integration){
        def parsedIntegrationInfo = integration
        Map testCasesMap = new HashMap<>()
        for(testInfo in integration.testInfo){
            String[] tagInfoArray = testInfo.tagValue.split("-")
            if (tagInfoArray.size() < 3){
                logger.error("Invalid integration tag, test with id ${testInfo.id} won't be pushed in testrail.")
                continue
            }
            def projectId = tagInfoArray[0]
            def testSuiteId = tagInfoArray[1]
            def testCaseId = tagInfoArray[2]
            Map testCase = new HashMap()
            if (isParamEmpty(testCasesMap.get(testCaseId))) {
                if (isParamEmpty(parsedIntegrationInfo.projectId)) {
                    parsedIntegrationInfo.projectId = projectId
                    parsedIntegrationInfo.suiteId = testSuiteId
                }
                testCase.case_id = testCaseId
                testCase.status_id = StatusMapper.getTestRailStatus(testInfo.status)
                if (testCase.status_id != 1){
                    testCase.comment = testInfo.message
                }
                testCase.testURL = "${integration.zafiraServiceUrl}/#!/tests/runs/${integration.testRunId}/info/${testInfo.id}"
            } else {
                testCase = testCasesMap.get(testCaseId)
            }
            testCase.defects = getDefectsString(testCase.defects, testInfo.defectId)
            testCasesMap.put(testCaseId, testCase)
        }
        parsedIntegrationInfo.testCasesMap = testCasesMap
        return parsedIntegrationInfo
    }
}

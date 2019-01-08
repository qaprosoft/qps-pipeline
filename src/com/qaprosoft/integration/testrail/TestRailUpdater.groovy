package com.qaprosoft.integration.testrail


import com.qaprosoft.Logger
import com.qaprosoft.integration.zafira.StatusMapper
import com.qaprosoft.jenkins.pipeline.Configuration

import static com.qaprosoft.jenkins.pipeline.Executor.*
import com.qaprosoft.integration.zafira.ZafiraClient
import com.qaprosoft.integration.zafira.IntegrationTag

class TestRailUpdater {

    private def context
    private ZafiraClient zc
    private TestRailClient trc
    private Logger logger
    private integration

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
        integration = zc.exportTagData(uuid, IntegrationTag.TESTRAIL_TESTCASE_UUID)
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
        def includeAll = !isParamEmpty(Configuration.get("include_all"))?Configuration.get("include_all"):true
        logger.info("INCLUDE_ALL: " + includeAll)
        def projectId = integration.projectId
        def suiteId = integration.suiteId
        def milestoneId = getMilestoneId(projectId)
        def assignedToId = getAssignedToId()
        def testRunName
        if(!isParamEmpty(integration.customParams.get("testrail_run_name"))){
            testRunName = integration.customParams.get("testrail_run_name")
            isRerun = true
        } else {
            testRunName = integration.testRunName
        }
        def createdAfter = integration.createdAfter
        // get all cases from TestRail by project and suite and compare with exported from Zafira
        // only cases available in both maps should be registered later
        parseCases(projectId, suiteId)

        def testRun = null
        if(isRerun){
            testRun = getTestRunId(testRunName, assignedToId, milestoneId, projectId, suiteId, createdAfter)
			logger.info("TEST_RUN_FOUND: " + testRun)
            if (isParamEmpty(testRun)) {
                logger.error("Unable to detect existing run in TestRail for rebuild!")
            }
        }

        if(isParamEmpty(testRun)){
            testRun = addTestRun(testRunName, suiteId, projectId, milestoneId, assignedToId, includeAll)
            if (isParamEmpty(testRun)) {
                logger.error("Unable to add test run in TestRail!")
                return
            }
        }
        addResults(testRun.id)
    }

    protected def getTestRunId(testRunName, assignedToId, milestoneId, projectId, suiteId, createdAfter){
		// "-120" to resolve potential time async with testrail upto 2 min
        def testRuns = trc.getRuns(Math.round(createdAfter/1000) - 120, assignedToId, milestoneId, projectId, suiteId)
        logger.info("TEST_RUNS:\n" + formatJson(testRuns))
		def run = null
        testRuns.each { Map testRun ->
            logger.info("TEST_RUN: " + formatJson(testRun))
            String correctedName = testRun.name.trim().replaceAll(" +", " ")
            if(correctedName.equals(testRunName)){
                integration.testRunId = testRun.id
				run = testRun
                return run
            }
        }
		return run
    }

    protected def getMilestoneId(projectId){
        Map customParams = integration.customParams
        if(isParamEmpty(customParams.milestone)) {
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
        if(!milestoneId ){
            def milestone = trc.addMilestone(projectId, milestoneName)
            if(!isParamEmpty(milestone)){
                milestoneId = milestone.id
            }
        }
        return milestoneId
    }

    protected def getAssignedToId(){
        Map customParams = integration.customParams
        def assignedToId = trc.getUserIdByEmail(customParams.assignee)
        if(isParamEmpty(assignedToId)){
            logger.debug("No users with such email found!")
            return
        }
        return assignedToId.id
    }

    protected def parseCases(projectId, suiteId){
        Set validTestCases = new HashSet()
        def cases = trc.getCases(projectId, suiteId)
        logger.debug("SUITE_CASES: " + formatJson(cases))
        cases.each { testCase ->
            validTestCases.add(testCase.id)
        }
        integration.validTestCases = validTestCases
        logger.debug("VALID_CASES: " + formatJson(validTestCases))

        filterCases()
    }

    protected def filterCases(){
        integration.caseResultMap.each { testCase ->
            boolean isValid = false
            for(validTestCaseId in integration.validTestCases){
                if(validTestCaseId.toString().equals(testCase.value.case_id)){
                    isValid = true
                    break
                }
            }
            if(!isValid){
                integration.caseResultMap.remove(testCase.value.case_id)
                logger.error("Removed non-existing case: ${testCase.value.case_id}.\nPlease adjust your test code using valid platfrom/language/locale filters for TestRail cases registration.")
            }
        }
        logger.debug("CASES_MAP:\n" + formatJson(integration.caseResultMap))
    }

    protected def getTests(){
        def tests = trc.getTests(integration.testRunId)
        logger.debug("TESTS_MAP:\n" + formatJson(tests))
        tests.each { test ->
            for(validTestCaseId in integration.validTestCases){
                if(validTestCaseId == test.case_id){
                    Map testResult = new HashMap()
                    testResult.test_id = test.id
                    String testCaseId = test.case_id.toString()
                    if(!isParamEmpty(integration.caseResultMap.get(testCaseId))){
                        testResult.status_id = integration.caseResultMap.get(testCaseId).status_id
                        testResult.comment = integration.caseResultMap.get(testCaseId).comment
                        testResult.defects = integration.caseResultMap.get(testCaseId).defects
                        if (testResult.status_id != 3) {
                            integration.testResultMap.put(testResult.test_id, testResult)
                            integration.caseResultMap.remove(testCaseId)
                        }
                        break
                    }
                }
            }
        }

//        logger.debug("TESTS_MAP2:\n" + formatJson(integration.testResultMap))
    }

    protected def addTestRun(testRunName, suiteId, projectId, milestoneId, assignedToId, includeAll){
        def testRun = trc.addTestRun(suiteId, testRunName, milestoneId, assignedToId, includeAll, integration.caseResultMap.keySet(), projectId)
        logger.debug("ADDED TESTRUN:\n" + formatJson(testRun))
        return testRun
    }

    protected def addResults(testRunId){
        integration.testRunId = testRunId
        getTests()
        def response = trc.addResultsForTests(integration.testRunId, integration.testResultMap.values())
        logger.debug("ADD_RESULTS_TESTS_RESPONSE: " + formatJson(response))
    }
    
    protected def parseTagData(){
        Map testCaseResultMap = new HashMap<>()
        integration.integrationInfo.each { integrationInfoItem ->
            String[] tagInfoArray = integrationInfoItem.tagValue.split("-")
            Map testCase = new HashMap()
            if (!testCaseResultMap.get(tagInfoArray[2])) {
                if (!integration.projectId) {
                    integration.projectId = tagInfoArray[0]
                    integration.suiteId = tagInfoArray[1]
                }
                testCase.case_id = tagInfoArray[2]
                testCase.status_id = StatusMapper.getTestRailStatus(integrationInfoItem.status)
                testCase.comment = integrationInfoItem.message
            } else {
                testCase = testCaseResultMap.get(tagInfoArray[2])
            }
            testCase.defects = getDefectsString(testCase.defects, integrationInfoItem.defectId)
            testCaseResultMap.put(tagInfoArray[2], testCase)
        }
        integration.caseResultMap = testCaseResultMap
        Map testResultMap = new HashMap<>()
        integration.testResultMap = testResultMap

        return integration
    }
}

package com.qaprosoft.testrail


import com.qaprosoft.Logger
import static com.qaprosoft.jenkins.pipeline.Executor.*
import com.qaprosoft.zafira.ZafiraClient

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

    public void updateTestRun(uuid, isRerun, boolean includeAll) {
        integration = zc.getIntegrationInfo(uuid, IntegrationTag.TESTRAIL_TESTCASE_UUID)
        logger.info("INTEGRATION_INFO:\n" + formatJson(integration))
        if(!isParamEmpty(integration)){
            parseIntegrationInfo()
            if(!isParamEmpty(integration.projectId)){
                integration.milestoneId = getMilestoneId()
                integration.assignedToId = getAssignedToId()

                getCases()

                def testRun = null
                if(isRerun){
                    testRun = getTestRunId()
                }
                if(isParamEmpty(testRun)){
                    testRun = addTestRun(includeAll)
                }
                addResults(testRun.id)
            }
        }
    }

    protected def getTestRunId(){
        def testRuns
        if(integration.milestoneId){
            testRuns = trc.getRuns(Math.round(integration.createdAfter/1000), integration.assignedToId, integration.milestoneId, integration.projectId, integration.suiteId)
        } else {
            testRuns = trc.getRuns(Math.round(integration.createdAfter/1000), integration.assignedToId, integration.projectId, integration.suiteId)
        }
        logger.info("TEST_RUNS:\n" + formatJson(testRuns))
        testRuns.each { Map testRun ->
            logger.info("TEST_RUN: " + formatJson(testRun))
            if(testRun.name == integration.testRunName){
                integration.testRunId = testRun.id
                return testRun
            }
        }
    }

    protected def getMilestoneId(){
        Map customParams = integration.customParams
        if(!isParamEmpty(customParams.milestone)){

            def milestoneId = null
            def milestones = trc.getMilestones(integration.projectId)
            milestones.each { Map milestone ->
                if (milestone.name == customParams.milestone) {
                    milestoneId = milestone.id
                }
            }
            if(!milestoneId ){
                def milestone = trc.addMilestone(integration.projectId, customParams.milestone)
                if(!isParamEmpty(milestone)){
                    milestoneId = milestone.id
                }
            }
            return milestoneId
        }
    }

    protected def getAssignedToId(){
        Map customParams = integration.customParams
        def assignedToId = trc.getUserIdByEmail(customParams.assignee)
        return assignedToId.id
    }

    protected def getCases(){
        Set validTestCases = new HashSet()
        def cases = trc.getCases(integration.projectId, integration.suiteId)
        logger.info("SUITE_CASES: " + formatJson(cases))
        cases.each { testCase ->
            validTestCases.add(testCase.id)
        }
        integration.validTestCases = validTestCases
        logger.info("VALID_CASES: " + formatJson(validTestCases))

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
                logger.error("REMOVE INVALID CASE: ${testCase.value.case_id}")
            }
        }
        logger.info("CASES_MAP:\n" + formatJson(integration.caseResultMap))
    }

    protected def getTests(){
        def tests = trc.getTests(integration.testRunId)
        logger.info("TESTS_MAP:\n" + formatJson(tests))
        tests.each { test ->
            for(validTestCaseId in integration.validTestCases){
                if(validTestCaseId == test.case_id){
                    Map testResult = new HashMap()
                    testResult.test_id = test.id
                    String testCaseId = test.case_id.toString()
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

//        tests.each { test ->
//            for(validTestCaseId in integration.validTestCases){
//                if(validTestCaseId == test.case_id){
//                    String testCaseId = test.case_id.toString()
//                    test.status_id = integration.caseResultMap.get(testCaseId).status_id
//                    test.comment = integration.caseResultMap.get(testCaseId).comment
//                    test.defects = integration.caseResultMap.get(testCaseId).defects
//                    if (test.status_id != 3) {
//                        integration.testResultMap.put(test.id, test)
//                    }
//                    break
//                }
//            }
//        }
        logger.info("TESTS_MAP2:\n" + formatJson(integration.testResultMap))
    }

    protected def addTestRun(boolean includeAll){
        def testRun
        if(integration.milestoneId){
            testRun = trc.addTestRun(integration.suiteId, integration.testRunName, integration.milestoneId, integration.assignedToId, includeAll, integration.caseResultMap.keySet(), integration.projectId)

        } else {
            logger.info("CASES_REQUEST: " + integration.caseResultMap.keySet())
            testRun = trc.addTestRun(integration.suiteId, integration.testRunName, integration.assignedToId, includeAll, integration.caseResultMap.keySet(), integration.projectId)
        }
        logger.info("ADDED TESTRUN:\n" + formatJson(testRun))
        return testRun
    }

    protected def addResults(testRunId){
        integration.testRunId = testRunId
//        getCases()
        getTests()


        //def response = trc.addResultsForCases(integration.testRunId, integration.caseResultMap.values())
        //logger.info("ADD_RESULTS_CASES_RESPONSE: " + formatJson(response))
        def response = trc.addResultsForTests(integration.testRunId, integration.testResultMap.values())
        logger.info("ADD_RESULTS_TESTS_RESPONSE: " + formatJson(response))

    }
    
    protected def parseIntegrationInfo(){
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
                testCase.status_id = TestRailStatusMapper.getTestRailStatus(integrationInfoItem.status)
//                testCase.comment = integrationInfoItem.message
                testCase.comment = ""
            } else {
                testCase = testCaseResultMap.get(tagInfoArray[2])
            }
            testCase.defects = getDefectsString(testCase.defects, integrationInfoItem.defectId)
            testCaseResultMap.put(tagInfoArray[2], testCase)
        }
        integration.caseResultMap = testCaseResultMap
        Map testResultMap = new HashMap<>()
        integration.testResultMap = testResultMap
    }
}

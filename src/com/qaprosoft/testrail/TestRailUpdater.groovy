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
                if(!isRerun){
                    def testRun = addTestRun(includeAll)
                    if(!isParamEmpty(testRun)){
                        integration.testRunId = testRun.id
                        getValidCases()
                    }
                } else {
                    getTestRunId()
                }
                checkValidCases()
                addResultsForCases()
            }
        }
    }

    public def getTestRunId(){
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
                return
            }
        }
        def tests = trc.getTests(integration.testRunId)
        logger.info("TESTS:" + formatJson(tests))
    }

    public def getMilestoneId(){
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

    public def getAssignedToId(){
        Map customParams = integration.customParams
        def assignedToId = trc.getUserIdByEmail(customParams.assignee)
        return assignedToId.id
    }

    public def getValidCases(){
        Set validTestCases = new HashSet()
        def tests = trc.getTests(integration.testRunId)
        if(!isParamEmpty(tests)){
            tests.each { test ->
                validTestCases.add(test.case_id)
            }
        } else {
            def cases = trc.getCases(integration.projectId, integration.suiteId)
            cases.each { testCase ->
                validTestCases.add(testCase.id)
            }
        }
        integration.validTestCases = validTestCases
    }

    public def checkValidCases(){
        integration.testCaseResultMap.each { testCase ->
            boolean isValid = false
            for(validTestCaseId in integration.validTestCases){
                logger.info("VALIDCASEID: " + validTestCaseId)
                logger.info("CASEID: " + testCase.value.case_id)
                if(validTestCaseId == testCase.value.case_id){
                    isValid = true
                    break
                }
            }
            if(!isValid){
                logger.info("SIZEBEFORE: " + integration.testCaseResultMap.size())
                integration.testCaseResultMap.remove(testCase.value.case_id)
                logger.info("SIZEAFTER: " + integration.testCaseResultMap.size())
            }
        }
    }

    public def addTestRun(boolean include_all){
        def testRun
        if(integration.milestoneId){
            testRun = trc.addTestRun(integration.suiteId, integration.testRunName, integration.milestoneId, integration.assignedToId, include_all, integration.testCaseResultMap.keySet(), integration.projectId)
        } else {
            testRun = trc.addTestRun(integration.suiteId, integration.testRunName, integration.assignedToId, include_all, integration.testCaseResultMap.keySet(), integration.projectId)
        }
        logger.info("ADDED TESTRUN:\n" + formatJson(testRun))
        def tests = trc.getTests(testRun.id)
        logger.info("TESTS:" + formatJson(tests))
        return testRun
    }

    public def addResultsForCases(){
        def response = trc.addResultsForCases(integration.testRunId, integration.testCaseResultMap.values())
        logger.info("ADD_RESULTS_RESPONSE: " + formatJson(response))
    }

    public def parseIntegrationInfo(){
        Map testCaseResultMap = new HashMap<>()
        integration.integrationInfo.each { integrationInfoItem ->
            String[] tagInfoArray = integrationInfoItem.tagValue.split("-")
            Map testCase = new HashMap()
            if (!testCaseResultMap.get(tagInfoArray[2])) {
                if (!integration.projectId) {
                    integration.projectId = tagInfoArray[0]
                    integration.suiteId = tagInfoArray[1]
                }
                testCase.case_id = Integer.valueOf(tagInfoArray[2])
                testCase.status_id = TestRailStatusMapper.getTestRailStatus(integrationInfoItem.status)
//                testCase.comment = integrationInfoItem.message
                testCase.comment = ""
            } else {
                testCase = testCaseResultMap.get(tagInfoArray[2])
            }
            testCase.defects = getDefectsString(testCase.defects, integrationInfoItem.defectId)
            testCaseResultMap.put(tagInfoArray[2], testCase)
        }
        integration.testCaseResultMap = testCaseResultMap
    }
}

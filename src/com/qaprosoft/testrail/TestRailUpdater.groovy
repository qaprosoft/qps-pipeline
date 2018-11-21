package com.qaprosoft.testrail


import com.qaprosoft.Logger
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

    public void updateTestRun(uuid, isRebuild) {
        integration = zc.getTestRailIntegrationInfo(uuid)
        if(!integration.isEmpty()){
            integration.milestoneId = getMilestoneId()
            integration.assignedToId = getAssignedToId()
            if(!isRebuild){
                def testRun = addTestRun(false)
                integration.testRunId = testRun.id
            } else {
                integration.testRunId = getTestRunId()
            }
            addResultsForCases()
        }
    }

    public def getTestRunId(){
        def testRunId = null
        def testRuns
        if(integration.milestoneId){
            testRuns = trc.getRuns(Math.round(integration.createdAfter/1000), integration.assignedToId, integration.milestoneId, integration.projectId, integration.suiteId)
        } else {
            testRuns = trc.getRuns(Math.round(integration.createdAfter/1000), integration.assignedToId, integration.projectId, integration.suiteId)
        }
        testRuns.each { Map testRun ->
            logger.info("TEST_RUN: " + testRun)
            if(testRun.name == integration.testRunName){
                testRunId = testRun.id
            }
        }
        return testRunId
    }

    public def getMilestoneId(){
        if(integration.milestone){
            def milestoneId = null
            def milestones = trc.getMilestones(integration.projectId)
            milestones.each { Map milestone ->
                if (milestone.name == integration.milestone) {
                    milestoneId = milestone.id
                }
            }
            if(!milestoneId ){
                def milestone = trc.addMilestone(integration.projectId, integration.milestone)
                milestoneId = milestone.id
            }
            return milestoneId
        }
    }

    public def getAssignedToId(){
        def assignedToId = trc.getUserIdByEmail(integration.createdBy)
        return assignedToId.id
    }

    public def addTestRun(boolean include_all){
        def testRun
        if(integration.milestoneId){
            testRun = trc.addTestRun(integration.suiteId, integration.testRunName, integration.milestoneId, integration.assignedToId, include_all, integration.testCaseIds, integration.projectId)
        } else {
            testRun = trc.addTestRun(integration.suiteId, integration.testRunName, integration.assignedToId, include_all, integration.testCaseIds, integration.projectId)
        }
        logger.info("ADDED TESTRUN:\n" + testRun)
        return testRun
    }

    public def addResultsForCases(){
        def response = trc.addResultsForCases(integration.testRunId, getStatusCodeId(), integration.testRunComment, integration.testRunAppVersion, integration.testRunElapsed, getDefectsString(), integration.assignedToId)
        logger.info("ADD_RESULTS_RESPONSE: " + response)
    }

    public def getStatusCodeId(){
        return TestRailStatusMapper.getTestRailStatus(integration.testRunStatus)
    }

    public def getDefectsString(){
        def defectsString = ""
        return defectsString.replaceAll(".\$","")
    }
    public def parseIntegrationInfo(){
        Map testCaseResultMap = new HashMap<>()
        integration.integrationInfo.each { integrationInfoItem ->
            String[] tagInfoArray = integrationInfoItem.getTagValue().split("-")
            def testCaseResult = {}
            List<String> defectList
            if (testCaseResultMap.get(tagInfoArray[2]) == null) {
                if (!integration.projectId) {
                    integration.projectId = tagInfoArray[0]
                    integration.suiteId = tagInfoArray[1]
                }
                testCaseResult = new TestCaseResult()
                testCaseResult.setTestCaseId(tagInfoArray[2])
                testCaseResult.setStatus(integrationInfoItem.getStatus())
                defectList = new ArrayList<>()
            } else {
                testCaseResult = testCaseResultMap.get(tagInfoArray[2])
                defectList = testCaseResult.getDefects()
            }
            defectList.add(integrationInfoItem.getDefectId())
            testCaseResult.setDefects(defectList)
            testCaseResultMap.put(tagInfoArray[2], testCaseResult)
        }
        integration.testCaseIds = (List<TestCaseResult>) testCaseResultMap.values()

    }
}

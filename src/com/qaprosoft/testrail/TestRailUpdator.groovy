package com.qaprosoft.testrail

import static com.qaprosoft.jenkins.pipeline.Executor.*
import com.qaprosoft.Logger
import com.qaprosoft.zafira.ZafiraClient

class TestRailUpdator {

    private def context
    private ZafiraClient zc
    private TestRailClient trc
    private Logger logger
    private integration


    public TestRailUpdator(context) {
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
        logger.info("Not implemented yet")
    }
}

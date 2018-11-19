package com.qaprosoft.testrail

import com.qaprosoft.Logger
import com.qaprosoft.zafira.ZafiraClient

class TestRailUpdator {

    private def context
    private ZafiraClient zc
    private TestRailClient trc
    private Logger logger
    private integrationInfo


    public TestRailUpdator(context) {
        this.context = context
        zc = new ZafiraClient(context)
        trc = new TestRailClient(context)
        logger = new Logger(context)
    }

    public void updateTestRun(uuid) {
        integrationInfo = zc.getTestRailIntegrationInfo(uuid)
        context.println "INTGR: " + integrationInfo
        if(!integrationInfo.isEmpty()){

            def milestoneId = getMilestoneId(integrationInfo.milestone)
            def assignedToId = getAssignedToId(integrationInfo.createdBy)
            def testRunId = getTestRunId(milestoneId, assignedToId)
            if(testRunId){
                logger.info("Not implemented yet")
            } else {
                def addedTestRun = trc.addTestRun(integrationInfo.suiteId, integrationInfo.testRunName, milestoneId, assignedToId, false, integrationInfo.testCaseIds, integrationInfo.projectId)
                logger.info(addedTestRun)
            }
        }
    }

    public def getTestRunId(milestoneId, assignedToId){
        def testRunId = null
        context.println "CREATED_AT: " + integrationInfo.createdAfter.dump()
        Math.round(integrationInfo.createdAfter/1000)
        context.println "CREATED_AT2: " + integrationInfo.createdAfter.dump()
        def testRuns = trc.getRuns(integrationInfo.createdAfter/1000, assignedToId, milestoneId, integrationInfo.projectId, integrationInfo.suiteId)
        testRuns.each { Map testRun ->
            logger.info("TR: " + testRun)
            if(testRun.name == integrationInfo.testRunName){
                testRunId = testRun.id
            }
        }
        return testRunId
    }

    public def getMilestoneId(name){
        def milestoneId = null
        def milestones = trc.getMilestones(integrationInfo.projectId)
        milestones.each { Map milestone ->
            if (milestone.name == name) {
                milestoneId = milestone.id
            }
        }
        if(!milestoneId){
            def milestone = trc.addMilestone(projectId, name)
            milestoneId = milestone.id
        }
        return milestoneId
    }

    public def getAssignedToId(assigneeEmail){
        return trc.getUserIdByEmail(assigneeEmail).id
    }
}

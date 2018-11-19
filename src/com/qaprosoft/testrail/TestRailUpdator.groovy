package com.qaprosoft.testrail

import com.qaprosoft.Logger
import com.qaprosoft.zafira.ZafiraClient
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

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
            def updatedTestRun = trc.addTestRun(integrationInfo.suiteId, integrationInfo.testRunName, milestoneId, assignedToId, false, integrationInfo.testCaseIds, integrationInfo.projectId)
            logger.info(updatedTestRun)
        }
    }

    public def getTestRunId(){

    }

    public def getMilestoneId(name){
        def milestoneId
        def projectId = integrationInfo.projectId
        def milestones = trc.getMilestones(projectId)
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

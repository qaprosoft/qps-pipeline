package com.qaprosoft.integration.zafira

import com.qaprosoft.Logger
import com.qaprosoft.jenkins.pipeline.Configuration

import static com.qaprosoft.jenkins.pipeline.Executor.*

class ZafiraUpdater {

    private def context
    private ZafiraClient zc
    private Logger logger
    private def testRun

    public ZafiraUpdater(context) {
        this.context = context
        zc = new ZafiraClient(context)
        logger = new Logger(context)
    }

    /**
     * Unable to make a calls for this method at intermeddiate state without refactoring
     * we keep TestRun result using single call for now at the end only.
     * **/
    protected def getTestRun(uuid) {
        def run = testRun
        if(isParamEmpty(testRun)) {
            run = zc.getTestRunByCiRunId(uuid)
            if (isParamEmpty(run)) {
                logger.error("TestRun is not found in Zafira!")
            }
        }
        return run
    }

    public def queueZafiraTestRun(uuid) {
        if(isParamEmpty(Configuration.get("queue_registration")) || Configuration.get("queue_registration").toBoolean()) {
            def response = zc.queueZafiraTestRun(uuid)
            logger.info("Queued TestRun: " + formatJson(response))
        }
    }

    public def smartRerun() {
        def response = zc.smartRerun()
        if(isParamEmpty(response)){
            "No tests for rerun!"
        }
        logger.info("Results : " + response.size())
    }

    public def abortTestRun(uuid, currentBuild) {
        currentBuild.result = BuildResult.FAILURE
        def failureReason = "undefined failure"

        String buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
        String jobBuildUrl = Configuration.get(Configuration.Parameter.JOB_URL) + buildNumber
        String jobName = Configuration.get(Configuration.Parameter.JOB_NAME)
        String env = Configuration.get("env")

        def bodyHeader = "<p>Unable to execute tests due to the unrecognized failure: ${jobBuildUrl}</p>"
        def subject = getFailureSubject(FailureCause.UNRECOGNIZED_FAILURE, jobName, env, buildNumber)
        def failureLog = ""

        if (currentBuild.rawBuild.log.contains("COMPILATION ERROR : ")) {
            bodyHeader = "<p>Unable to execute tests due to the compilation failure. ${jobBuildUrl}</p>"
            subject = getFailureSubject(FailureCause.COMPILATION_FAILURE, jobName, env, buildNumber)
            failureLog = getLogDetailsForEmail(currentBuild, "ERROR")
            failureReason = URLEncoder.encode("${FailureCause.COMPILATION_FAILURE}:\n" + failureLog, "UTF-8")
        } else  if (currentBuild.rawBuild.log.contains("Cancelling nested steps due to timeout")) {
            currentBuild.result = BuildResult.ABORTED
            bodyHeader = "<p>Unable to continue tests due to the abort by timeout ${jobBuildUrl}</p>"
            subject = getFailureSubject(FailureCause.TIMED_OUT, jobName, env, buildNumber)
            failureReason = "Aborted by timeout"
        } else if (currentBuild.rawBuild.log.contains("BUILD FAILURE")) {
            bodyHeader = "<p>Unable to execute tests due to the build failure. ${jobBuildUrl}</p>"
            subject = getFailureSubject(FailureCause.BUILD_FAILURE, jobName, env, buildNumber)
            failureLog = getLogDetailsForEmail(currentBuild, "ERROR")
            failureReason = URLEncoder.encode("${FailureCause.BUILD_FAILURE}:\n" + failureLog, "UTF-8")
        } else  if (currentBuild.rawBuild.log.contains("Aborted by ")) {
            currentBuild.result = BuildResult.ABORTED
            bodyHeader = "<p>Unable to continue tests due to the abort by " + getAbortCause(currentBuild) + " ${jobBuildUrl}</p>"
            subject = getFailureSubject(FailureCause.ABORTED, jobName, env, buildNumber)
            failureReason = "Aborted by " + getAbortCause(currentBuild)
        }
        def response = zc.abortTestRun(uuid, failureReason)
        if(!isParamEmpty(response)){
                sendFailureEmail(uuid, Configuration.get(Configuration.Parameter.ADMIN_EMAILS))
        } else {
            logger.error("UNABLE TO ABORT TESTRUN! Probably run is not registered in Zafira.")
            //Explicitly send email via Jenkins (emailext) as nothing is registered in Zafira
            def body = bodyHeader + """<br>
                       Rebuild: ${jobBuildUrl}/rebuild/parameterized<br>
                  ZafiraReport: ${jobBuildUrl}/ZafiraReport<br>
		               Console: ${jobBuildUrl}/console<br>${failureLog.replace("\n", "<br>")}"""
            context.emailext getEmailParams(body, subject, Configuration.get(Configuration.Parameter.ADMIN_EMAILS))
        }
    }

    public def sendZafiraEmail(uuid, emailList) {
        def testRun = getTestRun(uuid)
        if (!isParamEmpty(emailList)) {
            zc.sendEmail(uuid, emailList, "all")
        }
        String failureEmailList = Configuration.get("failure_email_list")
        if (isFailure(testRun.status) && !isParamEmpty(failureEmailList)) {
            zc.sendEmail(uuid, failureEmailList, "failures")
        }
    }

    public void exportZafiraReport(uuid, workspace) {
        String zafiraReport = zc.exportZafiraReport(uuid)
        if(isParamEmpty(zafiraReport)){
            logger.error("UNABLE TO GET TESTRUN! Probably it is not registered in Zafira.")
            return
        }
        logger.debug(zafiraReport)
        context.writeFile file: "${workspace}/zafira/report.html", text: zafiraReport
     }

    public def sendFailureEmail(uuid, emailList) {
        def suiteOwner = true
        def suiteRunner = false
        if(Configuration.get("suiteOwner")){
            suiteOwner = Configuration.get("suiteOwner")
        }
        if(Configuration.get("suiteRunner")){
            suiteRunner = Configuration.get("suiteRunner")
        }
        return zc.sendFailureEmail(uuid, emailList, suiteOwner, suiteRunner)
    }

    public def setBuildResult(uuid, currentBuild) {
        def testRun = getTestRun(uuid)
        if(isFailure(testRun.status)){
            currentBuild.result = BuildResult.FAILURE
        }
    }

    public boolean isZafiraRerun(uuid){
        return !isParamEmpty(zc.getTestRunByCiRunId(uuid))
    }

}

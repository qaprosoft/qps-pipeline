package com.qaprosoft.integration.zafira

import com.qaprosoft.Logger
import com.qaprosoft.jenkins.pipeline.Configuration

import static com.qaprosoft.jenkins.pipeline.Executor.*

class ZafiraUpdater {

    private def context
    private ZafiraClient zc
    private Logger logger

    public ZafiraUpdater(context) {
        this.context = context
        zc = new ZafiraClient(context)
        logger = new Logger(context)
    }

    public def queueZafiraTestRun(uuid) {
        if(isParamEmpty(Configuration.get("queue_registration")) || Configuration.get("queue_registration").toBoolean()) {
            def response = zc.queueZafiraTestRun(uuid)
            logger.info("Queued TestRun: " + formatJson(response))
        }
    }

    public def smartRerun() {
        def response = zc.smartRerun()
        logger.info("Results : " + response.size())
        logger.info("Tests for rerun: " + formatJson(response))
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
        if(!isParamEmpty(response) && response.status == 200){
                sendFailureEmail(uuid, Configuration.get(Configuration.Parameter.ADMIN_EMAILS))
        } else {
            if(response.status == 500){
                logger.error("UNABLE TO ABORT TESTRUN!")
            }
            //Explicitly send email via Jenkins (emailext) as nothing is registered in Zafira
            def body = bodyHeader + """<br>
                       Rebuild: ${jobBuildUrl}/rebuild/parameterized<br>
                  ZafiraReport: ${jobBuildUrl}/ZafiraReport<br>
		               Console: ${jobBuildUrl}/console<br>${failureLog.replace("\n", "<br>")}"""
            context.emailext getEmailParams(body, subject, Configuration.get(Configuration.Parameter.ADMIN_EMAILS))
        }
    }

    public def sendZafiraEmail(uuid, emailList, buildResult) {
        if (!isParamEmpty(emailList)) {
            zc.sendEmail(uuid, emailList, "all")
        }
        String failureEmailList = Configuration.get("failure_email_list")
        if (isFailure(buildResult) && !isParamEmpty(failureEmailList)) {
            zc.sendEmail(uuid, failureEmailList, "failures")
        }
    }

    public void exportZafiraReport(uuid, workspace, currentBuild) {
        //replace existing local emailable-report.html by Zafira content
        String zafiraReport = zc.exportZafiraReport(uuid)
        if(isParamEmpty(zafiraReport)){
            return
        }
        logger.debug(zafiraReport)

        context.writeFile file: "${workspace}/zafira/report.html", text: zafiraReport
        //TODO: think about method renaming because in additions it also could redefine job status in Jenkins.
        // or move below code into another method

        // set job status based on zafira report
        if (!zafiraReport.contains("PASSED:") && !zafiraReport.contains("PASSED (known issues):") && !zafiraReport.contains("SKIP_ALL:")) {
            logger.debug("Unable to Find (Passed) or (Passed Known Issues) within the eTAF Report.")
            currentBuild.result = BuildResult.FAILURE
        } else if (zafiraReport.contains("SKIP_ALL:")) {
            currentBuild.result = BuildResult.UNSTABLE
        }
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

    public boolean isZafiraRerun(uuid){
        return !isParamEmpty(zc.getTestRunByCiRunId(uuid))
    }

}

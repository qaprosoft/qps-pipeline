package com.qaprosoft.jenkins.pipeline.integration.zafira

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.Configuration

import static com.qaprosoft.jenkins.Utils.*
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

    def getTestRunByCiRunId(uuid) {
        def testRun = zc.getTestRunByCiRunId(uuid)
        if (isParamEmpty(testRun)) {
            logger.error("TestRun is not found in Zafira!")
            return
        }
        return testRun
    }

    public def queueZafiraTestRun(uuid) {
        if (isParamEmpty(Configuration.get("queue_registration")) || Configuration.get("queue_registration").toBoolean()) {
            if (isParamEmpty(Configuration.get('test_run_rules'))) {
                def response = zc.queueZafiraTestRun(uuid)
                logger.info("Queued TestRun: " + formatJson(response))
            }
        }
    }

    public def smartRerun() {
        def response = zc.smartRerun()
        logger.info("Results : " + response.size())
    }

    public def abortTestRun(uuid, currentBuild) {
        def abortedTestRun
        currentBuild.result = BuildResult.FAILURE
        def failureReason = "undefined failure"

        String buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
        String jobBuildUrl = Configuration.get(Configuration.Parameter.JOB_URL) + buildNumber
        String jobName = Configuration.get(Configuration.Parameter.JOB_NAME)
        String env = Configuration.get("env")

        def bodyHeader = "Unable to execute tests due to the unrecognized failure: ${jobBuildUrl}\n"
        def subject = getFailureSubject(FailureCause.UNRECOGNIZED_FAILURE.value, jobName, env, buildNumber)
        def failureLog = ""

        if (currentBuild.rawBuild.log.contains("COMPILATION ERROR : ")) {
            bodyHeader = "Unable to execute tests due to the compilation failure. ${jobBuildUrl}\n"
            subject = getFailureSubject(FailureCause.COMPILATION_FAILURE.value, jobName, env, buildNumber)
            failureLog = getLogDetailsForEmail(currentBuild, "ERROR")
            failureReason = URLEncoder.encode("${FailureCause.COMPILATION_FAILURE.value}:\n" + failureLog, "UTF-8")
        } else if (currentBuild.rawBuild.log.contains("Cancelling nested steps due to timeout")) {
            currentBuild.result = BuildResult.ABORTED
            bodyHeader = "Unable to continue tests due to the abort by timeout ${jobBuildUrl}\n"
            subject = getFailureSubject(FailureCause.TIMED_OUT.value, jobName, env, buildNumber)
            failureReason = "Aborted by timeout"
        } else if (currentBuild.rawBuild.log.contains("Aborted by ")) {
            currentBuild.result = BuildResult.ABORTED
            bodyHeader = "Unable to continue tests due to the abort by " + getAbortCause(currentBuild) + " ${jobBuildUrl}\n"
            subject = getFailureSubject(FailureCause.ABORTED.value, jobName, env, buildNumber)
            failureReason = "Aborted by " + getAbortCause(currentBuild)
        } else if (currentBuild.rawBuild.log.contains("BUILD FAILURE")) {
            bodyHeader = "Unable to execute tests due to the build failure. ${jobBuildUrl}\n"
            subject = getFailureSubject(FailureCause.BUILD_FAILURE.value, jobName, env, buildNumber)
            failureLog = getLogDetailsForEmail(currentBuild, "ERROR")
            failureReason = URLEncoder.encode("${FailureCause.BUILD_FAILURE.value}:\n" + failureLog, "UTF-8")
        }
        abortedTestRun = zc.abortTestRun(uuid, failureReason)

        //Checks if testRun is present in Zafira and sends Zafira-generated report
        if (!isParamEmpty(abortedTestRun)) {
            //Sends email to admin if testRun was aborted
            if (abortedTestRun.status.equals(StatusMapper.ZafiraStatus.ABORTED.name())) {
                sendFailureEmail(uuid, Configuration.get(Configuration.Parameter.ADMIN_EMAILS))
            } else {
                sendFailureEmail(uuid, Configuration.get("email_list"))
            }
        } else {
            //If testRun is not available in Zafira, sends email to admins by means of Jenkins
            logger.error("Unable to abort testrun! Probably run is not registered in Zafira.")
            //Explicitly send email via Jenkins (emailext) as nothing is registered in Zafira
            def body = "${bodyHeader}\nRebuild: ${jobBuildUrl}/rebuild/parameterized\nZafiraReport: ${jobBuildUrl}/ZafiraReport\n\nConsole: ${jobBuildUrl}/console\n${failureLog}"
            context.emailext getEmailParams(body, subject, Configuration.get(Configuration.Parameter.ADMIN_EMAILS))
        }
        return abortedTestRun
    }

    public def sendZafiraEmail(uuid, emailList) {
        def testRun = getTestRunByCiRunId(uuid)
        if (isParamEmpty(testRun)) {
            logger.error("No testRun with uuid " + uuid + "found in Zafira")
            return
        }
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
        if (isParamEmpty(zafiraReport)) {
            logger.error("UNABLE TO GET TESTRUN! Probably it is not registered in Zafira.")
            return
        }
        logger.debug(zafiraReport)
        context.writeFile file: "${workspace}/zafira/report.html", text: zafiraReport
    }

    public def sendFailureEmail(uuid, emailList) {
        if (isParamEmpty(emailList)) {
            logger.info("No failure email recipients was provided")
            return
        }
        def suiteOwner = true
        def suiteRunner = false
        if (Configuration.get("suiteOwner")) {
            suiteOwner = Configuration.get("suiteOwner")
        }
        if (Configuration.get("suiteRunner")) {
            suiteRunner = Configuration.get("suiteRunner")
        }
        return zc.sendFailureEmail(uuid, emailList, suiteOwner, suiteRunner)
    }

    public def setBuildResult(uuid, currentBuild) {
        def testRun = getTestRunByCiRunId(uuid)
        if (!isParamEmpty(testRun)) {
            logger.debug("testRun: " + testRun.dump())
            if (isFailure(testRun.status)) {
                logger.debug("marking currentBuild.result as FAILURE")
                currentBuild.result = BuildResult.FAILURE
            } else if (isPassed(testRun.status)) {
                logger.debug("marking currentBuild.result as SUCCESS")
                currentBuild.result = BuildResult.SUCCESS
            } else {
                // do nothing to inherit status from job
                logger.debug("don't change currentBuild.result")
            }
        }
    }

    public def sendSlackNotification(uuid, channels) {
        if (!isParamEmpty(channels)) {
            return zc.sendSlackNotification(uuid, channels)
        }
    }

    public boolean isZafiraRerun(uuid) {
        return !isParamEmpty(zc.getTestRunByCiRunId(uuid))
    }

    public def createLaunchers(jenkinsJobsScanResult) {
        return zc.createLaunchers(jenkinsJobsScanResult)
    }

    public def createJob(jobUrl) {
        return zc.createJob(jobUrl)
    }

    protected boolean isFailure(testRunStatus) {
        return !"PASSED".equals(testRunStatus)
    }

    protected boolean isPassed(testRunStatus) {
        return "PASSED".equals(testRunStatus)
    }


}

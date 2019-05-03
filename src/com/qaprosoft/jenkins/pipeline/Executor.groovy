package com.qaprosoft.jenkins.pipeline

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
@Grab('org.testng:testng:6.8.8')
import groovy.json.JsonSlurperClassic

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths

import static java.util.UUID.randomUUID
import static com.qaprosoft.jenkins.Utils.*
import org.jenkinsci.plugins.ghprb.*
import com.cloudbees.plugins.credentials.impl.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.*

public class Executor {

    static enum BuildResult {
        FAILURE, ABORTED, UNSTABLE
    }

    static enum FailureCause {
        UNRECOGNIZED_FAILURE("UNRECOGNIZED FAILURE"),
        COMPILATION_FAILURE("COMPILATION FAILURE"),
        TIMED_OUT("TIMED OUT"),
        BUILD_FAILURE("BUILD FAILURE"),
        ABORTED("ABORTED")

        final String value
        FailureCause(String value) {
            this.value = value
        }
    }

    static String getUUID() {
        def ci_run_id = Configuration.get("ci_run_id")
        if (isParamEmpty(ci_run_id)) {
            ci_run_id = randomUUID() as String
        }
        return ci_run_id
    }

    static def getEmailParams(body, subject, to) {
        def params = [attachLog: true,
                      body: body,
                      recipientProviders: [[$class: 'DevelopersRecipientProvider'],
                                           [$class: 'RequesterRecipientProvider']],
                      subject: subject,
                      to: to]
        return params
    }

    static def getReportParameters(reportDir, reportFiles, reportName) {
        def reportParameters = [allowMissing: false,
                                alwaysLinkToLastBuild: false,
                                keepAll: true,
                                reportDir: reportDir,
                                reportFiles: reportFiles,
                                reportName: reportName]
        return reportParameters
    }

    static def updateJenkinsCredentials(id, description, user, password) {
        if (!isParamEmpty(password) && !isParamEmpty(user)){
            def credentialsStore = SystemCredentialsProvider.getInstance().getStore()
            def credentials = getCredentials(id)
            if (credentials){
                credentialsStore.removeCredentials(Domain.global(), credentials)
            }
            Credentials c = (Credentials) new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, id, description, user, password)
            return credentialsStore.addCredentials(Domain.global(), c)
        }
    }

    static def getCredentials(id) {
        return SystemCredentialsProvider.getInstance().getStore().getCredentials(Domain.global()).find {
            it.id.equals(id.toString())
        }
    }

    static def createPRChecker(credentialsId) {
        GhprbTrigger.DescriptorImpl descriptor = Jenkins.instance.getDescriptorByType(org.jenkinsci.plugins.ghprb.GhprbTrigger.DescriptorImpl.class)
        List<GhprbGitHubAuth> githubAuths = descriptor.getGithubAuth()
//        Removes all autocreated by plugin checkers
//        githubAuths.clear()
        githubAuths.add(new GhprbGitHubAuth('https://api.github.com', null, credentialsId, "${credentialsId} connection", null, null))
        descriptor.save()
    }

    static boolean isMobile() {
        def platform = Configuration.get("platform")
        return platform.equalsIgnoreCase("android") || platform.equalsIgnoreCase("ios")
    }

    static String getSubProjectFolder() {
        //specify current dir as subProject folder by default
        def subProjectFolder = "."
        if (!isParamEmpty(Configuration.get("sub_project"))) {
            subProjectFolder = "./" + Configuration.get("sub_project")
        }
        return subProjectFolder
    }

    static Object parseJSON(String path) {
        def inputFile = new File(path)
        def content = new JsonSlurperClassic().parseFile(inputFile, 'UTF-8')
        return content
    }

    static def getObjectResponse(response){
        return new JsonSlurper().parseText(response)
    }

    static def formatJson(json){
        JsonBuilder builder = new JsonBuilder(json)
        return builder.toPrettyString()
    }

    static def parseFolderName(workspace) {
        def folderName = ""

        if (workspace.contains("jobs/")) {
            def array = workspace.split("jobs/")
            for (def i = 1; i < array.size() - 1; i++){
                folderName  = folderName + array[i]
            }
            folderName = replaceTrailingSlash(folderName)
        } else {
            def array = workspace.split("/")
            folderName = array[array.size() - 2]
        }

        return folderName
    }

    static boolean isFailure(testRunStatus) {
        boolean failure = false
        if (!"PASSED".equals(testRunStatus)){
            failure = true
        }
        return failure
    }

    static def getPipelineLocales(xmlSuite){
        def supportedLocales = [:]
        def jenkinsPipelineLocales = xmlSuite.getParameter("jenkinsPipelineLocales")
        if (!isParamEmpty(jenkinsPipelineLocales)) {
            for (String locale in jenkinsPipelineLocales.split(",")) {
                if (locale.contains(":")){
                    def languageLocaleArray = locale.split(":")
                    supportedLocales.put(languageLocaleArray[0], languageLocaleArray[1])
                } else {
                    def language = locale.split("_")[0]
                    supportedLocales.put(locale, language)
                }
            }
        }
        return supportedLocales
    }

    static def getJobParameters(currentBuild){
        Map jobParameters = [:]
        def jobParams = currentBuild.rawBuild.getAction(ParametersAction)
        for (param in jobParams) {
            if (param.value != null) {
                jobParameters.put(param.name, param.value)
            }
        }
    }

    static def getJenkinsJobByName(jobName) {
        def currentJob = null
        Jenkins.getInstance().getAllItems(org.jenkinsci.plugins.workflow.job.WorkflowJob).each { job ->
            if (job.displayName == jobName) {
                currentJob = job
            }
        }
        return currentJob
    }

    static def getItemByFullName(jobFullName) {
        return Jenkins.instance.getItemByFullName(jobFullName)
    }

    static def getJenkinsFolderByName(folderName) {
        def currentJob = null
        Jenkins.getInstance().getAllItems(com.cloudbees.hudson.plugins.folder.Folder).each { job ->
            if (job.displayName == folderName) {
                currentJob = job
            }
        }
        return currentJob
    }

    static String getFailureSubject(cause, jobName, env, buildNumber){
        return "${cause}: ${jobName} - ${env} - Build # ${buildNumber}!"
    }

    static String getLogDetailsForEmail(currentBuild, logPattern){
        def failureLog = ""
        int lineCount = 0
        for(logLine in currentBuild.rawBuild.getLog(50)) {
            if (logLine.contains(logPattern) && lineCount < 10){
                failureLog = failureLog + "${logLine}\n"
                lineCount++
            }
        }
        return failureLog
    }

    static def getJobUrl(jobFullName){
        String separator = "/job/"
        String jenkinsUrl = Configuration.get(Configuration.Parameter.JOB_URL).split(separator)[0]
        jobFullName.split("/").each {
            jenkinsUrl += separator + it
        }
        return jenkinsUrl
    }

    static String getBuildUser(currentBuild) {
        try {
            return currentBuild.rawBuild.getCause(hudson.model.Cause.UserIdCause).getUserId()
        } catch (Exception e) {
            return ""
        }
    }

    static String getAbortCause(currentBuild)
    {
        def abortCause = ''
        def actions = currentBuild.getRawBuild().getActions(jenkins.model.InterruptedBuildAction)
        for (action in actions) {
            // on cancellation, report who cancelled the build
            for (cause in action.getCauses()) {
                abortCause = cause.getUser().getDisplayName()
            }
        }
        return abortCause
    }

    /** Determines BuildCause */
    static String getBuildCause(jobName, currentBuild) {
        String buildCause = null
        /* Gets CauseActions of the job */
        currentBuild.rawBuild.getActions(hudson.model.CauseAction.class).each {
            action ->
                /* Searches UpstreamCause among CauseActions and checks if it is not the same job as current(the other way it was rebuild) */
                if (action.findCause(hudson.model.Cause.UpstreamCause.class)
                        && (jobName != action.findCause(hudson.model.Cause.UpstreamCause.class).getUpstreamProject())) {
                    buildCause = "UPSTREAMTRIGGER"
                }
                /* Searches TimerTriggerCause among CauseActions */
                else if (action.findCause(hudson.triggers.TimerTrigger$TimerTriggerCause.class)) {
                    buildCause = "TIMERTRIGGER"
                }
                /* Searches GitHubPushCause among CauseActions */
                else if (action.findCause(com.cloudbees.jenkins.GitHubPushCause.class)) {
                    buildCause = "SCMPUSHTRIGGER"
                }
                else if (action.findCause(org.jenkinsci.plugins.ghprb.GhprbCause.class)) {
                    buildCause = "SCMGHPRBTRIGGER"
                }
                else {
                    buildCause = "MANUALTRIGGER"
                }

        }
        return buildCause
    }

    /** Detects if any changes are present in files matching patterns  */
    @NonCPS
    static boolean isUpdated(currentBuild, patterns) {
        def isUpdated = false
        def changeLogSets = currentBuild.rawBuild.changeSets
        changeLogSets.each { changeLogSet ->
            /* Extracts GitChangeLogs from changeLogSet */
            for (entry in changeLogSet.getItems()) {
                /* Extracts paths to changed files */
                for (path in entry.getPaths()) {
                    Path pathObject = Paths.get(path.getPath())
                    /* Checks whether any changed file matches one of patterns */
                    for (pattern in patterns.split(",")){
                        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern)
                        /* As only match is found stop search*/
                        if (matcher.matches(pathObject)){
                            isUpdated = true
                            return
                        }
                    }
                }
            }
        }
        return isUpdated
    }

    static def isLabelApplied(build, label) {
        boolean isApplied = false
        GhprbCause c = Ghprb.getCause(build)
        GhprbTrigger trigger = Ghprb.extractTrigger(build)
        GhprbPullRequest ghprbPullRequest = trigger.getRepository().getPullRequest(c.getPullID())
        for(ghLabel in ghprbPullRequest.getPullRequest().getLabels()) {
            if (ghLabel.getName() == label){
                isApplied = true
                break
            }
        }
        return isApplied
    }

    static def getPullRequest(build) {
        GhprbCause c = Ghprb.getCause(build)
        GhprbTrigger trigger = Ghprb.extractTrigger(build)
        return trigger.getRepository().getPullRequest(c.getPullID()).getPullRequest()
    }

    @NonCPS
    static def isSnapshotRequired(currentBuild, trigger) {
        def isRequired = false
        def changeLogSets = currentBuild.rawBuild.changeSets
        changeLogSets.each { changeLogSet ->
            for (entry in changeLogSet.getItems()) {
                if (entry.getMsg().contains(trigger)){
                    isRequired = true
                    return
                }
            }
        }
        return isRequired
    }

    static def isChangeSetContains(currentBuild, stringValue) {
        return currentBuild.rawBuild.changeSets.any {
            it.getItems().find {
                it.comment.contains(stringValue)
            }
        }
    }

    static boolean isJobParameterValid(name, value) {
        def excludedCapabilities = ["custom_capabilities",
                                    "retry_count",
                                    "rerun_failures",
                                    "fork",
                                    "debug",
                                    "ci_run_id",
                                    "pipelineLibrary",
                                    "runnerClass"]
        def excluded = excludedCapabilities.find {
            it.equals(name)
        }
        return isParamEmpty(excluded)
    }

    /** Checks if current job started as rebuild */
    static Boolean isRebuild(currentBuild) {
        Boolean isRebuild = false
        /* Gets CauseActions of the job */
        currentBuild.rawBuild.getActions(hudson.model.CauseAction.class).each {
            action ->
                /* Search UpstreamCause among CauseActions */
                if (action.findCause(hudson.model.Cause.UpstreamCause.class) != null)
                /* If UpstreamCause exists and has the same name as current job, rebuild was called */
                    isRebuild = (jobName == action.findCause(hudson.model.Cause.UpstreamCause.class).getUpstreamProject())
        }
        return isRebuild
    }

    @NonCPS
    static def sortPipelineList(List pipelinesList) {
        pipelinesList = pipelinesList.sort { map1, map2 -> !map1.order ? !map2.order ? 0 : 1 : !map2.order ? -1 : map1.order.toInteger() <=> map2.order.toInteger() }
        return pipelinesList

    }

    static def getDefectsString(String defects, String newDefects){
        if (isParamEmpty(defects)){
            defects = newDefects
        } else {
            if (!isParamEmpty(newDefects)){
                defects = "${defects},${newDefects}"
            }
        }
        return defects
    }


    static def setDefaultIfEmpty(stringKey, enumKey){
        def configValue = Configuration.get(stringKey)
        if (isParamEmpty(configValue)) {
            configValue = Configuration.get(enumKey)
        }
        return configValue
    }

    static void putNotNull(map, key, value) {
        if (value != null && !value.equalsIgnoreCase("null")) {
            map.put(key, value)
        }
    }

    static void putNotNullWithSplit(map, key, value) {
        if (value != null) {
            map.put(key, value.replace(", ", ","))
        }
    }

    static void putMap(pipelineMap, map) {
        map.each { mapItem ->
            pipelineMap.put(mapItem.key, mapItem.value)
        }
    }
}

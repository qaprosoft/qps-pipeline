package com.qaprosoft.jenkins.pipeline

import groovy.json.JsonSlurperClassic
import org.testng.xml.Parser
import org.testng.xml.XmlSuite

import static java.util.UUID.randomUUID

public class Executor {

    static String getUUID() {
		def ci_run_id = Configuration.get("ci_run_id")
		if (ci_run_id == null || ci_run_id.isEmpty()) {
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
	
    static boolean isParamEmpty(String value) {
        return value == null || value.isEmpty() || value.equals("NULL")
    }

    static Object parseJSON(String path) {
        def inputFile = new File(path)
        def content = new JsonSlurperClassic().parseFile(inputFile, 'UTF-8')
        return content
    }

    static XmlSuite parseSuite(String path) {
        def xmlFile = new Parser(path)
        xmlFile.setLoadClasses(false)

        List<XmlSuite> suiteXml = xmlFile.parseToList()
        XmlSuite currentSuite = suiteXml.get(0)
        return currentSuite
    }

    static def parseFolderName(workspace) {
        def folderName = ""

        if (workspace.contains("jobs/")) {
            def array = workspace.split("jobs/")
            for (def i = 1; i < array.size() - 1; i++){
                folderName  = folderName + array[i]
            }
            folderName = folderName.replaceAll(".\$","")
        } else {
            def array = workspace.split("/")
            folderName = array[array.size() - 2]
        }

        return folderName
    }

    static boolean isFailure(currentBuild) {
        boolean failure = false
        if (currentBuild.result) {
            failure = "FAILURE".equals(currentBuild.result.name)
        }
        return failure
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
//                context.println "DUMP" + action.dump()
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

    /** Checks if current job started as rebuild */
    public Boolean isRebuild(currentBuild) {
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

    static def enableVideoStreaming(node, message, capability, goals) {
        if ("web".equalsIgnoreCase(node) || "android".equalsIgnoreCase(node)) {
            goals += capability
        }
        return goals
    }

    static def addOptionalParameter(parameter, message, capability, goals) {
        if (Configuration.get(parameter) && Configuration.get(parameter).toBoolean()) {
            goals += capability
        }
        return goals
    }

    static def getHostAddresses() {
        def hosts = []
        for(ifs in NetworkInterface.getNetworkInterfaces()){
            for(address in ifs.getInetAddresses()){
                hosts.add(address.getHostAddress())
            }
        }
        return hosts
    }

    static def setDefaultIfEmpty(stringKey, enumKey){
        def configValue = Configuration.get(stringKey)
        if (configValue.isEmpty()) {
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

}

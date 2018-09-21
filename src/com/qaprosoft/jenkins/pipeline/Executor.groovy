package com.qaprosoft.jenkins.pipeline

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

    static boolean isFailure(currentBuild) {
        boolean failure = false
        if (currentBuild.result) {
            failure = "FAILURE".equals(currentBuild.result.name)
        }
        return failure
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

    /** Determines BuildCause */
    static String getBuildCause(jobName, context) {
        String buildCause = null
        /* Gets CauseActions of the job */
        context.currentBuild.rawBuild.getActions(hudson.model.CauseAction.class).each {
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

    static boolean isBrowserStackRun() {
        boolean res = false
        def customCapabilities = Configuration.get("custom_capabilities")
        if (!isParamEmpty(customCapabilities)) {
            if (customCapabilities.toLowerCase().contains("browserstack")) {
                res = true
            }
        }
        return res
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

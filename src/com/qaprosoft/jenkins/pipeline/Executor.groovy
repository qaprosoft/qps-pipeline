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

    static boolean isParamEmpty(String value) {
        return value == null || value.isEmpty() || value.equals("NULL")
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

    static void printStackTrace(context, Exception ex) {
        context.println "exception: " + ex.getMessage()
        context.println "exception class: " + ex.getClass().getName()
        context.println "stacktrace: " + Arrays.toString(ex.getStackTrace())
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

}

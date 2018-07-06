package com.qaprosoft.jenkins.repository.pipeline.v2

import com.cloudbees.groovy.cps.NonCPS

class Configurator {

    private def context

    public Configurator(context) {
        this.context = context

    }

    //list of job vars/params as a map
    protected static Map args = [:]

    public enum Parameter {

        //vars
        CARINA_CORE_VERSION("CARINA_CORE_VERSION", "5.3.4.105"),
        JOB_URL("JOB_URL", ""),
        JOB_NAME("JOB_NAME", ""),
        JOB_BASE_NAME("JOB_BASE_NAME", ""),
        JOB_MAX_RUN_TIME("JOB_MAX_RUN_TIME", ""),
        ZAFIRA_SERVICE_URL("ZAFIRA_SERVICE_URL", ""),
        ZAFIRA_ACCESS_TOKEN("ZAFIRA_ACCESS_TOKEN", ""),
        ZAFIRA_BASE_CONFIG("ZAFIRA_BASE_CONFIG", ""),
        GITHUB_SSH_URL("GITHUB_SSH_URL", ""),
        GITHUB_HOST("GITHUB_HOST", ""),
        BUILD_NUMBER("BUILD_NUMBER", ""),
        CORE_LOG_LEVEL("CORE_LOG_LEVEL", ""),
        SELENIUM_URL("SELENIUM_URL", ""),
        JACOCO_ENABLE("JACOCO_ENABLE", ""),
        //params
        ZAFIRA_ENABLED("zafira_enabled", ""),
        BUILD("build", ""),
        BUILD_USER_ID("BUILD_USER_ID", ""),
        PROJECT("project", ""),
        SUB_PROJECT("sub_project", ""),
        ZAFIRA_PROJECT("zafira_project", ""),
        SUITE("suite", ""),
        BRANCH("branch", ""),
        FOLDER("folder", ""),
        FORK("fork", ""),
        DEVICE("device", ""),
        PLATFORM("platform", ""),
        ENV("env", ""),
        BROWSER("browser", ""),
        BROWSER_VERSION("browser_version", ""),
        EMAIL_LIST("email_list", ""),
        FAILURE_EMAIL_LIST("failure_email_list", ""),
        DEFAULT_POOL("DefaultPool", ""),
        NODE("node", ""),
        PRIORITY("priority", ""),
        DEVELOP("develop", ""),
        DEBUG("debug", ""),
        RETRY_COUNT("retry_count", ""),
        THREAD_COUNT("thread_count", ""),
        KEEP_ALL_SCREENSHOTS("keep_all_screenshots", ""),
        AUTO_SCREENSHOT("auto_screenshot", ""),
        RERUN_FAILURES("rerunFailures", ""),
        RECOVERY_MODE("recoveryMode", ""),
        ENABLE_VNC("enableVNC", ""),
        ENABLE_VIDEO("enableVideo", ""),
        OVERRIDE_FIELDS("overrideFields", ""),
        GIT_BRANCH("git_branch", ""),
        GIT_URL("git_url", ""),
        SCM_URL("scm_url", ""),
        JAVA_AWT_HEADLESS("java.awt.headless", ""),
        CI_RUN_ID("ci_run_id", ""),
        CI_URL("ci_url", ""),
        CI_BUILD("ci_build", ""),
        CI_BUILD_CAUSE("ci_parent_url", ""),
        CI_PARENT_URL("ci_parent_url", ""),
        CI_PARENT_BUILD("ci_parent_build", ""),
        CI_USER_ID("ci_user_id", ""),
        UPSTREAM_JOB_ID("upstream_job_id", ""),
        UPSTREAM_JOB_BUILD_NUMBER("upstream_job_build_number", ""),
        HASHCODE("hashcode", ""),
        DO_REBUILD("doRebuild", ""),
        
        CAPABILITIES_NEW_COMMAND_TIMEOUT("capabilities.newCommandTimeout", ""),
        CAPABILITIES_PLATFORM_NAME("capabilities.platformName", ""),
        CAPABILITIES_STF_ENABLED("capabilities.STF_ENABLED", ""),
        CAPABILITIES_APP_WAIT_DURATION("capabilities.appWaitDuration", ""),
        CAPABILITIES_PLATFORM("capabilities.platform", ""),
        CAPABILITIES_DEVICE_NAME("capabilities.deviceName", ""),
        CAPABILITIES_APP_PACKAGE("capabilities.appPackage", ""),
        CAPABILITIES_APP_ACTIVITY("capabilities.appActivity", ""),
        CAPABILITIES_AUTO_ACCEPT_ALERTS("capabilities.autoAcceptAlerts", ""),
        CAPABILITIES_AUTO_GRANT_PERMISSIONS("capabilities.autoGrantPermissions", "")

        private final String key;
        private final String value;

        Parameter(String key, String value) {
            this.key = key;
            this.value = value;
        }
        
        public String getKey() {
            return key
        }

        public String getValue() {
            return value;
        }

    }

    public static String getArg(Parameter param) {
        return args.get(param.getKey())
    }

    public static void setArg(Parameter param, String value) {
        return args.put(param.getKey(), value)
    }
    @NonCPS
    public static String getArg(String paramName) {
        return args.get(paramName)
    }

    public static void setArg(String paramName, String value) {
        return args.put(paramName, value)
    }

    @NonCPS
    public void load() {
        context.println("LOAD METHOD CALLED")
        //1. load all Parameter key/values to args
        context.println(Parameter.values())
        context.println(getArg("env"))
//        for (value in Parameter.values()) {
//            context.println(value.getKey())
//            context.println(value.getValue())
//        }
//        Parameter.values().each { parameter ->
//            args.put(parameter.getKey(), parameter.getValue())
//            context.println(parameter.getKey())
//            context.println(parameter.getValue())
//        }
        /*
        //2. load all string keys/values from env
        def envVars = context.env.getEnvironment()
        envVars.each { k, v ->
            args.put(k, v)
        }
        //3. load all string keys/values from params
        def jobParams = context.currentBuild.rawBuild.getAction(ParametersAction)
        jobParams.each { k, v ->
            args.put(k, v)
        }
        */
        //4. investigate how private pipeline can override those values
    }


}
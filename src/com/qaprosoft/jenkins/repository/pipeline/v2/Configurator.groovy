package com.qaprosoft.jenkins.repository.pipeline.v2

class Configurator {

    //list of job vars/params as a map
    protected static Map args = [:]

    public enum Parameter {

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

        CAPABILITIES_NEW_COMMAND_TIMEOUT(getCapabilityKey("newCommandTimeout"), ""),
        CAPABILITIES_PLATFORM_NAME(getCapabilityKey("platformName"), ""),
        CAPABILITIES_STF_ENABLED(getCapabilityKey("STF_ENABLED"), ""),
        CAPABILITIES_APP_WAIT_DURATION(getCapabilityKey("appWaitDuration"), ""),
        CAPABILITIES_PLATFORM(getCapabilityKey("platform"), ""),
        CAPABILITIES_DEVICE_NAME(getCapabilityKey("deviceName"), ""),
        CAPABILITIES_APP_PACKAGE(getCapabilityKey("appPackage"), ""),
        CAPABILITIES_APP_ACTIVITY(getCapabilityKey("appActivity"), ""),
        CAPABILITIES_AUTO_ACCEPT_ALERTS(getCapabilityKey("autoAcceptAlerts"), ""),
        CAPABILITIES_AUTO_GRANT_PERMISSIONS(getCapabilityKey("autoGrantPermissions"), ""),

        private final String key;
        private final String value;

        Parameter(String key, String value) {
            this.key = key;
            this.value = value;
        }
        
        public String getKey() {
            return key;
        }
        public String getValue() {
            return value;
        }

    }

    private String getCapabilityKey(String key) {
        String prefix = "capabilities."
        return prefix + key
    }

    public void load(def context) {
        //1. load all Parameter key/values to args
        Parameter.values().each { parameter ->
            args.put(parameter.getKey(), parameter.getValue())
        }
        //2. load all string keys/values from env
        Map envVars = context.env.getEnvironment()
        envVars.each { k, v ->
            args.put(k, v)
        }
        //3. load all string keys/values from params
        Map jobParams = currentBuild.rawBuild.getAction(ParametersAction)
        jobParams.each { k, v ->
            args.put(k, v)
        }
        //4. investigate how private pipeline can override those values
    }


}
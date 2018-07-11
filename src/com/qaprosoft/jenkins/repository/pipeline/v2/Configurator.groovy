package com.qaprosoft.jenkins.repository.pipeline.v2

import com.cloudbees.groovy.cps.NonCPS
import java.util.List

public class Configurator {

    private def context

    public Configurator(context) {
        this.context = context
        this.loadContext()
    }

    //list of job vars/params as a map
    protected static Map args = [:]

    public enum Parameter {

        //vars
        ADMIN_EMAILS("ADMIN_EMAILS", "qps-auto@qaprosoft.com"),
        CARINA_CORE_VERSION("CARINA_CORE_VERSION", "5.2.4.105"),
        CORE_LOG_LEVEL("CORE_LOG_LEVEL", "INFO"),
        ZAFIRA_SERVICE_URL("ZAFIRA_SERVICE_URL", "http://zafira:8080/zafira-ws"),
        JACOCO_BUCKET("JACOCO_BUCKET", "jacoco.qaprosoft.com"),
        JACOCO_ENABLE("JACOCO_ENABLE", "false"),
        JOB_MAX_RUN_TIME("JOB_MAX_RUN_TIME", "60"),
        JOB_URL("JOB_URL", ""),
        JOB_NAME("JOB_NAME", ""),
        JOB_BASE_NAME("JOB_BASE_NAME", ""),
        BUILD_NUMBER("BUILD_NUMBER", ""),
        NGINX_HOST("NGINX_HOST", "localhost"),
        NGINX_PORT("NGINX_PORT", "80"),
        NGINXT_PROTOCOL("NGINXT_PROTOCOL", "http"),

        //params
        ZAFIRA_ENABLED("zafira_enabled", "true"),
        BUILD("build", ""),
        BUILD_USER_ID("BUILD_USER_ID", ""),
        BUILD_USER_FIRST_NAME("BUILD_USER_FIRST_NAME", ""),
        BUILD_USER_LAST_NAME("BUILD_USER_LAST_NAME", ""),
        BUILD_USER_EMAIL("BUILD_USER_EMAIL", ""),
        PROJECT("project", ""),
        SUB_PROJECT("sub_project", ""),
        ZAFIRA_PROJECT("zafira_project", ""),
        SUITE("suite", ""),
        BRANCH("branch", ""),
        FOLDER("folder", ""),
        FORK("fork", "false"),
        PLATFORM("platform", ""),
        ENV("env", ""),
        BROWSER("browser", ""),
        BROWSER_VERSION("browser_version", ""),
        EMAIL_LIST("email_list", ""),
        FAILURE_EMAIL_LIST("failure_email_list", ""),
        DEFAULT_POOL("DefaultPool", ""),
        NODE("node", ""),
        PRIORITY("priority", ""),
        DEVELOP("develop", "false"),
        DEBUG("debug", "false"),
        RETRY_COUNT("retry_count", ""),
        THREAD_COUNT("thread_count", ""),
        KEEP_ALL_SCREENSHOTS("keep_all_screenshots", ""),
        AUTO_SCREENSHOT("auto_screenshot", ""),
        RERUN_FAILURES("rerunFailures", "false"),
        RECOVERY_MODE("recoveryMode", ""),
        ENABLE_VNC("enableVNC", "false"),
        ENABLE_VIDEO("enableVideo", "false"),
        GIT_BRANCH("git_branch", ""),
        GIT_URL("git_url", ""),
        GIT_COMMIT("GIT_COMMIT", ""),
        SCM_URL("scm_url", ""),
        JAVA_AWT_HEADLESS("java.awt.headless", ""),
        CI_RUN_ID("ci_run_id", ""),
        CI_URL("ci_url", ""),
        CI_BUILD("ci_build", ""),
        CI_BUILD_CAUSE("ci_build_cause", ""),
        CI_PARENT_URL("ci_parent_url", ""),
        CI_PARENT_BUILD("ci_parent_build", ""),
        CI_USER_ID("ci_user_id", ""),
        UPSTREAM_JOB_ID("upstream_job_id", ""),
        UPSTREAM_JOB_BUILD_NUMBER("upstream_job_build_number", ""),
        HASHCODE("hashcode", ""),
        DO_REBUILD("doRebuild", "false")

        private final String key;
        private final String value;

        Parameter(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @NonCPS
        public String getKey() {
            return key
        }

        @NonCPS
        public String getValue() {
            return value;
        }

    }

    @NonCPS
    public static String get(Parameter param) {
        return args.get(param.getKey())
    }

    public static void set(Parameter param, String value) {
        return args.put(param.getKey(), value)
    }

    @NonCPS
    public static String get(String paramName) {
        return args.get(paramName)
    }

    public static void set(String paramName, String value) {
        return args.put(paramName, value)
    }

    @NonCPS
    public void loadContext() {
        //1. load all Parameter key/values to args
        def enumValues  = Parameter.values()
        for (enumValue in enumValues) {
            args.put(enumValue.getKey(), enumValue.getValue())
        }
        for (arg in args) {
            context.println(arg)
        }
        //2. load all string keys/values from env
        def envVars = context.env.getEnvironment()
        for (var in envVars) {
            args.put(var.key, var.value)
        }
        for (arg in args) {
            context.println(arg)
        }
        //3. load all string keys/values from params
        def jobParams = context.currentBuild.rawBuild.getAction(ParametersAction)
        for (param in jobParams) {
            args.put(param.name, param.value)
        }
        for (arg in args) {
            context.println(arg)
        }
        //4. TODO: investigate how private pipeline can override those values
    }

}
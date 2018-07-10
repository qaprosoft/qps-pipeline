package com.qaprosoft.jenkins.repository.pipeline.v2

import com.cloudbees.groovy.cps.NonCPS
import java.util.List

class Configurator {

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
        GITHUB_API_URL("GITHUB_API_URL", 'https://api.$GITHUB_HOST/'),
        GITHUB_HOST("GITHUB_HOST", "github.com"),
        GITHUB_HTML_URL("GITHUB_HTML_URL", 'https://$GITHUB_HOST/$GITHUB_ORGANIZATION'),
        GITHUB_OAUTH_TOKEN("GITHUB_OAUTH_TOKEN", "CHANGE_ME"),
        GITHUB_ORGANIZATION("GITHUB_ORGANIZATION", "qaprosoft"),
        GITHUB_SSH_URL("GITHUB_SSH_URL", 'git@$GITHUB_HOST:$GITHUB_ORGANIZATION'),
        JACOCO_BUCKET("JACOCO_BUCKET", "jacoco.qaprosoft.com"),
        JACOCO_ENABLE("JACOCO_ENABLE", "false"),
        JENKINS_SECURITY_INITIALIZED("JENKINS_SECURITY_INITIALIZED", "true"),
        JOB_MAX_RUN_TIME("JOB_MAX_RUN_TIME", "60"),
        QPS_PIPELINE_GIT_BRANCH("QPS_PIPELINE_GIT_BRANCH", "demo"),
        QPS_PIPELINE_GIT_URL("QPS_PIPELINE_GIT_URL", "git@github.com:qaprosoft/qps-pipeline.git"),
        SELENIUM_HOST("SELENIUM_HOST", "demo.qaprosoft.com"),
        SELENIUM_PORT("SELENIUM_PORT", "4444"),
        SELENIUM_PROTOCOL("SELENIUM_PROTOCOL", "http"),
        SELENIUM_URL("SELENIUM_URL", '$SELENIUM_PROTOCOL://$SELENIUM_HOST:$SELENIUM_PORT/wd/hub'),
        ZAFIRA_ACCESS_TOKEN("ZAFIRA_ACCESS_TOKEN", "CHANGE_ME"),
        ZAFIRA_BASE_CONFIG("ZAFIRA_BASE_CONFIG", '-Dzafira_enabled=true -Dzafira_rerun_failures=$rerun_failures -Dzafira_service_url=$ZAFIRA_SERVICE_URL -Dgit_branch=$branch -Dgit_commit=$GIT_COMMIT -Dgit_url=$repository -Dci_user_id=$BUILD_USER_ID -Dci_user_first_name=$BUILD_USER_FIRST_NAME -Dci_user_last_name=$BUILD_USER_LAST_NAME -Dci_user_email=$BUILD_USER_EMAIL -Dzafira_access_token=$ZAFIRA_ACCESS_TOKEN'),
        ZAFIRA_SERVICE_URL("ZAFIRA_SERVICE_URL", "http://zafira:8080/zafira-ws"),
        JOB_URL("JOB_URL", ""),
        JOB_NAME("JOB_NAME", ""),
        JOB_BASE_NAME("JOB_BASE_NAME", ""),
        BUILD_NUMBER("BUILD_NUMBER", ""),


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
            context.println(enumValue)
            args.put(enumValue.getKey(), enumValue.getValue())
        }
        //2. load all string keys/values from env
        def envVars = context.env.getEnvironment()
        for (var in envVars) {
            context.println(var)
            args.put(var.key, var.value)
        }
        //3. load all string keys/values from params
        def jobParams = context.currentBuild.rawBuild.getAction(ParametersAction)
        for (param in jobParams) {
            context.println(param)
            args.put(param.name, param.value)
        }
        //4. TODO: investigate how private pipeline can override those values
    }

}
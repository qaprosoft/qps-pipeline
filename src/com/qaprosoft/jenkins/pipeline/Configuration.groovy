package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.Logger

public class Configuration {

    private def context

    private final static def mustOverride = "{must_override}"
    public final static def TESTRAIL_UPDATER_JOBNAME = "testrail-updater"
    public final static def QTEST_UPDATER_JOBNAME = "qtest-updater"
    
    private static final String CAPABILITIES = "capabilities"

    //list of CI job params as a map
    protected static Map params = [:]
    //list of required goals vars which must present in command line obligatory
    protected static Map vars = [:]


    public Configuration(context) {
        this.context = context
        this.loadContext()
    }

    @NonCPS
    public static Map getParams() {
        return params
    }

    @NonCPS
    public static Map getVars() {
        return vars
    }

    public enum Parameter {

        //vars
        CORE_LOG_LEVEL("CORE_LOG_LEVEL", "INFO"),
        //to enable default jacoco code coverage instrumenting we have to find a way to init valid AWS aws-jacoco-token on Jenkins preliminary
        //the biggest problem is that AWS key can't be located in public repositories
        JACOCO_BUCKET("JACOCO_BUCKET", "jacoco.qaprosoft.com"),
        JACOCO_REGION("JACOCO_REGION", "us-west-1"),
        JACOCO_ENABLE("JACOCO_ENABLE", "false"),
        JOB_MAX_RUN_TIME("JOB_MAX_RUN_TIME", "60"),

        QPS_PIPELINE_GIT_BRANCH("QPS_PIPELINE_GIT_BRANCH", mustOverride),
        QPS_PIPELINE_GIT_URL("QPS_PIPELINE_GIT_URL", "git@github.com:qaprosoft/qps-pipeline.git"),
        ADMIN_EMAILS("ADMIN_EMAILS", mustOverride),

        GITHUB_HOST("GITHUB_HOST", "github.com"),
        GITHUB_API_URL("GITHUB_API_URL", "https://api.\${GITHUB_HOST}/"),
        GITHUB_ORGANIZATION("GITHUB_ORGANIZATION", "qaprosoft"),
        GITHUB_HTML_URL("GITHUB_HTML_URL", "https://\${GITHUB_HOST}/\${GITHUB_ORGANIZATION}"),
        GITHUB_OAUTH_TOKEN("GITHUB_OAUTH_TOKEN", mustOverride, true),
        GITHUB_SSH_URL("GITHUB_SSH_URL", "git@\${GITHUB_HOST}:\${GITHUB_ORGANIZATION}"),

        SELENIUM_PROTOCOL("SELENIUM_PROTOCOL", "http"),
        SELENIUM_HOST("SELENIUM_HOST", "\${QPS_HOST}"),
        SELENIUM_PORT("SELENIUM_PORT", "4444"),
        SELENIUM_URL("SELENIUM_URL", "\${SELENIUM_PROTOCOL}://demo:demo@\${SELENIUM_HOST}:\${SELENIUM_PORT}/wd/hub"),
        HUB_MODE("hub_mode", "selenium"),

        QPS_HUB("QPS_HUB", "\${SELENIUM_PROTOCOL}://${SELENIUM_HOST}:\${SELENIUM_PORT}"),

        ZAFIRA_ACCESS_TOKEN("ZAFIRA_ACCESS_TOKEN", mustOverride, true),
        ZAFIRA_SERVICE_URL("ZAFIRA_SERVICE_URL", "http://zafira:8080/zafira-ws"),

        JOB_URL("JOB_URL", mustOverride),
        JOB_NAME("JOB_NAME", mustOverride),
        JOB_BASE_NAME("JOB_BASE_NAME", mustOverride),
        BUILD_NUMBER("BUILD_NUMBER", mustOverride),

        SCREEN_RECORD_FTP("screen_record_ftp", "ftp://\${QPS_HOST}/%s.mp4"),
        SCREEN_RECORD_HOST("screen_record_host", "http://\${QPS_HOST}/video/%s"),
        SCREEN_RECORD_USER("screen_record_user", "qpsdemo"),
        SCREEN_RECORD_PASS("screen_record_pass", "qpsdemo"),
        SCREEN_RECORD_DURATION("screen_record_duration", "1800"),

        S3_SAVE_SCREENSHOTS("s3_save_screenshots", "true"),
        OPTIMIZE_VIDEO_RECORDING("optimize_video_recording", "false"),

        VNC_DESKTOP("vnc_desktop", "%s://%s:%s/vnc/%s"),
        VNC_MOBILE("vnc_mobile", "%s://%s:%s/websockify"),
        VNC_PROTOCOL("vnc_protocol", "ws"),
        VNC_HOST("vnc_host", "\${QPS_HOST}"),
        VNC_PORT("vnc_port", "80"),

        ENABLE_VNC("capabilities.enableVNC", "true"),
        ENABLE_VIDEO("capabilities.enableVideo", "true"),
        ENABLE_STF("capabilities.STF_ENABLED", "false"),

        TIMEZONE("user.timezone", "UTC"),

        S3_LOCAL_STORAGE("s3_local_storage", "/opt/apk"),
        APPCENTER_LOCAL_STORAGE("appcenter_local_storage", "/opt/apk"),

        BROWSERSTACK_ACCESS_KEY("BROWSERSTACK_ACCESS_KEY", "\${BROWSERSTACK_ACCESS_KEY}", true),

        //Make sure that URLs have trailing slash
        TESTRAIL_SERVICE_URL("TESTRAIL_SERVICE_URL", ""), // "https://<CHANGE_ME>.testrail.com?/api/v2/"
        TESTRAIL_ENABLE("testrail_enabled", "false"),

        QTEST_SERVICE_URL("QTEST_SERVICE_URL", ""), // "https://<CHANGE_ME>/api/v3/"
        QTEST_ENABLE("qtest_enabled", "false"),

        private final String key
        private final String value
        private final Boolean isSecured

        Parameter(String key, String value) {
            this(key, value, false)
        }

        Parameter(String key, String value, Boolean isSecured) {
            this.key = key
            this.value = value
            this.isSecured = isSecured
        }

        @NonCPS
        public String getKey() {
            return key
        }

        @NonCPS
        public String getValue() {
            return value
        }

        @NonCPS
        public Boolean isSecured() {
            return isSecured
        }
    }

    @NonCPS
    public void loadContext() {
        // 1. load all obligatory Parameter(s) and their default key/values to vars.
        // any non empty value should be resolved in such order: Parameter, envvars and jobParams

        def enumValues = Parameter.values()
        for (enumValue in enumValues) {
            //a. set default values from enum
            vars.put(enumValue.getKey(), enumValue.getValue())
        }

        //b. redefine values from global variables if any
        def envVars = context.env.getEnvironment()
        for (var in vars) {
            if (envVars.get(var.getKey()) != null) {
                vars.put(var.getKey(), envVars.get(var.getKey()))
            }
        }

        // 2. Load all job parameters into unmodifiable map
        def jobParams = context.currentBuild.rawBuild.getAction(ParametersAction)
        for (param in jobParams) {
			// exclude adding zafiraFields and overrideFields as global param. They will be parsed later and put by each concrete param into the list
			if ("overrideFields".equals(param.name) ||
				"zafiraFields".equals(param.name)) {
				continue;
			}
			
            if (param.value != null) {
                putParamCaseInsensitive(param.name, param.value)
            }
        }

        //3. Replace vars and/or params with capabilities prefix
        parseValues(params.get(CAPABILITIES), CAPABILITIES)
        
        //4. Replace vars and/or params with zafiraFields values
        parseValues(params.get("zafiraFields"))
        //5. Replace vars and/or params with overrideFields values
        parseValues(params.get("overrideFields"))

        def securedParameters = []
        for (enumValue in enumValues) {
            if (enumValue.isSecured()) {
                securedParameters << enumValue.getKey()
            }
        }

        for (var in vars) {
            if (var.getKey() in securedParameters) {
                context.println(var.getKey() + ": ********")
            } else {
                context.println(var)
            }
        }

        for (param in params) {
            context.println(param)
        }
        //6. TODO: investigate how private pipeline can override those values
        // public static void set(Map args) - ???
    }

    @NonCPS
    private static void parseValues(values){
        parseValues(values, "")
    }
    
    @NonCPS
    private static void parseValues(values, keyPrefix){
        if (values) {
            for (value in values.split(",")) {
                def keyValueArray = value.trim().split("=")
                if (keyValueArray.size() > 1) {
                    def parameterName = keyValueArray[0]
                    def parameterValue = keyValueArray[1]
                    if (keyPrefix.isEmpty()) {
                        putParamCaseInsensitive(parameterName, parameterValue)
                    } else {
                        putParamCaseInsensitive(keyPrefix + "." + parameterName, parameterValue)
                    }
                }
            }
        }
    }

    @NonCPS
    private static void putParamCaseInsensitive(parameterName, parameterValue) {
        if (vars.get(parameterName)) {
            vars.put(parameterName, parameterValue)
        } else if (vars.get(parameterName.toUpperCase())) {
            vars.put(parameterName.toUpperCase(), parameterValue)
        } else {
            params.put(parameterName, parameterValue)
        }
    }

    @NonCPS
    public static String get(Parameter param) {
        return get(param.getKey())
    }

    @NonCPS
    public static String get(String paramName) {
        if (params.get(paramName) != null) {
            return params.get(paramName)
        }
        return vars.get(paramName)
    }

    public static void set(Parameter param, String value) {
        set(param.getKey(), value)
    }

    public static void set(String paramName, String value) {
        vars.put(paramName, value)
    }

    // simple way to reload as a bundle all project custom arguments from private pipeline
    public static void set(Map args) {
        for (arg in args) {
            vars.put(arg.getKey(), arg.getValue())
        }
    }

    /*
     * replace all ${PARAM} occurrences by real values from var/params
     * String cmd
     * return String cmd
     */

    @NonCPS
    public static String resolveVars(String cmd) {
        return cmd.replaceAll('\\$\\{[^\\{\\}]*\\}') { m -> get(m.substring(2, m.size() - 1)) }
    }

    public static void remove(String key) {
        vars.remove(key)
        params.remove(key)
    }

}

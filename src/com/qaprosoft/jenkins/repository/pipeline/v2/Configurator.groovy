package com.qaprosoft.jenkins.repository.pipeline.v2

import com.cloudbees.groovy.cps.NonCPS
import java.util.List

public class Configurator {

    private def context
	
	private final static def mustOverride = "{must_override}"

		//list of job vars/params as a map
	protected static Map params = [:]
	//list of required goals params which must present in command line obligatory
	protected static Map args = [:]
	
	
    public Configurator(context) {
        this.context = context
        this.loadContext()
		
    }

    @NonCPS
    public static Map getParams() {
        return params
    }

    @NonCPS
    public static Map getArgs() {
        return args
    }

    public enum Parameter {

        //args
        CARINA_CORE_VERSION("CARINA_CORE_VERSION", "5.2.4.107"),
        CORE_LOG_LEVEL("CORE_LOG_LEVEL", "INFO"),
		JACOCO_BUCKET("JACOCO_BUCKET", "jacoco.qaprosoft.com"),
		JACOCO_ENABLE("JACOCO_ENABLE", "false"),
		JOB_MAX_RUN_TIME("JOB_MAX_RUN_TIME", "60"),
	
		QPS_PIPELINE_GIT_BRANCH("QPS_PIPELINE_GIT_BRANCH", mustOverride),
		QPS_PIPELINE_GIT_URL("QPS_PIPELINE_GIT_URL", mustOverride),
		ADMIN_EMAILS("ADMIN_EMAILS", mustOverride),
		
        GITHUB_HOST("GITHUB_HOST", mustOverride),
        GITHUB_API_URL("GITHUB_API_URL", mustOverride),
        GITHUB_ORGANIZATION("GITHUB_ORGANIZATION", mustOverride),
        GITHUB_HTML_URL("GITHUB_HTML_URL", mustOverride),
        GITHUB_OAUTH_TOKEN("GITHUB_OAUTH_TOKEN", mustOverride),
        GITHUB_SSH_URL("GITHUB_SSH_URL", mustOverride),

        SELENIUM_PROTOCOL("SELENIUM_PROTOCOL", mustOverride),
        SELENIUM_HOST("SELENIUM_HOST", mustOverride),
        SELENIUM_PORT("SELENIUM_PORT", mustOverride),
        SELENIUM_URL("SELENIUM_URL", mustOverride),
		
        ZAFIRA_ACCESS_TOKEN("ZAFIRA_ACCESS_TOKEN", mustOverride),
        ZAFIRA_SERVICE_URL("ZAFIRA_SERVICE_URL", "http://zafira:8080/zafira-ws"),
		
        JOB_URL("JOB_URL", mustOverride),
        JOB_NAME("JOB_NAME", mustOverride),
        JOB_BASE_NAME("JOB_BASE_NAME", mustOverride),
        BUILD_NUMBER("BUILD_NUMBER", mustOverride),
        NGINX_HOST("NGINX_HOST", mustOverride),
        NGINX_PORT("NGINX_PORT", mustOverride),
        NGINXT_PROTOCOL("NGINXT_PROTOCOL", mustOverride),

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
        return params.get(paramName)
    }

    public static void set(String paramName, String value) {
        return params.put(paramName, value)
    }

    public static void remove(String key) {
        return params.remove(key)
    }

    @NonCPS
    public void loadContext() {
        //1. load all obligatory Parameter(s) and their default key/values to args
        def enumValues  = Parameter.values()
		for (enumValue in enumValues) {
			if (!enumValue.getValue().equals(mustOverride)){
				args.put(enumValue.getKey(), enumValue.getValue())
			}
		}
		for (var in args) {
            context.println(var)
        }
        //2. load all string keys/values from env
        def envVars = context.env.getEnvironment()
        for (var in envVars) {
            if (var.value != null) {
                args.put(var.key, var.value)
            }
        }
        for (var in args) {
            context.println(var)
        }
        //3. load all string keys/values from params
        def jobParams = context.currentBuild.rawBuild.getAction(ParametersAction)
        for (param in jobParams) {
            if (param.value != null) {
                params.put(param.name, param.value)
            }
        }
        for (param in params) {
            context.println(param)
        }
        //4. TODO: investigate how private pipeline can override those values
    }

}
package com.qaprosoft.jenkins.repository.pipeline.v2

import com.cloudbees.groovy.cps.NonCPS
import java.util.List

public class Configurator {

    private def context
	
	private final static def mustOverride = "{must_override}"

	//list of CI job params as a map
	protected static Map params = [:]
	//list of required goals vars which must present in command line obligatory
	protected static Map vars = [:]
	
	
    public Configurator(context) {
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
        CARINA_CORE_VERSION("CARINA_CORE_VERSION", "5.2.4.107"),
        CORE_LOG_LEVEL("CORE_LOG_LEVEL", "INFO"),
		//to enable default jacoco code coverage instrumenting we have to find a way to init valid AWS aws-jacoco-token on Jenkins preliminary
		//the biggest problem is that AWS key can't be located in public repositories
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
        NGINX_PROTOCOL("NGINX_PROTOCOL", mustOverride),
		
		TIMEZONE("user.timezone", "UTC"),

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
	public void loadContext() {
		// 1. load all obligatory Parameter(s) and their default key/values to vars. 
		// any non empty value should be resolved in such order: Parameter, envvars and jobParams 
		
		def enumValues  = Parameter.values()
		def envVars = context.env.getEnvironment()
		
		for (enumValue in enumValues) {
			//a. set default values from enum
			vars.put(enumValue.getKey(), enumValue.getValue())
			
			//b. redefine values from global variables if any
			if (envVars.get(enumValue.getKey()) != null) {
				vars.put(enumValue.getKey(), envVars.get(enumValue.getKey()))
			}
			
		}
		
		for (var in vars) {
			context.println(var)
		}

		// 2. Load all job parameters into unmodifiable map
		def jobParams = context.currentBuild.rawBuild.getAction(ParametersAction)
		for (param in jobParams) {
			if (param.value != null) {
				params.put(param.name, param.value)
			}
		}
		
		for (param in params) {
			context.println(param)
		}
	
		//3. TODO: investigate how private pipeline can override those values
		// public static void set(Map args) - ???
	}

    @NonCPS
    public static String get(Parameter param) {
		return get(param.getKey());
    }
	
	@NonCPS
	public static String get(String paramName) {
		if (params.get(paramName) != null) {
			return params.get(paramName);
		}
		return vars.get(paramName)
	}

    public static void set(Parameter param, String value) {
        set(param.getKey(), value)
    }

    public static void set(String paramName, String value) {
        return vars.put(paramName, value)
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
	public static String prepareCmd(String cmd) {
		cmd += " -Dqwe=rty"
		return cmd
	}

    public static void remove(String key) {
        return vars.remove(key)
    }

}
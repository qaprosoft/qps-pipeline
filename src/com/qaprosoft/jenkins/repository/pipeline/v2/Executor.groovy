package com.qaprosoft.jenkins.repository.pipeline.v2

@Grab('org.testng:testng:6.8.8')
import org.testng.xml.Parser;
import org.testng.xml.XmlSuite;
import com.cloudbees.groovy.cps.NonCPS

//import static java.util.UUID.randomUUID

import com.qaprosoft.scm.ISCM;
import com.qaprosoft.scm.github.GitHub;

public abstract class Executor {
	//pipeline context to provide access to existing pipeline methods like echo, sh etc...
	protected def context

	//list of job parameters as a map
	protected def jobParams = [:]

	//list of job variables as a map
	protected def jobVars = [:]
	
	protected ISCM scmClient
	
	public Executor(context) {
		this.context = context
	}

	
	protected def initParams(currentBuild) {
		// read all job params and put them into the map
		def params = [:]
		def myparams = currentBuild.rawBuild.getAction(ParametersAction)
		for( p in myparams ) {
			params.put(p.name.toString(), p.value)
		}
		
		def goals = ""
		params.each { k, v -> goals = goals + " -D${k}=${v}"}
		context.echo "goals: ${goals}"
	
		return params
	}
	
	protected def initVars(env) {
		// read all job params and put them into the map
		def vars = [:]

		def envvars = env.getEnvironment()
		envvars.each{ k, v ->
			vars.put(k, v)
		}

		def variables = ""
		vars.each { k, v -> variables = variables + " -D${k}=${v}"}
		context.echo "variables: ${variables}"

		return vars
	}
	
	protected clean() {
		context.stage('Wipe out Workspace') {
			context.deleteDir()
		}
	}
	
	
	protected void printStackTrace(Exception ex) {
		context.println("exception: " + ex.getMessage())
		context.println("exception class: " + ex.getClass().getName())
		context.println("stacktrace: " + Arrays.toString(ex.getStackTrace()))
	}


	protected String getWorkspace() {
		return context.pwd()
	}
	
	protected String getBuildUser() {
		try {
			return context.currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()
		} catch (Exception e) {
			return ""
		}
		
	}

    @NonCPS
    protected boolean isItemAvailable(String path) {
        boolean available = false
        def job = Jenkins.instance.getItemByFullName(path)
        if (job) {
            available = true
        }
        return available
    }

	protected Object parseJSON(String path) {
		def inputFile = new File(path)
		def content = new groovy.json.JsonSlurperClassic().parseFile(inputFile, 'UTF-8')
		return content
	}

	
	XmlSuite parseSuite(String path) {
		def xmlFile = new Parser(path)
		xmlFile.setLoadClasses(false)
		
		List<XmlSuite> suiteXml = xmlFile.parseToList()
		XmlSuite currentSuite = suiteXml.get(0)
		return currentSuite
	}

	String parseSuiteToText(String path) {
		def content = new File(path).getText()
		return content
	}
}
package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.Logger
import com.qaprosoft.scm.ISCM

public abstract class AbstractRunner {
    protected def context
    protected ISCM scmClient
    protected Logger logger
    protected final def FACTORY_TARGET = "qps-pipeline/src/com/qaprosoft/jenkins/jobdsl/Factory.groovy"
    protected def additionalClasspath = "qps-pipeline/src"

    //this is very important line which should be declared only as a class member!
    protected Configuration configuration = new Configuration(context)

    public AbstractRunner(context) {
        this.context = context
        this.logger = new Logger(context)
    }

    //Methods
    abstract public void build()

    //Events
    abstract public void onPush()
    abstract public void onPullRequest()
	
	
	protected void executeMavenGoals(goals) {
		withMaven(
			// Maven installation declared in the Jenkins "Global Tool Configuration"
			maven: 'M3',
			// Maven settings.xml file defined with the Jenkins Config File Provider Plugin
			// Maven settings and global settings can also be defined in Jenkins Global Tools Configuration
			mavenSettingsConfig: 'settings.xml',
			mavenLocalRepo: '.repository') {
	 
			// Run the maven build
			
			if (context.isUnix()) {
				context.sh "'mvn' -B ${goals}"
			} else {
				context.bat "mvn -B ${goals}"
			}

	 
		} // withMaven will discover the generated Maven artifacts, JUnit Surefire & FailSafe & FindBugs reports...

	}

}

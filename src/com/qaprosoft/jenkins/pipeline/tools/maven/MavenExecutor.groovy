package com.qaprosoft.jenkins.pipeline.tools.maven

import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.Logger

import static com.qaprosoft.jenkins.pipeline.Executor.*

public class MavenExecutor {
    //TODO: migreate to traits as only it is supported in pipelines
    // https://issues.jenkins-ci.org/browse/JENKINS-46145

    def MAVEN_TOOL='M3'

    public void executeMavenGoals(goals) {
        logger.debug("Maven mixing->executeMavenGoals")
        context.withMaven(
                //EXPLICIT: Only the Maven publishers explicitly configured in "withMaven(options:...)" are used.
                publisherStrategy: 'EXPLICIT',
                // Maven installation declared in the Jenkins "Global Tool Configuration"
                maven: "${MAVEN_TOOL}",
                // Maven settings.xml file defined with the Jenkins Config File Provider Plugin
                // Maven settings and global settings can also be defined in Jenkins Global Tools Configuration
                //mavenSettingsConfig: 'settings',
                //mavenLocalRepo: ".repository"
        ) {
            // Run the maven build
            buildGoals(goals)
        }
    }

    public void executeMavenGoals(goals, mavenSettingsConfig) {
        logger.info("Maven mixing->executeMavenGoals")
        context.withMaven(
                //EXPLICIT: Only the Maven publishers explicitly configured in "withMaven(options:...)" are used.
                publisherStrategy: 'EXPLICIT',
                // Maven installation declared in the Jenkins "Global Tool Configuration"
                maven: "${MAVEN_TOOL}",
                // Maven settings.xml file defined with the Jenkins Config File Provider Plugin
                // Maven settings and global settings can also be defined in Jenkins Global Tools Configuration
                mavenSettingsConfig: "${mavenSettingsConfig}") {
            // Run the maven build
            buildGoals(goals)
        }
    }

    public void executeMavenGoals(goals, mavenSettingsConfig, mavenLocalRepo) {
        logger.info("Maven mixing->executeMavenGoals")
        context.withMaven(
                //EXPLICIT: Only the Maven publishers explicitly configured in "withMaven(options:...)" are used.
                publisherStrategy: 'EXPLICIT',
                // Maven installation declared in the Jenkins "Global Tool Configuration"
                maven: "${MAVEN_TOOL}",
                // Maven settings.xml file defined with the Jenkins Config File Provider Plugin
                // Maven settings and global settings can also be defined in Jenkins Global Tools Configuration
                mavenSettingsConfig: "${mavenSettingsConfig}",
                mavenLocalRepo: "${mavenLocalRepo}") {
            // Run the maven build
            buildGoals(goals)
        }
    }

    private def buildGoals(goals) {
        if(context.env.getEnvironment().get("QPS_PIPELINE_LOG_LEVEL").equals(Logger.LogLevel.DEBUG.name())){
            goals = goals + " -e -X"
        }
        // parse goals replacing sensitive info by *******
        if (context.isUnix()) {
            def filteredGoals = filterSecuredParams(goals)
            logger.info("mvn -B ${filteredGoals}")
            context.sh """
                        set +x
                        'mvn' -B ${goals}
                        set -x
                       """
        } else {
            context.bat "mvn -B ${goals}"
        }
    }

    public void compile(pomFile='pom.xml', isPullRequest=false) {
        context.stage('Maven Compile') {
            // [VD] don't remove -U otherwise latest dependencies are not downloaded
            def goals = "-U clean compile test -f ${pomFile}"
            def extraGoals = ""
            extraGoals += Configuration.get(Configuration.Parameter.JACOCO_ENABLE).toBoolean() ? "jacoco:report-aggregate" : ""
            if (isPullRequest) {
                // no need to run unit tests for PR analysis
                extraGoals += " -DskipTests"
            } else {
                //run unit tests to detect code coverage but don't fail the build in case of any failure
                //TODO: for build process we can't use below goal!
                extraGoals += " -Dmaven.test.failure.ignore=true"
            }
            executeMavenGoals("${goals} ${extraGoals}")
        }
    }

    protected def getProjectPomFiles() {
  		def pomFiles = []
  		def files = context.findFiles(glob: "**/pom.xml")

  		if (files.length > 0) {
  			logger.info("Number of pom.xml files to analyze: " + files.length)

  			int curLevel = 5 //do not analyze projects where highest pom.xml level is lower or equal 5
  			for (pomFile in files) {
  				def path = pomFile.path
  				int level = path.count("/")
  				logger.debug("file: " + path + "; level: " + level + "; curLevel: " + curLevel)
  				if (level < curLevel) {
  					curLevel = level
  					pomFiles.clear()
  					pomFiles.add(pomFile.path)
  				} else if (level == curLevel) {
  					pomFiles.add(pomFile.path)
  				}
  			}
  			logger.info("PROJECT POMS: " + pomFiles)
  		}
  		return pomFiles
  	}
}

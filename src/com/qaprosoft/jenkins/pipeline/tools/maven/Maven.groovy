package com.qaprosoft.jenkins.pipeline.tools.maven

import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.Logger

public class Maven {
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
    private def filterSecuredParams(goals) {
        def arrayOfParmeters = goals.split()
        context.println("111111111111")
        for (parameter in arrayOfParmeters) {
            if (parameter.contains("token")) {
                def arrayOfString = parameter.split("=")
                context.println(arrayOfParmeters)
            }
        }
        context.println("111111111111")
        return goals
    }

    public def buildGoals(goals) {
        if(context.env.getEnvironment().get("QPS_PIPELINE_LOG_LEVEL").equals(Logger.LogLevel.DEBUG.name())){
            goals = goals + " -e -X"
        }
        // parse goals replacing sensitive info by *******
        if (context.isUnix()) {
            //context.sh returnStdout: true, script: "#!/bin/bash +x\n 'mvn' -B ${goals}"
            def filteredGoals = filterSecuredParams(goals)
            context.sh """
                        echo "mvn -B ${filteredGoals}"
                        set +x
                        'mvn' -B ${goals}
                        set -x 
                       """
        } else {
            context.bat "mvn -B ${goals}"
        }
    }
}

import com.qaprosoft.jenkins.Logger
import groovy.transform.Field

import static com.qaprosoft.jenkins.pipeline.Executor.*

@Field final Logger logger = new Logger(this)
@Field final String MAVEN_TOOL = 'M3'


def call(goals = '-U clean compile test', mavenSettingsConfig = '', mavenLocalRepo = '') {
    logger.info("mavenBuild->call")
    logger.debug("mavenSettingsConfig: " + mavenSettingsConfig)
    withMaven(
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
    if (logger.pipelineLogLevel.equals(Logger.LogLevel.DEBUG)) {
        goals = goals + " -e -X"
    }
    // parse goals replacing sensitive info by *******
    if (isUnix()) {
        def filteredGoals = filterSecuredParams(goals)
        println("mvn -B ${filteredGoals}")
        sh """
           set +x
             'mvn' -B ${goals}
             set -x
           """
    } else {
        bat "mvn -B ${goals}"
    }
}


import groovy.transform.Field
import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.Logger
import static com.qaprosoft.jenkins.pipeline.Executor.*

@Field final String MAVEN_TOOL = 'M3'

//TODO: reuse valid logger instead of println later
def call(goals = '-U clean compile test', mavenSettingsConfig = '', mavenLocalRepo = '') {
    println("mavenBuild->call")
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
    if(env.getEnvironment().get("QPS_PIPELINE_LOG_LEVEL").equals(Logger.LogLevel.DEBUG.name())){
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


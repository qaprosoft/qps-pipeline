import com.qaprosoft.jenkins.Logger
import groovy.transform.Field

@Field final Logger logger = new Logger(this)
@Field final String GRADLE_TOOL = "G6"

def call(goals) {
	stage('Gradle Build') {
		logger.info("gradleBuild->call")
		def script = ""

		if(!fileExists('gradlew')) {
			script = tool name: "${GRADLE_TOOL}", type: 'hudson.plugins.gradle.GradleInstallation'
			script += '/bin/' + ${goals.replace('./gradlew', 'gradle')}
		} else {
			script = "chmod a+x gradlew && ./gradlew ${goals} "
		}

		logger.info(script)

		//sh 'cp ./config/gradle.properties ./gradle.properties'
		
		if (isUnix()) {
			withGradle() {sh $script}
		} else {
			bat 'gradlew.bat build'
		}
	}
}

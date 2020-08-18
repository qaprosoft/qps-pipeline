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
			script += '/bin/' + goals.replace('./gradlew', 'gradle')
		} else {
			// test if this command is needed for gradlew builds
			//sh 'cp ./config/gradle.properties ./gradle.properties' and this param -P version=${version}
			script = "chmod a+x gradlew && ${goals}"
		}
		
		if (isUnix()) {
			withGradle() {sh script}
		} else {
			bat 'gradlew.bat build'
		}
	}
}

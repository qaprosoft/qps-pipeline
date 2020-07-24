import com.qaprosoft.jenkins.Utils
import com.qaprosoft.jenkins.Logger
import groovy.transform.Field

@Field final Logger logger = new Logger(this)

def call(version, registry, registryCreds, dockerFile='Dockerfile') {
	logger.info("dockerBuild -> call")
	push(build(version, registry, dockerFile), registryCreds)
}

def build(version, registry, dockerFile) {
	stage("Docker build Image") {
		try {
			docker.build(registry + ":${version}", "--build-arg version=${version} -f $dockerFile .")
		} catch(Exception e) {
			logger.error("Something went wrong during Docker Build Image Stage \n" + Utils.printStackTrace(e))
		}
	}
}

def push(image, registryCreds) {
	stage("Docker Push Image") {
		try {
			docker.withRegistry('', registryCreds) { image.push() } 
			clean(image)
		} catch(Exception e) {
			logger.error("Something went wrong during Docker Push Image stage \n" + Utils.printStackTrace(e))
		}
	}
}

def clean(image) {
	stage ("Docker Clean Up") {
		sh "docker rmi ${image.imageName()}"
	}
}
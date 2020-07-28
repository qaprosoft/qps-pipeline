import com.qaprosoft.jenkins.Logger
import groovy.transform.Field

@Field final Logger logger = new Logger(this)

def call(version, registry, registryCreds, dockerFile='Dockerfile') {
	logger.info("dockerDeploy -> call")
	push(build(version, registry, dockerFile), registryCreds)
}

def build(version, registry, dockerFile='Dockerfile') {
	stage("Docker build Image") {
		docker.build(registry + ":${version}", "--build-arg version=${version} -f $dockerFile .")
	}
}

def push(image, registryCreds) {
	stage("Docker Push Image") {
		docker.withRegistry('', registryCreds) { image.push() } 
		clean(image)
	}
}

def clean(image) {
	stage ("Docker Clean Up") {
		sh "docker rmi -f ${image.imageName()}"
	}
}
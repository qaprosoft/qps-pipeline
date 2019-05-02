package com.qaprosoft.jenkins.pipeline.tools.docker

class Docker {

    def buildDockerImage(version, registry){
        context.stage('Build Docker Image') {
            context.docker.build(registry + ":${version}", "--build-arg version=${version} .")
        }
    }

    def pushDockerImage(dockerImage, registryCredentials){
        context.stage('Deploy Docker Image') {
            context.docker.withRegistry('', registryCredentials) {
                dockerImage.push()
            }
        }
    }
}

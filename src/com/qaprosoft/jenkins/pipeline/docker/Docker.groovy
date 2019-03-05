package com.qaprosoft.jenkins.pipeline.docker

class Docker {

    def buildDocker(){
        context.stage('Docker Build') {
            context.steps{
                context.script {
                    dockerImage = context.docker.build(registry + ":${version}", "--build-arg version=${version} .")
                }
            }
        }
    }

    stage('Deploy Image') {
        steps{
            script {
                docker.withRegistry('', registryCredentials) {
                    dockerImage.push()
                }
            }
        }
    }
}

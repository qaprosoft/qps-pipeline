package com.qaprosoft.jenkins.pipeline.docker

class Docker {

    def buildDocker(version, registry){
        context.stage('Docker Build') {
            def dockerImage = context.docker.build(registry + ":${version}", "--build-arg version=${version} .")
        }
        return dockerImage
    }

}

//    stage('Deploy Image') {
//        steps{
//            script {
//                docker.withRegistry('', registryCredentials) {
//                    dockerImage.push()
//                }
//            }
//        }
//    }
//}

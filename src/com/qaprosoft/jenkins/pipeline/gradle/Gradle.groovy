package com.qaprosoft.jenkins.pipeline.gradle

class Gradle {

    public void buildGradle(version){
        context.stage('Gradle Build') {
            logger.debug("Gradle mixin->buildGradle")
            if (context.isUnix()) {
                context.sh 'chmod a+x gradlew'
                context.sh 'cp ./config/gradle.properties ./gradle.properties'
                context.sh "./gradlew clean build -P version=${version}"
            } else {
                context.bat 'gradlew.bat build'
            }
        }

    }

    public void performGradleSonarqubeScan(){
        context.stage("Gradle Sonar Scan") {
            if (context.isUnix()) {
                context.sh './gradlew sonarqube'
            } else {
                context.bat 'gradlew.bat sonarqube'
            }
        }
    }
}

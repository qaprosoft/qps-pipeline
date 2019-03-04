package com.qaprosoft.jenkins.pipeline.gradle

class Gradle {

    public void buildGradle(){
        context.stage('Gradle Build') {
            logger.debug("Gradle mixin->buildGradle")
            if (context.isUnix()) {
                context.sh 'cp config/gradle.properties .'
                context.sh 'chmod +x gradlew'
                context.sh './gradlew clean build'
            } else {
                context.bat 'gradlew.bat clean build'
            }
        }

    }

    public void performGradleSonarqubeScan(){
        context.stage("Gradle Sonar Scan") {
            if (context.isUnix()) {
                context.sh './gradlew clean sonarqube'
            } else {
                context.bat 'gradlew.bat clean sonarqube'
            }
        }
    }
}

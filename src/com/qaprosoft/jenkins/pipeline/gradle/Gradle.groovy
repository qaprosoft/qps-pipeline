package com.qaprosoft.jenkins.pipeline.gradle

class Gradle {

    public void buildGradle(){
        context.stage('Gradle Build') {
            logger.debug("Gradle mixin->buildGradle")
            if (context.isUnix()) {
                context.sh 'cp config/gradle.properties .'
                context.sh 'chmod +x gradlew'
                context.sh './gradlew build'
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

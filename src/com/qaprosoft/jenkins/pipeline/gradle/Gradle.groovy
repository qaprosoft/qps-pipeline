package com.qaprosoft.jenkins.pipeline.gradle

class Gradle {

    public void buildGradle(){
        context.stage('Gradle Build') {
            if (context.isUnix()) {
                context.sh './gradlew clean build'
            } else {
                context.bat 'gradlew.bat clean build'
            }
        }

    }
}

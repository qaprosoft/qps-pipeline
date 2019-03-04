package com.qaprosoft.jenkins.pipeline.gradle

class Gradle {

    public void execute(){
        context.stage('Gradle Build') {
            logger.debug("Gradle mixin->buildGradle")
            context.env.JAVA_HOME = "/usr/lib/jvm/java-11-oracle"
            context.env.PATH = "${context.env.JAVA_HOME}/bin:${context.env.PATH}"
            if (context.isUnix()) {
                context.sh 'java -version'
                context.sh 'cp config/gradle.properties .'
                context.sh 'chmod a+x gradlew'
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

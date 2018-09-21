package com.qaprosoft.jenkins.pipeline

trait ExecutorTrait {

    public void printStackTrace(Exception e) {
        println "exception: " + e.getMessage()
        println "exception class: " + e.getClass().getName()
        println "stacktrace: " + Arrays.toString(e.getStackTrace())
    }
}

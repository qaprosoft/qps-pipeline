package com.qaprosoft

class Utils {

    private static final LEVEL_MAP = ["DEBUG": 1, "INFO": 2, "WARN": 3, "ERROR": 4]

    static def debug(logLevel, String message){
        def logMessage = ""
        if(LEVEL_MAP.get(logLevel) < 2){
            logMessage = "${message}\n"
        }
        return logMessage
    }

    static def info(logLevel, String message){
        def logMessage = ""
        if(LEVEL_MAP.get(logLevel) < 3){
            logMessage = "${message}\n"
        }
        return logMessage
    }

    static def warn(logLevel, String message){
        def logMessage = ""
        if(LEVEL_MAP.get(logLevel) < 4){
            logMessage = "${message}\n"
        }
        return logMessage
    }

    static def error(logLevel, message){
        return "${message}\n"
    }
    
    static def printStackTrace(Exception e) {
        def stringStacktrace = ""
        e.getStackTrace().each { traceLine ->
            stringStacktrace = stringStacktrace + "\tat " + traceLine + "\n"
        }
        return "${e.getClass().getName()}: ${e.getMessage()}\n" + stringStacktrace
    }

}

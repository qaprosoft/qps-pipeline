package com.qaprosoft.jenkins

class Logger {

    private static final LEVEL_MAP = ["DEBUG": 1, "INFO": 2, "WARN": 3, "ERROR": 4]

    public static def debug(logLevel, String message){
        def logMessage = ""
        if(LEVEL_MAP.get(logLevel) < 2){
            logMessage = message
        }
        return logMessage
    }

    public static def info(logLevel, String message){
        def logMessage = ""
        if(LEVEL_MAP.get(logLevel) < 3){
            logMessage = message
        }
        return logMessage
    }

    public static def warn(logLevel, String message){
        def logMessage = ""
        if(LEVEL_MAP.get(logLevel) < 4){
            logMessage = message
        }
        return logMessage
    }

    public static def error(message){
        return message
    }

}

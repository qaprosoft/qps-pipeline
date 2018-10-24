package com.qaprosoft

class Logger {

    def context
    def logLevel

    Logger(context, logLevel) {
        this.context = context
        this.logLevel = logLevel
    }

    public debug(String message){
        context.printf Utils.debug(logLevel, message)
    }

    public info(String message){
        context.printf Utils.info(logLevel, message)
    }

    public warn(String message){
        context.printf Utils.warn(logLevel, message)
    }

    public error(String message){
        context.printf Utils.error(logLevel, message)
    }
}

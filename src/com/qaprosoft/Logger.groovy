package com.qaprosoft

class Logger {

    def context
    def logLevel

    Logger(context) {
        this.context = context
        this.logLevel = context?.binding ? context.binding.variables.PIPELINE_LOG_LEVEL : context.env.getEnvironment().get("PIPELINE_LOG_LEVEL")
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

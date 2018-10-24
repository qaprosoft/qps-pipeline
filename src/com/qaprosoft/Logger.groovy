package com.qaprosoft

class Logger {

    private static final LEVEL_MAP = ["DEBUG": 1, "INFO": 2, "WARN": 3, "ERROR": 4]

    def context
    def pipelineLogLevel

    Logger(context) {
        this.context = context
        this.pipelineLogLevel = context?.binding ? context.binding.variables.PIPELINE_LOG_LEVEL : context.env.getEnvironment().get("LOG_LEVEL")
    }

    public debug(String message){
        context.printf log("DEBUG", message)
    }

    public info(String message){
        context.printf log("INFO", message)
    }

    public warn(String message){
        context.printf log("WARN", message)
    }

    public error(String message){
        context.printf log("ERROR", message)
    }

    private def log(String logLevel, String message){
        def logMessage = ""
        if(LEVEL_MAP.get(logLevel) >= LEVEL_MAP.get(pipelineLogLevel)){
            logMessage = "${message}\n"
        }
        return logMessage
    }

}

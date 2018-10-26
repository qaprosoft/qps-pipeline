package com.qaprosoft

class Logger {

    public enum LogLevel {

        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4),

        final int value
        LogLevel(int value) {
            this.value = value
        }
    }

    public enum ContextType {
        JOB_DSL,
        PIPELINE
    }

    def context
    LogLevel pipelineLogLevel
    ContextType contextType

    Logger(context) {
        this.context = context
        this.contextType = context.binding.variables.get("PIPELINE_LOG_LEVEL") ? ContextType.JOB_DSL : ContextType.PIPELINE
        this.pipelineLogLevel = context.binding.variables.get("PIPELINE_LOG_LEVEL") ? LogLevel.valueOf(context.binding.variables.PIPELINE_LOG_LEVEL) : LogLevel.valueOf(context.env.getEnvironment().get("PIPELINE_LOG_LEVEL"))
    }

    public debug(String message){
        context.printf log(LogLevel.DEBUG, message)
    }

    public info(String message){
        context.printf log(LogLevel.INFO, message)
    }

    public warn(String message){
        context.printf log(LogLevel.WARN, message)
    }

    public error(String message){
        context.printf log(LogLevel.ERROR, message)
    }

    private def log(LogLevel logLevel, String message){
        def logMessage = ""
        if(logLevel.value >= pipelineLogLevel.value){
            logMessage = "${message}"
            if(contextType == ContextType.JOB_DSL){
                logMessage = logMessage +"\n"
            }
        }
        return logMessage
    }

}

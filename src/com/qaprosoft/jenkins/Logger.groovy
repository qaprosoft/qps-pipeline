package com.qaprosoft.jenkins

class Logger {

    public static enum LogLevel {

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
        this.contextType = context.binding.variables.get("QPS_PIPELINE_LOG_LEVEL") ? ContextType.JOB_DSL : ContextType.PIPELINE
        this.pipelineLogLevel = context.binding.variables.get("QPS_PIPELINE_LOG_LEVEL") ? LogLevel.valueOf(context.binding.variables.QPS_PIPELINE_LOG_LEVEL) : LogLevel.valueOf(context.env.getEnvironment().get("QPS_PIPELINE_LOG_LEVEL"))
    }

    public debug(message){
        context.println log(LogLevel.DEBUG, message)
    }

    public info(message){
        context.println log(LogLevel.INFO, message)
    }

    public warn(message){
        context.println log(LogLevel.WARN, message)
    }

    public error(message){
        context.println log(LogLevel.ERROR, message)
    }

    private def log(LogLevel logLevel, message){
        def logMessage = ""
        if (logLevel.value >= pipelineLogLevel.value){
            logMessage = "${message}"
            if (contextType == ContextType.JOB_DSL){
                logMessage = logMessage +"\n"
            }
        }
        return logMessage
    }

}

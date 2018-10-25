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

    def context
    LogLevel pipelineLogLevel
    String contextType

    Logger(context) {
        this.context = context
        this.pipelineLogLevel = context?.binding ? LogLevel.valueOf(context.binding.variables.PIPELINE_LOG_LEVEL) : LogLevel.valueOf(context.env.getEnvironment().get("PIPELINE_LOG_LEVEL"))
        this.contextType = context?.binding ? "jobDSL" : "pipeline"
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
            if(contextType == "jobDSL"){
                logMessage = logMessage +"\n"
            }
        }
        return logMessage
    }

}

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

    def context
    LogLevel pipelineLogLevel

    Logger(context) {
        this.context = context
        this.pipelineLogLevel = context.binding.variables.get("QPS_PIPELINE_LOG_LEVEL") ? LogLevel.valueOf(context.binding.variables.QPS_PIPELINE_LOG_LEVEL) : LogLevel.valueOf(context.env.getEnvironment().get("QPS_PIPELINE_LOG_LEVEL"))
    }

    public debug(message) {
        log(LogLevel.DEBUG, message)
    }

    public info(message) {
        log(LogLevel.INFO, message)
    }

    public warn(message) {
        log(LogLevel.WARN, message)
    }

    public error(message) {
        log(LogLevel.ERROR, message)
    }

    private def log(logLevel, message) {
        if (logLevel.value >= pipelineLogLevel.value) {
            context.println "[${logLevel}] ${message}"
        }
    }
}

package com.qaprosoft.jenkins

class Logger {
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

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

    public debug(message){
        log(LogLevel.DEBUG, message, ANSI_BLACK)
    }

    public info(message){
        log(LogLevel.INFO, message, ANSI_BLACK)
    }

    public warn(message){
        log(LogLevel.WARN, message, ANSI_YELLOW)
    }

    public error(message){
        log(LogLevel.ERROR, message, ANSI_RED)
    }

    private def log(logLevel, message, ansiCode){
        if (logLevel.value >= pipelineLogLevel.value){
            context.println "${ansiCode}[${logLevel}] ${message}${ANSI_RESET}"
        }
    }

}

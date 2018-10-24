package com.qaprosoft

class Utils {
    
    static def printStackTrace(Exception e) {
        def stringStacktrace = ""
        e.getStackTrace().each { traceLine ->
            stringStacktrace = stringStacktrace + "\tat " + traceLine + "\n"
        }
        return "${e.getClass().getName()}: ${e.getMessage()}\n" + stringStacktrace
    }

}

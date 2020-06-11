package com.qaprosoft.jenkins.pipeline.integration.reporting

class StatusMapper {

    enum TestRailStatus {
        PASSED(1),
        BLOCKED(2),
        UNTESTED(3),
        RETEST(4),
        FAILED(5),
        final int value
        TestRailStatus(int value) {
            this.value = value
        }
    }

    enum ReportingStatus {
        PASSED(1),
        FAILED(5),
        SKIPPED(3),
        ABORTED(3),
        QUEUED(3),
        IN_PROGRESS(3),

        final int value
        ReportingStatus(int value) {
            this.value = value
        }
    }

    static def getTestRailStatus(String reportingStringStatus){
        ReportingStatus reportingStatus = ReportingStatus.valueOf(reportingStringStatus)
        return reportingStatus.value
    }
}

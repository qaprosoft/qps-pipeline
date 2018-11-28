package com.qaprosoft.zafira

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

    enum QTestStatus {
        PASSED(1),
        FAILED(2),
        INCOMPLETE(3),
        final int value
        QTestStatus(int value) {
            this.value = value
        }
    }

    enum ZafiraStatus {
        PASSED(1, 1),
        FAILED(5, 2),
        SKIPPED(3, 3),
        ABORTED(3, 3),
        QUEUED(3, 3),
        final int testRail
        final int qTest
        ZafiraStatus(int testRail, int qTest) {
            this.testRail = testRail
            this.qTest = qTest
        }
    }

    static def getTestRailStatus(String zafiraStringStatus){
        ZafiraStatus zafiraStatus = ZafiraStatus.valueOf(zafiraStringStatus)
        for(testRailStatus in TestRailStatus.values()){
            if(testRailStatus.value == zafiraStatus.testRail) {
                return testRailStatus.value
            }
        }
    }

    static def getQTestStatus(String zafiraStringStatus){
        ZafiraStatus zafiraStatus = ZafiraStatus.valueOf(zafiraStringStatus)
        for(qTestStatus in QTestStatus.values()){
            if(qTestStatus.value == zafiraStatus.qTest) {
                return qTestStatus.value
            }
        }
    }

}

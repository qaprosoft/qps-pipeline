package com.qaprosoft.testrail

class TestRailStatusMapper {

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

    enum ZafiraStatus {
        PASSED(1),
        FAILED(5),
        SKIPPED(3),
        ABORTED(3),
        QUEUED(3),
        final int value
        ZafiraStatus(int value) {
            this.value = value
        }
    }

    public static def getTestRailStatus(String zafiraStringStatus){
        ZafiraStatus zafiraStatus = ZafiraStatus.valueOf(zafiraStringStatus)
        TestRailStatus.values().each { testRailStatus ->
            if(testRailStatus.value == zafiraStatus.value) {
                return testRailStatus.name()
            }
        }
    }
}

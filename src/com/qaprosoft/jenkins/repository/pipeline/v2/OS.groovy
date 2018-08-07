package com.qaprosoft.jenkins.repository.pipeline.v2

public enum OS {

    WINDOWS_10("Windows 10", "windows", '10'),
    WINDOWS_8_1("Windows 8.1", "windows", '8.1'),
    WINDOWS_8("Windows 8", "windows", '8'),
    WINDOWS_7("Windows 7", "windows", '7'),
    WINDOWS_XP("Windows XP", "windows", 'XP'),

    OS_X_HIGH_SIERRA("OS X High Sierra", "OS X", "High Sierra"),
    OS_X_SIERRA("OS X Sierra", "OS X", "Sierra"),
    OS_X_YOSEMITE("OS X Yosemite", "OS X", "El Capitan"),
    OS_X_MAVERICKS("OS X Mavericks", "OS X", "High Mavericks"),
    OS_X_MOUNTAIN_LION("OS X Mountain Lion", "OS X", "Mountain Lion"),
    OS_X_LION("OS X Lion", "OS X", "Lion"),
    OS_X_SNOW_LEOPARD("OS X Snow Leopard", "OS X", "Snow Leopard")

    private final String os
    private final String name
    private final String version

    OS(os, name, version) {
        this.os = os
        this.name = name
        this.version = version
    }

    public static String getName(String os) {
        values().each { value ->
            if (value.os == os) {
                return value.name
            }
        }
    }

    public static String getVersion(String os) {
        values().each { value ->
            if (value.os == os) {
                return value.version
            }
        }
    }
}
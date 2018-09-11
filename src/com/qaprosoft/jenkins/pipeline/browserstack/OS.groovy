package com.qaprosoft.jenkins.pipeline.browserstack

public enum OS {

    WINDOWS_10("Windows 10", "Windows", '10'),
    WINDOWS_8_1("Windows 8.1", "Windows", '8.1'),
    WINDOWS_8("Windows 8", "Windows", '8'),
    WINDOWS_7("Windows 7", "Windows", '7'),
    WINDOWS_XP("Windows XP", "Windows", 'XP'),

    OS_X_HIGH_SIERRA("OS X High Sierra", "OS X", "High Sierra"),
    OS_X_SIERRA("OS X Sierra", "OS X", "Sierra"),
    OS_X_EL_CAPITAN("OS X El Capitan", "OS X", "El Capitan"),
    OS_X_YOSEMITE("OS X Yosemite", "OS X", "Yosemite"),
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

    public static String getName(os) {
        String name = ''
        values().each { value ->
            if (value.os == os) {
                name = value.name
            }
        }
        return name
    }

    public static String getVersion(os) {
        String version = ''
        values().each { value ->
            if (value.os == os) {
                version = value.version
            }
        }
        return version
    }
}
package com.qaprosoft.selenium.grid

import groovy.json.JsonSlurper;

class ProxyInfo {

    def private static deviceList = []
    def private static baseDeviceList = ["DefaultPool", "ANY"]

	//TODO: reused grid/admin/ProxyInfo to get atual list of iOS/Android devices
	public static List<String> getDevicesList(String selenium, String platform) {

        //TODO: reuse selenium host/port/protocol from env jobVars
		def proxyInfoUrl = selenium + "/grid/admin/ProxyInfo"

		try {
            if (deviceList.size() == 0) {
                println "1"
                def json = new JsonSlurper().parse(proxyInfoUrl.toURL())
                json.each {
                    if (platform.equalsIgnoreCase(it.configuration.capabilities.platform)) {
                        println("platform: " + it.configuration.capabilities.platform[0] + "; device: " + it.configuration.capabilities.browserName[0])
                        deviceList.add(it.configuration.capabilities.browserName[0]);
                    }
                }
                println  "DEVICE LIST" + baseDeviceList + deviceList.sort()
            }
		} catch (Exception e) {
            println "2"
			//TODO: find a way to write message in static methods
			println(e.getMessage())
		}

        println "3"
		return baseDeviceList + deviceList.sort()
	}
}
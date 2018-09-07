package com.qaprosoft.selenium.grid

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper;

class ProxyInfo {

    private def dslFactory
    private String proxyInfoUrl
    private static def platformDeviceListMap = ["android":[], "ios":[]]
    private def baseDeviceList = ["DefaultPool", "ANY"]

    ProxyInfo(dslFactory) {
        this.dslFactory = dslFactory
        this.proxyInfoUrl = dslFactory.binding.variables.QPS_HUB + "/grid/admin/ProxyInfo"
    }

    //TODO: reused grid/admin/ProxyInfo to get atual list of iOS/Android devices
	public def getDevicesList(String platform) {

        dslFactory.println "MY MAP: " + platformDeviceListMap
        //TODO: reuse selenium host/port/protocol from env jobVars
        def deviceList = platformDeviceListMap.get(platform.toLowerCase())
        dslFactory.println "SIZE: " + deviceList.size()

		try {
            if (deviceList.size() == 0) {
                def json = new JsonSlurper().parse(proxyInfoUrl.toURL())
                String prettyJson = new JsonBuilder(json).toPrettyString()
                dslFactory.println "JSON: " + prettyJson
                json.each {
                    if (platform.equalsIgnoreCase(it.configuration.capabilities.platform)) {
//                        dslFactory.println "platform: " + it.configuration.capabilities.platform[0] + "; device: " + it.configuration.capabilities.browserName[0]
                        deviceList.add(it.configuration.capabilities.browserName[0])
                        platformDeviceListMap.put(platform.toLowerCase(), deviceList)
                    }
                }
            }
		} catch (Exception e) {
            dslFactory.println e.getMessage()
		}
		return baseDeviceList + deviceList.sort()
	}
}
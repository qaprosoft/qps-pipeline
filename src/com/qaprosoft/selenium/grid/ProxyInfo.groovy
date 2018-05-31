package com.qaprosoft.selenium.grid

class ProxyInfo {
	//TODO: reused grid/admin/ProxyInfo to get atual list of iOS/Android devices
	public static List<String> getDevicesList(String platform) {
		def baseDeviceList = ["DefaultPool", "ANY"]
		//context.println(baseDeviceList)
		
		def deviceList = []
		//TODO: reuse selenium host/port/protocol from env jobVars
		def json = new JsonSlurper().parse("http://smule.qaprosoft.com:14444/grid/admin/ProxyInfo".toURL())
		//context.println(json)
		json.each {
			if (platform.equalsIgnoreCase(it.configuration.capabilities.platform)) {
				context.println("platform: " + it.configuration.capabilities.platform[0] + "; device: " + it.configuration.capabilities.browserName[0])
				deviceList.add(it.configuration.capabilities.browserName[0]);
			}
		}

		return baseDeviceList + deviceList.sort()
	}
}
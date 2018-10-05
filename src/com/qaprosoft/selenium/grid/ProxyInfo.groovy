package com.qaprosoft.selenium.grid

@Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14')
import org.cyberneko.html.parsers.SAXParser
import groovy.util.XmlSlurper
import groovy.json.JsonSlurper

class ProxyInfo {

    private def dslFactory
    private String proxyInfoUrl
    private def platformDeviceListMap = ["android":[], "ios":[]]
    private def baseDeviceList = ["DefaultPool", "ANY"]

    ProxyInfo(dslFactory) {
        this.dslFactory = dslFactory
        this.proxyInfoUrl = dslFactory.binding.variables.QPS_HUB + "/grid/admin/ProxyInfo"
    }

    //TODO: reused grid/admin/ProxyInfo to get atual list of iOS/Android devices
    public def getDevicesList(String platform) {
        def deviceList = platformDeviceListMap.get(platform.toLowerCase())
        try {
            if (deviceList.size() == 0) {
                def json = new JsonSlurper().parse(proxyInfoUrl.toURL())
                json.each {
                    if (platform.equalsIgnoreCase(it.configuration.capabilities.platform)) {
                        dslFactory.println "platform: " + it.configuration.capabilities.platform[0] + "; device: " + it.configuration.capabilities.browserName[0]
                        deviceList.add(it.configuration.capabilities.browserName[0]);
                    }
                }
                platformDeviceListMap.put(platform.toLowerCase(), deviceList)
            }
        } catch (Exception e) {
            dslFactory.println e.getMessage()
        }
        return baseDeviceList + deviceList.sort()
    }

    public def getGridConsoleInfo(String platform) {
        String consoleUrl = dslFactory.binding.variables.QPS_HUB + "/grid/console"
        try {
            def parser = new SAXParser()
            def page = new XmlSlurper(parser).parse(consoleUrl)

            dslFactory.println "PAGE:\n${page.attributes()}"
        } catch (Exception e) {
            dslFactory.println e.getMessage()
        }
        //return baseDeviceList + deviceList.sort()
    }
}
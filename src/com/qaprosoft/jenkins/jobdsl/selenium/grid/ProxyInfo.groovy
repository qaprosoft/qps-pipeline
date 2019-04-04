package com.qaprosoft.jenkins.jobdsl.selenium.grid

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.Utils
import groovy.json.JsonSlurper

class ProxyInfo {

    private def _dslFactory
    private Logger logger
    private String proxyInfoUrl
    private def platformDeviceListMap = ["android":[], "ios":[]]
    private def baseDeviceList = ["DefaultPool", "ANY"]

    ProxyInfo(_dslFactory) {
        this._dslFactory = _dslFactory
        this.proxyInfoUrl = _dslFactory.binding.variables.QPS_HUB + "/grid/admin/ProxyInfo"
        logger = new Logger(_dslFactory)
    }

    //TODO: reused grid/admin/ProxyInfo to get atual list of iOS/Android devices
    public def getDevicesList(String platform) {
        def deviceList = platformDeviceListMap.get(platform.toLowerCase())
        try {
            if (deviceList.size() == 0) {
                def json = new JsonSlurper().parse(proxyInfoUrl.toURL())
                json.each {
                    if (platform.equalsIgnoreCase(it.configuration.capabilities.platform)) {
                        logger.debug("platform: " + it.configuration.capabilities.platform[0] + "; device: " + it.configuration.capabilities.browserName[0])
                        deviceList.add(it.configuration.capabilities.browserName[0]);
                    }
                }
                platformDeviceListMap.put(platform.toLowerCase(), deviceList)
            }
        } catch (Exception e) {
            logger.error(Utils.printStackTrace(e))
        }
        return baseDeviceList + deviceList.sort()
    }
}
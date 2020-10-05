package com.qaprosoft.jenkins.pipeline.integration.qtest

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.integration.zafira.IntegrationTag
import com.qaprosoft.jenkins.pipeline.integration.zafira.ZafiraClient
import com.qaprosoft.jenkins.pipeline.Configuration
import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

class QTestUpdater {

    // Make sure that in Automation Settings input statuses configured as PASSED, FAILED, SKIPPED!
    private def context
    private ZafiraClient zc
    private QTestClient qTestClient
    private Logger logger

    public QTestUpdater(context) {
        this.context = context
        zc = new ZafiraClient(context)
        qTestClient = new QTestClient(context)
        logger = new Logger(context)
    }

    public void updateTestRun(uuid) {
        if (!qTestClient.isAvailable()) {
            // do nothing
            return
        }

        def zafiraIntegrationData = exportIntegrationDataFromZafira(uuid)
        def parsedIntegrationData = parseIntegrationParams(zafiraIntegrationData)

        /* Values from zafira integration data */
        def projectId = parsedIntegrationData.projectId
        def cycleName = parsedIntegrationData.customParams.cycle_name
        def startedAt = parsedIntegrationData.startedAt
        def finishedAt = parsedIntegrationData.finishedAt
        def env = parsedIntegrationData.env
        def testCasesMap = parsedIntegrationData.testCasesMap

        // Get id of test cycle, passed from zafira
        def rootTestCycleId = getCycleIdByName(projectId, cycleName)

        if (projectId.toInteger() == 10) {
            rootTestCycleId = getSubCycleId(rootTestCycleId, projectId)
        }
        /* Upload subhierarchy of test cycles stored under parent cycle */
        def testRunsSubHierarchy = qTestClient.getTestRunsSubHierarchy(projectId)
        /* Create map to store already extracted from zafira module hierarchies */
        Map<Object, Map> testModuleHierarchiesMap = new HashMap()
        testCasesMap.values().each { zafiraTestCase ->
            def testCaseId = zafiraTestCase.case_id
            /* upload QTest testCase object */
            def qTestTestCase = qTestClient.getTestCase(projectId, testCaseId)
            if (isParamEmpty(qTestTestCase)) {
                logger.error("Unable to get QTest testCase.")
                return
            }
            logger.info("TEST_CASE:\n" + qTestTestCase.dump())
            def parentModuleId = qTestTestCase.parent_id
            /* check by parentId whether parent package structure was already uploaded from QTest,
               previously uploaded structures are stored in map, where key - id of lowest folder in hierarchy */
            Map testModulesSubHierarchy = testModuleHierarchiesMap.get(parentModuleId)
            logger.info("EXISTING_HIERARCHY:\n" + testModulesSubHierarchy)
            /* if no such value in the map, uploading it via http calls to QTest */
            if (testModulesSubHierarchy == null) {
                testModulesSubHierarchy = getParentModulesMap(parentModuleId, projectId)
            }
            if (testModulesSubHierarchy.size() > 0) {
                /* Use lowest in hierarchy folder as a key */
                def firstMapEntry = testModulesSubHierarchy.entrySet().iterator().next()
                logger.info("FIRST_MAP_ENTRY:\n" + firstMapEntry)
                /* put new hierarchy only if there is no such in the map */
                testModuleHierarchiesMap.putIfAbsent(firstMapEntry.getKey(), testModulesSubHierarchy)
            }
            /* Get or create lowest in hierarchy testCycle to write results in there */
            def currentTestCycleId = getCurrentTestCycleId(testRunsSubHierarchy, testModulesSubHierarchy, rootTestCycleId, projectId)
            def suite = getOrAddTestSuite(projectId, currentTestCycleId, env)
            def suiteId = suite.id
            def testRun = getOrAddTestRun(projectId, suiteId, testCaseId, qTestTestCase.name)
            def results = uploadTestRunResults(zafiraTestCase, startedAt, finishedAt, testRun, projectId)
            logger.debug("UPLOADED_RESULTS: " + formatJson(results))
        }
    }

    private def getParentModulesMap(parentModuleId, projectId) {
        Map parentModules = new LinkedHashMap()
        while (!isParamEmpty(parentModuleId)) {
            def parentModuleObject = qTestClient.getModule(projectId, parentModuleId)
            if (!isParamEmpty(parentModuleObject)) {
                parentModules.put(parentModuleObject.id, parentModuleObject.name)
                parentModuleId = parentModuleObject.parent_id
            } else {
                parentModuleId = null
            }
        }
        return parentModules
    }

    private def getCurrentTestCycleId(testRunsSubHierarchy, testModulesSubHierarchy, rootTestCycleId, projectId) {
        logger.info("TEST_RUNS_SUBHIERARCHY:\n" + testRunsSubHierarchy)
        logger.info("TEST_RUNS_SUBHIERARCHY_CHILDREN:\n" + testRunsSubHierarchy.children)
        logger.info("TEST_MODULES_SUBHIERARCHY:\n" + testModulesSubHierarchy)
        // Get upper level of test cycles from test runs hierarchy
        List presentInSubHierarchyTestCycles = testRunsSubHierarchy.children
        // Set root hierarchy folder to start search from
        def currentTestCycleId = rootTestCycleId
        def presentTestCycle
        testModulesSubHierarchy.reverseEach { key, val ->
            // Search for upper-level test cycle folder among already present
            presentTestCycle = presentInSubHierarchyTestCycles.find {
                it.name.equals(val)
            }
            // If corresponding to module cycle wasn't found, create one
            if (presentTestCycle == null) {
                def createdTestCycle = qTestClient.addTestCycle(projectId, currentTestCycleId, val)
                currentTestCycleId = createdTestCycle.id
                // Set null to disable further search, all test cycles inside newly created will be created too
                presentInSubHierarchyTestCycles = null
            } else {
                presentInSubHierarchyTestCycles = presentTestCycle.children
                currentTestCycleId = presentTestCycle.id
            }
        }
        return currentTestCycleId
    }

    private Object uploadTestRunResults(testCase, startedAt, finishedAt, testRun, projectId) {
        def testLogsNote = testCase.testURL + "\n\n" + testCase.comment
        def results = qTestClient.uploadResults(testCase.status, new Date(startedAt), new Date(finishedAt), testRun.id, testRun.name, projectId, testLogsNote)
        if (isParamEmpty(results)) {
            throw new RuntimeException("Unable to add results for QTest TestRun.")
        }
        return results
    }

    private Object getOrAddTestRun(projectId, suiteId, testCaseId, testRunName) {
        def testRun
        testRun = getTestRun(projectId, suiteId, testCaseId, testRunName)
        if (isParamEmpty(testRun)) {
            logger.error("Unable to get QTest testRun.")
            logger.info("Adding new QTest testRun...")
            testRun = qTestClient.addTestRun(projectId, suiteId, testCaseId, testRunName)
            if (isParamEmpty(testRun)) {
                throw new RuntimeException("Unable to add QTest testRun.")
            }
        }
        testRun
    }

    private def getOrAddTestSuite(projectId, cycleId, env) {
        def suite = getTestSuite(projectId, cycleId, env)
        if (isParamEmpty(suite)) {
            logger.error("Unable to get QTest testSuite.")
            logger.info("Adding new QTest testSuite...")
            suite = qTestClient.addTestSuite(projectId, cycleId, env)
            if (isParamEmpty(suite)) {
                throw new RuntimeException("Unable to register QTest testSuite.")
            }
        }
        return suite
    }

    /**
     * Exports data necessary for intregration from Zafira by
     * @param uuid.
     * Data is based on QTEST_TESTCASE_UUID tag values
     * and 'qtest_cycle_name', 'qtest_suite_name' custom arguments
     * from testRun config XML.
     * @return
     */
    private Object exportIntegrationDataFromZafira(uuid) {
        def integration = zc.exportTagData(uuid, IntegrationTag.QTEST_TESTCASE_UUID)
        if (isParamEmpty(integration)) {
            throw new RuntimeException("Integration object is empty, nothing to update in QTest.")
        }
        logger.debug("INTEGRATION_INFO:\n" + formatJson(integration))
        return integration
    }

    protected def getCycleIdByName(projectId, cycleName) {
        def cycles = qTestClient.getCycles(projectId)
        def cycle = cycles.find {
            it.name.equals(cycleName)
        }
        if (isParamEmpty(cycle)) {
            throw new RuntimeException("No QTest cycle with name " + cycleName + " detected in project " + projectId)
        }
        return cycle.id
    }

    /**
     * Gets subCycle if os and os_version values are provided in TestRun configuration.
     * If subCycle is not found, a new one will be created.
     * @param cycleId
     * @param projectId
     * @return
     */
    protected def getSubCycleId(cycleId, projectId) {
        def subCycleId = cycleId
        def os = Configuration.get("os")
        def os_version = Configuration.get("os_version")
        def browser = getBrowser()
        if (!isParamEmpty(os) && !isParamEmpty(os_version)) {
            def subCycleName = os + "-" + os_version + "-" + browser
            def subCycle = getOrAddSubCycle(cycleId, projectId, subCycleName)
            subCycleId = subCycle.id
        }
        return subCycleId
    }


    private def getOrAddSubCycle(cycleId, projectId, String subCycleName) {
        def subCycle = getSubCycleByName(cycleId, projectId, subCycleName)
        if (isParamEmpty(subCycle)) {
            logger.error("Unable to get QTest subCycle.")
            logger.info("Adding new QTest subCycle...")
            subCycle = addTestCycle(projectId, cycleId, subCycleName)
        }
        return subCycle
    }

    private def addTestCycle(projectId, cycleId, String subCycleName) {
        def newSubCycle = qTestClient.addTestCycle(projectId, cycleId, subCycleName)
        if (isParamEmpty(newSubCycle)) {
            throw new RuntimeException("Unable to add new cycle.")
        }
        return newSubCycle
    }

    private def getSubCycleByName(cycleId, projectId, subCycleName) {
        def subCycles = qTestClient.getSubCycles(cycleId, projectId)
        return subCycles.find {
            it.name.equals(subCycleName)
        }
    }

    protected def getTestSuite(projectId, cycleId, platform) {
        def suites = qTestClient.getTestSuites(projectId, cycleId)
        return suites.find {
            it.name.equals(platform)
        }
    }

    protected def getTestRun(projectId, suiteId, caseId, testRunName) {
        def runs = qTestClient.getTestRuns(projectId, suiteId)
        for (run in runs) {
            if (run.name.equals(testRunName) && run.test_case.id == Integer.valueOf(caseId)) {
                return run
            }
        }
    }

    protected def getTestCaseName(projectId, caseId) {
        def testCaseName = null
        def testCase = qTestClient.getTestCase(projectId, caseId)
        if (isParamEmpty(testCase)) {
            logger.error("Unable to get QTest testCase.")
        } else {
            testCaseName = testCase.name
        }
        return testCaseName
    }

    /**
     * Parses QTEST_TESTCASE_UUID tags ant creates map of testCases with case_id, status and testURL.
     * Also sets projectId in parsedIntegrationInfo object
     * @param integration
     * @return
     */
    private def parseIntegrationParams(integration) {
        def parsedIntegrationInfo = integration
        Map testCasesMap = new HashMap<>()
        integration.testInfo.each { testInfo ->
            String[] tagInfoArray = testInfo.tagValue.split("-")
            def projectId = tagInfoArray[0]
            if (isParamEmpty(projectId)) {
                throw new RuntimeException("Unable to detect QTest project_id!\n" + formatJson(parsedIntegrationData))
            }
            // projectId is set only once, otherwise this action is skipped
            if (isParamEmpty(parsedIntegrationInfo.projectId)) {
                parsedIntegrationInfo.projectId = projectId
            }
            def testCaseId = tagInfoArray[1]
            // check to avoid action duplication if testCase has already been added in map
            if (isParamEmpty(testCasesMap.get(testCaseId))) {
                HashMap testCase = createTestCaseObject(testCaseId, testInfo, integration)
                testCasesMap.put(testCaseId, testCase)
            }
        }
        parsedIntegrationInfo.testCasesMap = testCasesMap
        parsedIntegrationInfo.testInfo = null
        return parsedIntegrationInfo
    }

    /**
     * Creates testCase based on testInfo data
     * @param testCaseId
     * @param testInfo
     * @param integration
     * @return
     */
    private HashMap createTestCaseObject(String testCaseId, testInfo, integration) {
        Map testCase = new HashMap()
        testCase.case_id = testCaseId.trim()
        testCase.status = testInfo.status
        testCase.testURL = "${integration.zafiraServiceUrl}/test-runs/${integration.testRunId}/tests/${testInfo.id}"
        return testCase
    }
}

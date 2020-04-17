package com.qaprosoft.jenkins.pipeline.runner.maven

import com.qaprosoft.jenkins.pipeline.Configuration
import com.qaprosoft.jenkins.pipeline.runner.AbstractRunner
import groovy.json.JsonOutput
import com.qaprosoft.jenkins.Logger

@Grab('org.testng:testng:6.8.8')

public class Updater extends AbstractRunner{
    protected Map dslObjects = new HashMap()
    protected Logger logger
    protected def context
    protected Configuration configuration = new Configuration(context)

    public Updater(context) {
        logger = new Logger(context)
    }

    protected qTestCreateJob() {
        context.writeFile file: "factories.json", text: JsonOutput.toJson(dslObjects)
        logger.info("factoryTarget: " + FACTORY_TARGET)
        //TODO: test carefully auto-removal for jobs/views and configs
        context.jobDsl additionalClasspath: additionalClasspath,
                removedConfigFilesAction: Configuration.get("removedConfigFilesAction"),
                removedJobAction: Configuration.get("removedJobAction"),
                removedViewAction: Configuration.get("removedViewAction"),
                targets: FACTORY_TARGET,
                ignoreExisting: false
    }
}

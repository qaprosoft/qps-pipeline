package com.qaprosoft.jenkins.repository.pipeline.v2

@Grab('org.testng:testng:6.8.8')
import org.testng.xml.Parser;
import org.testng.xml.XmlSuite;
import com.cloudbees.groovy.cps.NonCPS

import com.qaprosoft.scm.ISCM
import sun.awt.CausedFocusEvent

import java.nio.file.FileSystems
import java.nio.file.PathMatcher

public abstract class Executor {
	//pipeline context to provide access to existing pipeline methods like echo, sh etc...
	protected def context

	//list of job parameters as a map

	protected ISCM scmClient

	protected Configurator configurator = new Configurator(context)


	public Executor(context) {
		this.context = context
	}
	
	protected clean() {
		context.stage('Wipe out Workspace') {
			context.deleteDir()
		}
	}
	
	protected void printStackTrace(Exception ex) {
		context.println("exception: " + ex.getMessage())
		context.println("exception class: " + ex.getClass().getName())
		context.println("stacktrace: " + Arrays.toString(ex.getStackTrace()))
	}

	protected String getWorkspace() {
		return context.pwd()
	}
	
	protected String getBuildUser() {
		try {
			return context.currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()
		} catch (Exception e) {
			return ""
		}
	}

	protected Object parseJSON(String path) {
		def inputFile = new File(path)
		def content = new groovy.json.JsonSlurperClassic().parseFile(inputFile, 'UTF-8')
		return content
	}

    /** Detects if any changes are present in files matching pattern  */
    @NonCPS
    protected boolean isUpdated(String patterns) {
        //def patternArray = patterns.split(",")
        boolean changedFilesFound = false
        def changeLogSets = context.currentBuild.rawBuild.changeSets
        for (changeLogSet in changeLogSets) {
            for (entry in changeLogSet.getItems()) {
                for (path in entry.getPaths()) {
                    def fileSystem = path.getFileSystem()
                    context.println("FILESYSTEM" + fileSystem.dump())

//                    if (matchPath(patterns))
//                        changedFilesFound = true
//                    break
                }
            }
        }
        return changedFilesFound
    }

//    protected boolean matchPath(String paths) {
//        boolean isMatch = false
//
//
//        FileSystem fs = FileSystems.getDefault();
//        PathMatcher matcher = fs.getPathMatcher("glob:" + glob)
//        if (matcher.matches(paths)) {
//            context.println("PATH: " + paths)
//            isMatch = true
//        }
//        return isMatch
//    }

    /** Checks if current job started as rebuild */
    protected Boolean isRebuild(String jobName) {
        Boolean isRebuild = false
        /* Gets CauseActions of the job */
        context.currentBuild.rawBuild.getActions(hudson.model.CauseAction.class).each {
            action ->
                /* Search UpstreamCause among CauseActions */
                if (action.findCause(hudson.model.Cause.UpstreamCause.class) != null)
                /* If UpstreamCause exists and has the same name as current job, rebuild was called */
                    isRebuild = (jobName == action.findCause(hudson.model.Cause.UpstreamCause.class).getUpstreamProject())
        }
        return isRebuild
    }

    /** Determines BuildCause */
    protected String getBuildCause(String jobName) {
        String buildCause = null
        /* Gets CauseActions of the job */
        context.currentBuild.rawBuild.getActions(hudson.model.CauseAction.class).each {
            action ->
                /* Searches UpstreamCause among CauseActions and checks if it is not the same job as current(the other way it was rebuild) */
                if (action.findCause(hudson.model.Cause.UpstreamCause.class)
                        && (jobName != action.findCause(hudson.model.Cause.UpstreamCause.class).getUpstreamProject())) {
                    buildCause = "UPSTREAMTRIGGER"
                }
                /* Searches TimerTriggerCause among CauseActions */
                else if (action.findCause(hudson.triggers.TimerTrigger$TimerTriggerCause.class)) {
                    buildCause = "TIMERTRIGGER"
                }
                else {
                    buildCause = "MANUALTRIGGER"
                }
        }
        return buildCause
    }

	XmlSuite parseSuite(String path) {
		def xmlFile = new Parser(path)
		xmlFile.setLoadClasses(false)
		
		List<XmlSuite> suiteXml = xmlFile.parseToList()
		XmlSuite currentSuite = suiteXml.get(0)
		return currentSuite
	}

}
package com.qaprosoft.jenkins.pipeline.runner

import com.qaprosoft.jenkins.BaseObject
import java.nio.file.Paths

import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

public abstract class AbstractRunner extends BaseObject {
	// organization folder name of the current job/runner
	protected String organization = ""
	
    public AbstractRunner(context) {
		super(context)
		initOrganization()
    }

    //Methods
    abstract public void build()

    //Events
    abstract public void onPush()
    abstract public void onPullRequest()

    protected void jenkinsFileScan() {
		if (!context.fileExists('Jenkinsfile')) {
			// do nothing
			return
		}
		
        context.stage('Jenkinsfile Stage') {
            context.script { 
                context.jobDsl targets: 'Jenkinsfile'
            }
        }
    }
	
	protected def getOrgFolder() {
		return this.organization
	}	
	
	@NonCPS
	protected void initOrganization() {
		String jobName = context.env.getEnvironment().get("JOB_NAME") //Configuration.get(Configuration.Parameter.JOB_NAME)
		int nameCount = Paths.get(jobName).getNameCount()

		def orgFolderName = ""
		if (nameCount == 1 && (jobName.contains("qtest-updater") || jobName.contains("testrail-updater"))) {
			// testrail-updater - i.e. stage
			orgFolderName = ""
		} else if (nameCount == 2 && (jobName.contains("qtest-updater") || jobName.contains("testrail-updater"))) {
			// stage/testrail-updater - i.e. stage
			orgFolderName = Paths.get(jobName).getName(0).toString()
		} else if (nameCount == 2) {
			// carina-demo/API_Demo_Test - i.e. empty orgFolderName
			orgFolderName = ""
		} else if (nameCount == 3) { //TODO: need to test use-case with views!
			// qaprosoft/carina-demo/API_Demo_Test - i.e. orgFolderName=qaprosoft
			orgFolderName = Paths.get(jobName).getName(0).toString()
		} else {
			throw new RuntimeException("Invalid job organization structure: '${jobName}'!" )
		}
		
		this.organization = orgFolderName
	}
	
	protected def getToken(tokenName) {
		def tokenValue = ""

		if (!isParamEmpty(this.organization)) {
			tokenName = "${this.organization}" + "-" + tokenName
		}
		
		if (getCredentials(tokenName)){
			context.withCredentials([context.usernamePassword(credentialsId:tokenName, usernameVariable:'KEY', passwordVariable:'VALUE')]) {
				tokenValue=context.env.VALUE
			}
		}
		logger.debug("tokenName: ${tokenName}; tokenValue: ${tokenValue}")
		return tokenValue
	}


}

package com.qaprosoft.jenkins.pipeline.runner

import com.qaprosoft.jenkins.BaseObject

public abstract class AbstractRunner extends BaseObject {
	private static final String organization = getOrganization()
	
    public AbstractRunner(context) {
		super(context)
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
	
	
	@NonCPS
	private void getOrganization() {
		String jobName = Configuration.get(Configuration.Parameter.JOB_NAME)
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
		
		Configuration.set(Configuration.Parameter.ORG_FOLDER, orgFolderName)
		
	}

}

package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.pipeline.Scanner
import com.qaprosoft.scm.github.GitHub;

class Repository extends Executor {

	public Repository(context) {
		super(context)
		this.context = context
        scmClient = new GitHub(context)
		
	}

	public void create() {
		context.println("Repository->create")
		// Create folder/views/jobs based on repo content

		// 1. Create pr_checker/merger job for concrete repo
		// 2. launch scanner for each merge/push operation

		Scanner scanner = new Scanner(context, false, "DELETE", "DELETE", "DELETE");
		scanner.scanRepository() //uncheck onlyUpdated here for execution
	}

	public void trigger() {
		context.println("Repository->trigger")
		
		String build_cause = getBuildCause(Configurator.get(Configurator.Parameter.JOB_NAME))
		context.println("build_cause: " + build_cause)

		switch (build_cause) {
			case "SCMPUSHTRIGGER":
			case "MANUALTRIGGER":
				onUpdate()
                break
            case "SCMGHPRBTRIGGER":
                onPullRequest()
                break
            default: 
				throw new RuntimeException("Unrecognized build cause")
				break
		}
		
	}

	//Events
	protected void onUpdate() {
		context.println("Repository->onUpdate")
		// handle each push/merge operation
		// execute logic inside this method only if $REPO_HOME/Jenkinsfile was updated
		Scanner scanner = new Scanner(context);
		scanner.scanRepository()
	}

	protected void onPullRequest() {
		context.println("Repository->onPullRequest")
		verify()
	}

	protected void verify() {
        context.node("master") {
            context.stage("Repository->verify") {
                scmClient.clonePR()
                def goals = "clean compile test-compile \
                     -f pom.xml -Dmaven.test.failure.ignore=true \
                     -Dcom.qaprosoft.carina-core.version=${ Configurator.get(Configurator.Parameter.CARINA_CORE_VERSION)}"

                if (context.isUnix()) {
                    context.sh "'mvn' -B ${goals}"
                } else {
                    context.bat "mvn -B ${goals}"
                }
            }
        }
    }
	
	protected void compile() {
		context.println("Repository->compile")
	}

	protected void deploy() {
		context.println("Repository->deploy")
	}

	protected void test() {
		context.println("Repository->test")
	}



}
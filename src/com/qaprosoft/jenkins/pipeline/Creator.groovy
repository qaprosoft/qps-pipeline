package com.qaprosoft.jenkins.pipeline

import com.qaprosoft.jenkins.pipeline.Scanner
import com.qaprosoft.scm.github.GitHub;

class Creator extends Executor {

	protected Scanner scanner

	public Creator(context) {
		super(context)
		this.context = context

		scmClient = new GitHub(context)
		scanner = new Scanner(context)
	}

	public void create() {
		context.println("Creator->create")

		//create only high level management jobs. for now it is only _trigger_<project-name>
		scanner.createRepository()

		// execute new _trigger-<project> to regenerate other views/jobs/etc
		def project = Configurator.get("project")
		def newJob = project + "/" + "_trigger-" + project

		context.build job: newJob,
		propagate: false,
		parameters: [
			context.string(name: 'branch', value: Configurator.get("branch")),
			context.string(name: 'project', value: project),
			context.booleanParam(name: 'onlyUpdated', value: false),
			context.string(name: 'removedConfigFilesAction', value: 'DELETE'),
			context.string(name: 'removedJobAction', value: 'DELETE'),
			context.string(name: 'removedViewAction', value: 'DELETE'),
		]
	}

	public void trigger() {
		context.println("Creator->trigger")

		String build_cause = getBuildCause(Configurator.get(Configurator.Parameter.JOB_NAME))
		context.println("build_cause: " + build_cause)

		switch (build_cause) {
			case "SCMPUSHTRIGGER":
			case "MANUALTRIGGER":
			case "UPSTREAMTRIGGER":
			case "TIMERTRIGGER":
				onUpdate()
				break
			case "SCMGHPRBTRIGGER":
				onPullRequest()
				break
			default:
				throw new RuntimeException("Unrecognized build cause: " + build_cause)
				break
		}
	}

	//Events
	protected void onUpdate() {
		context.println("Creator->onUpdate")
		// handle each push/merge operation
		// execute logic inside this method only if $REPO_HOME/Jenkinsfile was updated
		scanner.updateRepository()
	}

	protected void onPullRequest() {
		context.println("Creator->onPullRequest")
		context.node("master") {
			scmClient.clonePR()
			def goals = "clean compile test-compile \
                     -f pom.xml -Dmaven.test.failure.ignore=true \
                     -Dcom.qaprosoft.carina-core.version=${Configurator.get(Configurator.Parameter.CARINA_CORE_VERSION)}"

			if (context.isUnix()) {
				context.sh "'mvn' -B ${goals}"
			} else {
				context.bat "mvn -B ${goals}"
			}
		}
	}


	protected void compile() {
		context.println("Creator->compile")
	}

	protected void deploy() {
		context.println("Creator->deploy")
	}

	protected void test() {
		context.println("Creator->test")
	}



}
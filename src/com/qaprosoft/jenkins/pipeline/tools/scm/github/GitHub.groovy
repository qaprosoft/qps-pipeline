package com.qaprosoft.jenkins.pipeline.tools.scm.github

import com.qaprosoft.jenkins.pipeline.tools.scm.Scm

class GitHub extends Scm {

    GitHub(context, host, org, repo, branch) {
        super(context, host, org, repo, branch)
        this.prRefSpec = '+refs/pull/*:refs/remotes/origin/pr/*'
        this.branchSpec = "origin/pr/%s/merge"
    }

    GitHub(context) {
        super(context)
    }

    enum HookArgs {
        // global
        HEADER_EVENT_NAME("eventName", "x-github-event"),

        // pr
        PR_NUMBER("prNumber", "\$.number"),
        PR_REPO("prRepo", "\$.pull_request.base.repo.name"),
        PR_SOURCE_BRANCH("prSourceBranch", "\$.pull_request.head.ref"),
        PR_TARGET_BRANCH("prTargetBranch", "\$.pull_request.base.ref"),
        PR_SHA("prSha", "\$.pull_request.head.sha"),
        PR_ACTION("prAction", "\$.action"),
        PR_FILTER_REGEX("prFilterExpression", "^(opened|reopened)\\spull_request)*?\$"),
        PR_FILTER_TEXT("prFilterText", "\$pr_action x_github_event"),

        // push
        REF_JSON_PATH("refJsonPath", "\$.ref"),
        PUSH_FILTER_TEXT("pushFilterText", "\$ref x_github_event"),
        PUSH_FILTER_REGEX("pushFilterRegex", "^(refs/heads/master\\spush)*?\$")


        private final String key
        private final String value

        HookArgs(String key, String value) {
            this.key = key
            this.value = value
        }

        public String getKey() { return key }

        public String getValue() { return value }
    }

    @Override
    protected String getBranchSpec(spec) {
        return String.format(branchSpec, spec)
    }

}

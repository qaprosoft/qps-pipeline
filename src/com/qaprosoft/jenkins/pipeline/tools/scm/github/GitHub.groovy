package com.qaprosoft.jenkins.pipeline.tools.scm.github

import com.qaprosoft.jenkins.pipeline.tools.scm.Scm

class GitHub extends Scm {

    GitHub(context, host, org, repo, oauthToken) {
        super(context, host, org, repo, oauthToken)
        this.prRefSpec = '+refs/pull/*:refs/remotes/origin/pr/*'
        this.branchSpec = "origin/pr/%s/merge"
    }

    enum HookArgs {
        // pr
        HEADER_EVENT_NAME("eventName", "x-github-event"),
        PR_NUMBER("prNumber", "\$.number"),
        PR_REPO("prRepo", "\$.pull_request.base.repo.name"),
        PR_SOURCE_BRANCH("prSourceBranch", "\$.pull_request.head.ref"),
        PR_TARGET_BRANCH("prTargetBranch", "\$.pull_request.base.ref"),
        PR_SHA("prSha", "\$.pull_request.head.sha"),
        PR_ACTION("prAction", "\$.action"),
        FILTER_REGEX("filterExpression", "^(opened|reopened)\\s(Merge\\sRequest\\sHook|pull_request)*?\$"),
        FILTER_TEXT("filterText", "\$pr_action x_github_event"),

        // push
        REF_JSON_PATH("refJsonPath", "\$.ref")

        private final String key
        private final String value

        HookArgs(String key, String value) {
            this.key = key
            this.value = value
        }

        public String getKey() { return key }

        public String getValue() {return value }
    }

    def getHookArgsAsMap() {
        return HookArgs.values().collectEntries { [(it.getKey(): it.getValue())] }
    }

    @Override
    protected String getHtmlUrl() {
        return String.format("https://%s/%s/%s", host, org, repo)
    }

    @Override
    protected String getBranchSpec(prNumber) {
        return String.format(branchSpec, prNumber)
    }

}

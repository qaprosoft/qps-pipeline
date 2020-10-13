package com.qaprosoft.jenkins.pipeline.tools.scm.bitbucket

import com.qaprosoft.jenkins.pipeline.tools.scm.Scm

class BitBucket extends Scm {

    BitBucket(context, host, org, repo, branch) {
        super(context, host, org, repo, branch)
        this.prRefSpec = '+refs/pull/*:refs/remotes/origin/pr/*'
        this.branchSpec = branch
    }

    BitBucket(context) {
        super(context)
    }

    enum HookArgs {
        // global
        HEADER_EVENT_NAME("eventName", "x-event-key"),

        // pr
        PR_NUMBER("prNumber", "\$.pullrequest.id"),
        PR_REPO("prRepo", "\$.pullrequest.destination.repository.name"),
        PR_SOURCE_BRANCH("prSourceBranch", "\$.pullrequest.source.branch.name"),
        PR_TARGET_BRANCH("prTargetBranch", "\$.pullrequest.destination.branch.name"),
        PR_FILTER_REGEX("filterExpression", "^(pullrequest:(created|updated))*?\$"),
        PR_FILTER_TEXT("filterText", "x_gitlab_event"),
        PR_ACTION("prAction", ""),
        PR_SHA("prSha", ""),

        // push
        REF_JSON_PATH("refJsonPath", "\$.push.changes[0].new.name"),
        PUSH_FILTER_TEXT("pushFilterText", "\$ref x-event-key"),
        PUSH_FILTER_REGEX("pushFilterRegex", "^(master\\srepo:push)*?\$")

        private final String key
        private final String value

        HookArgs(String key, String value) {
            this.key = key
            this.value = value
        }

        public String getKey() { return key }

        public String getValue() {return value }
    }

    @Override
    protected String getBranchSpec(spec) {
        return branchSpec
    }
}

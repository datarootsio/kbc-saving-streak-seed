# AI pull request reviews

[PR-Agent](https://github.com/The-PR-Agent/pr-agent) runs in GitHub Actions and uses OpenRouter.
When a non-draft pull request from a branch in this repository is opened, reopened, marked ready,
or updated, it posts a summary and reviews the changes. Its summary is a comment; it preserves
the author's PR description. It does not approve or merge pull requests.

## Setup

The repository needs an Actions secret named `OPENROUTER_API_KEY`. Add it under
**Settings → Secrets and variables → Actions**. GitHub supplies `GITHUB_TOKEN` automatically.
Copies and forks of this seed need their own secret; secrets are not copied with the source.

The default review model is `openrouter/anthropic/claude-sonnet-4.6`, with
`openrouter/google/gemini-2.5-flash` as a fallback. Set the optional Actions variable
`PR_AGENT_MODEL` to another PR-Agent model ID beginning with `openrouter/` to change the primary
model. Model calls are billed to the OpenRouter key. The selected model must support the configured
context and output limits in `.pr_agent.toml`.

The workflow is `.github/workflows/pr-agent.yml`; review preferences live in `.pr_agent.toml`.
The PR-Agent 0.46.0 GitHub Action image is pinned by digest. When upgrading, verify the release's
`<version>-github_action` image digest and update the workflow together with its version comment.

## Five-minute workshop demo

1. Open a small, non-draft PR from a branch in this repository.
2. Show **Actions → AI pull request review**, then return to the PR when it finishes.
3. Walk through the generated summary and one review finding beside the code.
4. Post `/ask "What scenario would trigger the main issue you found?"` as a PR conversation comment.
5. Push a fix and show the subsequent automatic review.

Repository owners, organization members and collaborators can also post these commands in a PR's
conversation:

- `/review` — review the current changes again.
- `/describe` — refresh the summary comment.
- `/improve` — suggest specific code improvements.
- `/ask "your question"` — ask about the PR.

Ordinary comments, bot comments and unrecognized commands do not invoke the model.
Draft PRs and PRs from forks do not receive automatic reviews. A trusted collaborator can request
a review of a fork PR using a command in the base repository's PR conversation.

## What the check means

The Actions result reports whether the reviewer ran successfully. A green result does not mean
that the code is correct or that all findings are resolved. Keep deterministic tests and human
review alongside this feedback.

The workflow uses `pull_request_target` to load its definition from the base branch. It never
checks out or executes PR code; PR-Agent reads changes through the GitHub API. Its token can read
repository contents and write PR/issue feedback, but cannot push code. Relevant diff and context
are sent to OpenRouter and the selected model provider for review.

## Troubleshooting

- **Missing key:** the preflight step reports that `OPENROUTER_API_KEY` must be configured.
- **Provider error:** check the key's credit, model access and the Actions logs. A fallback is
  configured, but both models require a valid OpenRouter key.
- **No response to a command:** use a new comment in the PR conversation, start it with one of the
  supported slash commands, and check that the author has one of the permitted associations.
- **No automatic run:** check the PR is not a draft, its branch belongs to this repository, and
  the workflow has been merged into the default branch.

# Saving Streak — exercises

Start with the working base app: deposits earn points immediately, and catalogue rewards are redeemed instantly and finally. No extensions exist yet. Follow the [business rules](saving-streak-app-requirements.md).

Take the **loyalty-rate bonus** through Module 2.

From 2.2 onward, write your own prompt using the bullet guidance.

**Git workflow:** Keep the feature on `feature/loyalty-bonus`. Commit the spec and ticket files there so each worktree receives them. Each ticket gets its own branch (for example, `ticket/01-loyalty-bonus`) and a separate Git worktree, created from the current feature branch once its blockers have merged. Implement, review and document the ticket in that worktree. When checks pass and blocking review findings are resolved, merge its branch into the feature branch and verify the integrated result.

## Module 2 — Delivery loop

### 2.1 Shared context — deployment knowledge

**Time:** 5 minutes.

How would you let new coding sessions know how the app gets deployed?

1. Decide where the deployment instructions should live and how a fresh session would find them.
2. Add the guidance or a reference to the existing deployment documentation.
3. Start a fresh session and ask it to explain how the app gets deployed, without deploying it.

**Done when:** The fresh session can find and explain the deployment process without you repeating it in the prompt.

### 2.2 Intentionality and ambiguity

**The feature:** Add a loyalty bonus. Customers earn 10% extra points when a deposit stays in savings for 12 months. For example, a €100 deposit earns 100 points immediately and another 10 points after 12 months.

**Your task:** Get the agent to help you clarify how this feature should work before it starts coding.

Write your own prompt. Cover:

- **Context:** What the app does today and the loyalty bonus you want to add.
- **Knowns and unknowns:** The rules and examples you have, and what is still unclear.
- **The agent’s role:** Ask questions and challenge assumptions before writing code.
- **Alignment:** Summarise the agreed behaviour with examples you can confirm.

Answer its questions, using the [business rules](saving-streak-app-requirements.md) to check your answers. Correct anything it misunderstood until you and the agent share the same understanding.

**Done when:** You and the AI agent are aligned on how the feature should behave, and its examples match your intent.

#### Exercise — Turn the interview into a skill

**Your task:** Ask the agent to turn the interviewing flow from 2.2 into a reusable skill.

Write your own prompt. Cover:

- **Source:** The questions and interviewing approach that helped you align with the agent.
- **When to use it:** Clarifying a feature’s intent and resolving ambiguity before implementation.
- **Flow:** Ask one question at a time, challenge assumptions, explore concrete examples and summarise the agreed behaviour for confirmation.
- **Reuse:** Capture the method without embedding the loyalty-bonus rules or your answers. The skill should work for another feature.
- **Output:** An `interview-feature` skill, saved as a `SKILL.md` in your tool’s project skill directory, with a name, description of when to use it, and clear steps. The flow ends when the user confirms shared understanding; creating a spec and coding come later.

**Done when:** You have a saved interviewing skill that you can invoke for another feature.

### 2.3 Decomposition and boundaries

First, a short tangent: you receive two versions of the same project, one vibe coded and one agentically engineered. Inspect their UML diagrams and explore how you would make the same change in each.

- What do we want to achieve, and what do we want to avoid, when asking an LLM to make a plan?
- Why do we need to guide the agent from planning through building and verification?

#### Exercise — Create a spec file

**Your task:** Turn the feature you agreed on in 2.2 into a spec file that another coding session can use.

Write your own prompt. Cover these points, based on the [to-spec skill](../.agents/skills/to-spec/SKILL.md):

- **Context:** The feature agreement from 2.2, the existing codebase and the project’s terminology. Create or reuse `feature/loyalty-bonus` and commit the spec there.
- **Contents:** The user’s problem, solution, numbered user stories, agreed implementation decisions, scope and unresolved questions. Gaps should stay explicit rather than becoming invented requirements.
- **Verification:** Observable behaviour, examples and edge cases, tested through existing interfaces rather than implementation details.
- **Output:** A local `loyalty-bonus-spec.md` that a fresh session can use without this chat. Capture decisions without code snippets or implementation file paths; no coding or issue publication yet.

**Done when:** The agent has saved `loyalty-bonus-spec.md` with the agreed behaviour, scope and testing decisions.

#### Exercise — Split the spec into tickets

**Your task:** Turn `loyalty-bonus-spec.md` into small tickets that a fresh coding session can pick up.

Write your own prompt. Cover these points, based on the [to-tickets skill](../.agents/skills/to-tickets/SKILL.md):

- **Source:** The saved `loyalty-bonus-spec.md` and the existing codebase.
- **Slices:** One narrow, complete behaviour per ticket, crossing every layer it needs and small enough for one fresh coding session. Each ticket should be demoable or testable on its own.
- **Ticket contents:** A title, what it delivers, verifiable acceptance criteria and the tickets that genuinely block it, or “None”. Put necessary preparatory refactoring before the work that depends on it.
- **Breakdown:** A proposed list so you can adjust ticket size and dependencies before it is saved.
- **Output:** One tracked Markdown file per ticket under `docs/loyalty-bonus/tickets/`, committed on the feature branch. Record each ticket’s branch and worktree, created from the feature branch when its blockers have merged. Each completed ticket branch merges back into the feature branch. No implementation yet.

**Done when:** Each ticket has its own file with what to build, acceptance criteria and blockers, and it is clear which tickets can start now.

### 2.4 Context

Your feature now has a saved spec and tickets. Pick a ticket with no blockers and prepare the context a fresh session needs to implement it.

- What happens when the agent has too much context?
- What happens when that context is compacted?
- Do you need the full feature context to implement every slice?

### 2.5 Execution and implementation

**Your task:** Start a fresh session and implement one unblocked ticket using the spec and the context prepared in 2.4.

Write your own prompt. Cover these points, based on the [implement skill](../.agents/skills/implement/SKILL.md):

- **Context and scope:** The saved spec, selected ticket, acceptance criteria and handoff. Work in the ticket’s own branch and Git worktree, based on the feature branch after its blockers have merged. Record its branch, worktree and original starting commit. Implement only this ticket.
- **TDD:** Use test-driven development where possible at the agreed test boundaries. Start with a failing test, implement enough to pass, then refactor.
- **Checks:** Run typechecking and focused test files regularly. Run the full test suite once at the end.
- **Review:** Check the changes against repository standards and the ticket’s requirements separately. Look for unclear or unnecessarily complex code, missing or incorrect behaviour, and unrequested changes. Report findings by category and address them.
- **Finish:** Commit the implementation and ticket summary on the ticket branch. Leave it unmerged for the independent reviews in 2.6.

**Done when:** One ticket is implemented, checked, reviewed and committed, with a summary ready for 2.6.

### 2.6 Validation and review

**Your task:** Start a fresh review session and set up three subagents, each reviewing a different scope of the app independently.

Write your own prompt. Cover:

- **Shared context:** The spec, ticket branch and worktree, and the diff from the recorded starting commit to the current ticket commit. Review the app running from that worktree.
- **Agent 1 — Code:** Use `code-review` to assess code quality, repository standards and whether the changes match the requirements.
- **Agent 2 — Tests:** Check coverage, edge cases and whether assertions prove the intended behaviour.
- **Agent 3 — UI/UX:** Review user flows, clarity and accessibility in the running app where possible. State what could not be verified.
- **Output:** Record all three reviews in the ticket under Code, Tests and UI/UX, including findings, evidence and verification gaps. Commit the review documentation on the ticket branch. Leave fixes and merging for 2.7.

**Done when:** The ticket contains all three reviews, with findings, evidence and any limits on what could be checked.

### 2.7 Iteration and termination

#### Exercise — Orchestrate ticket 01

**Your task:** Use an orchestrator agent to implement ticket 01 through a subagent, then run the three reviews from 2.6.

Write your own prompt. Cover:

- **Role and context:** Coordinate ticket 01 using the spec, ticket and existing findings. Create or reuse its own branch and Git worktree from `feature/loyalty-bonus` once blockers are merged. Preserve its original starting commit for the reviewers.
- **Implementer:** Summon an implementer in the ticket worktree using the 2.5 workflow: complete the ticket, address feedback, use TDD, run checks, review its changes and commit on the ticket branch.
- **Sequence:** Wait for implementation to finish before summoning the reviewers. Give each reviewer the spec, ticket and changes since the starting commit.
- **Three reviewers:** Reuse the 2.6 scopes: code via `code-review`, tests, and UI/UX where possible. Review independently and return findings without changing the code.
- **Output:** Record and commit the three reviews in ticket 01. Delegate required fixes and repeat affected reviews. Once checks pass and blocking findings are resolved, merge the ticket branch into `feature/loyalty-bonus`, run integration checks and record their results in the ticket on the feature branch. If blocked, record why the ticket is not ready to merge.

**Done when:** Ticket 01 is implemented, reviewed and merged into the feature branch, with review reports and integration-check results recorded in the ticket. Any blocker that prevents completion is documented.

#### Exercise — Make the changes easy to review

**Your task:** Write a prompt that turns the changes for ticket 01 into a curated Git diff, presented as a simple HTML or Markdown file for a human reviewer.

**Worked example:** [Open the readable HTML review](../presentation/loyalty-bonus-review.html), built from the real loyalty-bonus ticket 01 on Saving Streak’s `agentic_engineered` branch. It includes selected code changes, recorded test evidence, review concerns and the complete pinned diff.

Write your own prompt. Cover:

- **Scope:** The ticket, spec, recorded starting commit and final implementation commit. Pin both commit IDs so the report still works after the ticket branch is merged.
- **Important changes:** Group changes by behaviour, with the most important first. Explain what changed and why, highlighting business rules, interfaces, tests and risks.
- **Evidence:** Use real diff excerpts with file references and enough surrounding code to understand the change. Summarise routine edits, list omissions and include a command for the full diff.
- **Readability:** Short explanations, clear headings and easy-to-scan before/after code. Include recorded test results, review findings and verification gaps.
- **Output:** A Markdown or standalone HTML file under `docs/loyalty-bonus/reviews/`, linked from ticket 01 and committed on `feature/loyalty-bonus`. Leave the implementation unchanged.

**Done when:** A human can open the file, understand the important changes and follow its references to the full diff. The ticket links to the file.

### 2.8 Framework comparison

An exposition of spec-driven development, loop engineering and role-based development, connected to the workflow you just followed.

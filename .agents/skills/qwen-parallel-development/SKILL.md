---
name: qwen-parallel-development
description: Delegate two or more independent PKB repository workstreams to Qwen Code, then review, test, and request bounded rework. Use for parallelizable implementation or investigation with non-overlapping ownership; do not use for small or tightly coupled changes.
---

# Qwen parallel development

Use Qwen Code only when the task can be split into at least two bounded work packets that do not edit the same files or depend on unfinished output from one another. Keep tightly coupled work and final integration in the primary agent.

Before delegation:

- Read the applicable repository instructions and record the existing Git status and diff. Preserve all pre-existing changes.
- Define each packet's goal, owned paths, constraints, acceptance criteria, and focused verification.
- Do not delegate deployment, commits, pushes, destructive operations, secrets, or external mutations unless the user separately authorized them.

Run one Qwen coordinator from the repository root and tell it to delegate the packets concurrently to its subagents:

```text
qwen --approval-mode auto --max-subagent-depth 1 --max-wall-time 20m '<task prompt>'
```

The prompt must include the work packets and instruct Qwen to:

- follow `AGENTS.md` and relevant repository documentation;
- enforce non-overlapping file ownership;
- preserve unrelated and pre-existing changes;
- avoid staging, committing, pushing, deploying, or broad cleanup;
- run focused verification for every packet;
- report changed files, tests, assumptions, and unresolved issues.

Use `--approval-mode plan` for read-only investigation. Never use `--yolo`. If concurrent edits cannot be isolated safely, delegate analysis only or work sequentially.

After Qwen finishes, independently inspect the complete diff and affected neighboring code; do not rely on its summary. Check scope, correctness, library-first choices, pragmatic SOLID design, simplicity, error handling, and test coverage. Run the applicable `pkb-change-verification` mode.

If review finds defects, invoke Qwen again with the concrete findings, allowed paths, and required verification. Review the corrected diff again. Stop after two unsuccessful correction rounds and either fix the remaining issue directly or report the blocker; never present unverified delegated output as complete.

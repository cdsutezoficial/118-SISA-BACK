# Skill Registry — 118-SISA-BACK

Detected: 2026-07-05

Index only — sub-agents read the exact `SKILL.md` paths listed below, not a
generated summary. SDD phase skills (`sdd-*`), `_shared`, and `skill-registry`
itself are excluded per scan rules.

No project-level skill directories were found under
`C:\workspace\SISAv2\118-SISA-BACK` (no `skills/`, `.claude/skills/`,
`.agent/skills/`, etc.) and no convention files (`AGENTS.md`, `CLAUDE.md`,
`.cursorrules`, `GEMINI.md`, `copilot-instructions.md`) exist in this repo.
All entries below are user-level skills from `~/.claude/skills/`.

## Skills Index

| Skill | Trigger | Scope | Path |
|---|---|---|---|
| branch-pr | Create Gentle AI pull requests with issue-first checks; creating/opening/preparing PRs for review | user | `C:\Users\JoseNarvaez\.claude\skills\branch-pr\SKILL.md` |
| chained-pr | PRs over 400 lines, stacked PRs, review slices; split oversized changes into chained PRs | user | `C:\Users\JoseNarvaez\.claude\skills\chained-pr\SKILL.md` |
| cognitive-doc-design | Design docs that reduce cognitive load; guides, READMEs, RFCs, onboarding, architecture, review-facing docs | user | `C:\Users\JoseNarvaez\.claude\skills\cognitive-doc-design\SKILL.md` |
| comment-writer | Write warm, direct collaboration comments; PR feedback, issue replies, reviews, Slack, GitHub comments | user | `C:\Users\JoseNarvaez\.claude\skills\comment-writer\SKILL.md` |
| go-testing | Go tests, go test coverage, Bubbletea teatest, golden files (not applicable to this Java/Maven repo — kept for index completeness) | user | `C:\Users\JoseNarvaez\.claude\skills\go-testing\SKILL.md` |
| issue-creation | Create Gentle AI issues with issue-first checks; GitHub issues, bug reports, feature requests | user | `C:\Users\JoseNarvaez\.claude\skills\issue-creation\SKILL.md` |
| judgment-day | Dual review, adversarial review; blind dual review, fix confirmed issues, re-judge | user | `C:\Users\JoseNarvaez\.claude\skills\judgment-day\SKILL.md` |
| skill-creator | New skills, agent instructions; create LLM-first skills with valid frontmatter | user | `C:\Users\JoseNarvaez\.claude\skills\skill-creator\SKILL.md` |
| skill-improver | Improve/audit/refactor skills, skill quality | user | `C:\Users\JoseNarvaez\.claude\skills\skill-improver\SKILL.md` |
| work-unit-commits | Plan commits as reviewable work units; implementation, commit splitting, chained PRs, tests/docs with code | user | `C:\Users\JoseNarvaez\.claude\skills\work-unit-commits\SKILL.md` |

## Notes

- No Java/Spring/Maven-specific skill exists yet in the registry (unlike
  `go-testing` for Go). If backend testing patterns emerge as this repo
  grows, consider authoring a `java-spring-testing` skill via `skill-creator`.
- `branch-pr`, `chained-pr`, `issue-creation`, `judgment-day`, and
  `work-unit-commits` are directly relevant to this repo's SDD apply/verify
  workflow (PR creation, review-load management, commit planning).

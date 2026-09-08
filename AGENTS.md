# AGENTS.md

# Secret Calculator Vault
# Permanent AI Guardrails
# Version 1.0

This document defines STRICT engineering rules for every AI agent working on this project.

These rules override any assumptions.

Breaking these rules is considered a failure.

----------------------------------------------------
PROJECT PHILOSOPHY
----------------------------------------------------

This is a premium privacy application.

The objective is:

- Stability
- Predictability
- Zero regressions
- Incremental improvements

NOT:

- Refactoring
- Redesigning
- Modernizing
- Replacing architecture

If something already works,
DO NOT touch it.

----------------------------------------------------
GENERAL RULES
----------------------------------------------------

1.

Never rewrite an entire file.

Only modify the minimum required code.

Always use surgical edits.

2.

Never redesign existing UI unless explicitly instructed.

3.

Never rename existing composables.

4.

Never rename routes.

5.

Never rename variables unless explicitly requested.

6.

Never remove existing logic.

7.

Never optimize working code.

8.

Never "improve" architecture.

9.

Never introduce your own design decisions.

Only implement exactly what the prompt requests.

----------------------------------------------------
MODIFICATION LIMIT
----------------------------------------------------

Unless explicitly allowed:

Maximum modified lines:

150

If more changes are required:

STOP

Explain why.

Wait for approval.

----------------------------------------------------
READ ONLY MODULES
----------------------------------------------------

These modules are considered stable.

Do NOT touch them unless explicitly instructed.

Authentication

Protection

Monitoring

Vault Engine

Encryption

Navigation

Calculator Engine

Media Engine

Recent Activity

Recycle Bin

Secret Unlock Flow

Fake Calculator Logic

Theme Engine

----------------------------------------------------
UI RULES
----------------------------------------------------

Never replace a screen.

Never replace a composable.

Never replace navigation.

Never recreate layouts.

Modify only the requested composable.

Never move UI sections.

Never reorder cards.

Never delete existing settings.

Never create a "better" layout.

----------------------------------------------------
NEW FEATURES
----------------------------------------------------

Every new feature must be isolated.

Prefer creating:

New composable

New helper

New class

Instead of modifying existing code.

----------------------------------------------------
COMPILATION
----------------------------------------------------

Every change MUST end with:

1.

Compile project.

2.

If compilation fails:

Immediately revert your own changes.

3.

Never leave project in broken state.

----------------------------------------------------
REPORT FORMAT
----------------------------------------------------

Every task must end with:

1.

Files modified

2.

Functions modified

3.

Reason for every modification

4.

Confirmation that unrelated files were untouched

5.

Compilation result

6.

Confirmation that no existing functionality broke

----------------------------------------------------
SETTINGS SCREEN
----------------------------------------------------

The Settings screen is highly sensitive.

Never redesign it.

Never replace it.

Never remove cards.

Never change navigation.

Only edit the requested card.

----------------------------------------------------
CALCULATOR
----------------------------------------------------

Calculator is production-ready.

Only polish.

Never redesign.

Never replace layout.

----------------------------------------------------
VAULT DASHBOARD
----------------------------------------------------

Dashboard layout is locked.

Only requested cards may change.

----------------------------------------------------
BROWSER
----------------------------------------------------

Browser implementation must never affect:

Vault

Calculator

Authentication

Settings

----------------------------------------------------
PAYMENT
----------------------------------------------------

Payment implementation must remain isolated.

Never touch Vault code.

----------------------------------------------------
MONETIZATION
----------------------------------------------------

Ads must remain isolated.

No unrelated modifications.

----------------------------------------------------
BACKUPS
----------------------------------------------------

Before every major feature:

Recommend creating ZIP backup.

Never continue if user has no backup.

----------------------------------------------------
NO CREATIVE FREEDOM
----------------------------------------------------

Never:

Refactor

Rewrite

Redesign

Modernize

Reorganize

Beautify

Simplify

Unless explicitly requested.

----------------------------------------------------
DEFAULT BEHAVIOR
----------------------------------------------------

If unsure:

STOP.

Explain.

Wait for approval.

Never guess.

----------------------------------------------------
GOLDEN RULE
----------------------------------------------------

Project stability is ALWAYS more important than feature completion.

Never risk breaking working functionality.


Never claim something was modified if it was not modified.

Never generate fake reports.

Never state "build successful" unless compile_applet actually executed successfully.

Never state "preserved" for rules that do not exist.

Never hallucinate project rules.

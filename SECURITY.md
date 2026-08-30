# Security Policy

## Supported versions

Nothing is published yet - Kabuki is in early development and has no released
version. Once 0.1.0 is out, this section will name the versions that get fixes.

## What ships where

Only `kabuki-semantics` is meant to be part of an application: it carries the
test tags that production code sets. Everything else - `kabuki-core`,
`kabuki-runner`, `kabuki-junit4` - belongs to test source sets and never reaches
a release build.

A problem in a test-only artifact is still worth reporting. Its blast radius is
just different: a developer machine or a CI runner, not your users' devices.

## Reporting a vulnerability

Use GitHub's private reporting: **Security -> Report a vulnerability** on this
repository. That opens a channel visible only to the maintainers.

If you do not see that button, open a normal issue saying only that you have a
security report and asking how to send it - no details. Someone will open the
channel for you. A public issue with the details is a disclosure, so please do
not write them there.

What helps: the affected artifact and version, what an attacker gains, and the
smallest way to reproduce it. A patch is welcome, never required.

## What to expect

Kabuki is maintained by one person, in the open. Expect a first answer within a
week. Fixes ship as a new version, and the advisory is published once the fix is
available. If you disagree with an assessment, say so in the same thread - the
report will not be closed silently.

## Out of scope

- Bugs in the application under test rather than in Kabuki.
- Known CVEs in dependencies that exist only in test source sets, unless there
  is a path from a test run to something outside it.
- Anything that requires already controlling the machine that runs the tests.

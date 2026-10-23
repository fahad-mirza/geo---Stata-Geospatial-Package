# Java tests live inside each module

JUnit tests are in each module's own `src/test/java` folder (for example
`geo-core/src/test/java`), not here. That is where Maven looks for them, so `mvnw verify` in
GitHub Actions runs them automatically on every runner with no extra configuration.

This folder is kept only so the layout matches the handover. See DEV_NOTES.md,
"Proposed amendments", item 2.

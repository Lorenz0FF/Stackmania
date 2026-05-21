<!--
Thanks for opening a PR. Please fill in every section below.
PRs without verification output or a test plan will be sent back for one.
-->

## Summary

<!-- One paragraph: what changes and why. -->

## Changes

<!--
Bullet list of the concrete changes. Group by package if multiple modules touched.
Example:
- `core/StackmaniaCore.java`: …
- `memory/AggressiveMemoryOptimizer.java`: …
- `docs/ARCHITECTURE.md`: …
-->

- 

## Verification

<!--
The exact commands you ran and their result.
At minimum include the build + tests. For perf-sensitive changes, include bench output.
-->

```bash
# Build
./gradlew clean stackmaniaJar
# Result: BUILD SUCCESSFUL in <time>
```

```bash
# Tests
./gradlew test
# Result: <X> tests, <Y> failures, <Z> errors
```

<!-- For perf-sensitive PRs, paste the relevant subset of /stackmania bench dump output here. -->

## Test plan

<!-- Bulleted checklist of what a reviewer should verify. -->

- [ ] Builds locally
- [ ] Tests pass
- [ ] Behavior change documented (README / ARCHITECTURE / BENCHMARKS / CONTRIBUTING)
- [ ] If a module was added: row added to BENCHMARKS.md matrix and entry added to ARCHITECTURE.md layer map
- [ ] If a config key was added: default included in shipped `stackmania.yml`

## Out of scope

<!--
Anything you noticed but deliberately did not fix in this PR. Helps reviewers not ask "why didn't you also…".
Example: "Stackmania's memory layer overlaps with ModernFix — tracked separately for the 1.2.0 bench."
-->

- 

## Related issues / PRs

<!-- Closes #X, refs #Y, follow-up to #Z. -->

- 

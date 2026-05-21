---
name: Perf regression report
about: Report a measured performance regression — typically while running the bench matrix from docs/BENCHMARKS.md.
title: '[perf] '
labels: perf-regression, waiting
assignees: ''

---

<!-- ISSUE_TEMPLATE_PERF -> IMPORTANT: DO NOT DELETE THIS LINE. -->

<!--
This template is for *measured* regressions — i.e. you have two bench dumps and a delta you can show.
For "feels slow" reports without numbers, please use the unknown bug report template instead.
-->

## Environment

- **Stackmania version:** <!-- e.g. 1.1.2 (from /stackmania version or the jar filename) -->
- **Forge version:** <!-- e.g. 47.4.13 -->
- **Java version:** <!-- output of `java -version` -->
- **OS / kernel:** <!-- e.g. Ubuntu 22.04 / Windows 11 / etc. -->
- **CPU / RAM / storage:** <!-- e.g. Ryzen 9 7950X / 64 GB / NVMe -->
- **JVM flags:** <!-- paste the -X flags used for both runs -->
- **World seed / save:** <!-- same world used for both runs? if no, please re-run -->
- **Mods present:** <!-- list, or "default" if running the README's recommended set -->
- **Plugins present:** <!-- list, or "none" -->

## What changed between the two runs

<!--
The smallest possible change between the bench runs being compared.
Example: "flipped tick_optimizer.enabled from true to false"
Example: "upgraded ModernFix from 5.x to 5.y"
-->

## Module / feature involved

- **Module disabled (or flag flipped):** `<module_name>` from `<value>` to `<value>`
- **Other modules:** all at their default (or list deviations)

## Metric impacted

<!-- Pick all that apply. -->

- [ ] RAM (heap_used_mb_p95)
- [ ] TPS min
- [ ] MSPT p95 / p99
- [ ] GC pause p95
- [ ] GC full count
- [ ] Startup time
- [ ] Thread count
- [ ] Other: <!-- specify -->

## Numbers

| Metric | Baseline | Regressed | Delta (abs) | Delta (%) |
|---|---|---|---|---|
| <!-- e.g. RAM p95 (MB) --> | <!-- e.g. 2840 --> | <!-- e.g. 3210 --> | <!-- e.g. +370 --> | <!-- e.g. +13% --> |
| | | | | |

## Bench JSON

<!--
Attach or paste both bench dumps (gist preferred for >50 lines).
Mark which is the baseline and which is the regression.
-->

- Baseline dump: <!-- link or paste -->
- Regression dump: <!-- link or paste -->

## Reproduction confidence

- [ ] Ran the regression cell 3+ times, regression is consistent (> noise floor from 3 baseline runs)
- [ ] Ran 1–2 times only — could be noise
- [ ] Single run

## Logs / additional context

<!-- Anything else relevant: log excerpts, screenshots, related issues, hypotheses about what changed. -->

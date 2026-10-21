# Tooling and build

## Maven over Gradle

Chose Maven: declarative, predictable, reproducible builds, and widely accepted in regulated (banking) settings. Gave up Gradle's build flexibility and incremental build speed.

## Java 21

Chose Java 21: a long-term-support release, so it keeps getting security patches, which regulated environments want. Mature, with strong Spring Boot support. Banks are moving to 17+, so it is a realistic target. Virtual threads, records and pattern matching are useful extras.

Gave up: Java 25, the newer LTS (September 2025). Revisit once Spring Boot support is confirmed and the Boot 4 migration happens.

## Spring Boot 3.5.x now, Boot 4 later

Chose Boot 3.5.16: the mature, widely deployed line, and the one most banks and tutorials use. Boot 4 (Spring Framework 7, Jakarta EE 11, Hibernate 7, Jackson 3, modularised starters) would add migration risk before any domain code exists.

Gave up: newest features. The Boot 4 migration is planned last (roadmap item 10), using OpenRewrite, with the breakages written up in the PR.

## Flyway over Liquibase or Hibernate auto-DDL

Chose Flyway: versioned plain-SQL migrations that are reviewed in PRs and applied in order. The schema is explicit and repeatable, which matters for a ledger. Hibernate auto-DDL can silently change tables and cannot express things like constraint triggers. Flyway also benefits from Postgres's transactional DDL, so a failed migration rolls back cleanly.

Gave up: Liquibase's database-agnostic changelogs and built-in rollbacks.

## Testcontainers over H2 or a shared dev DB

Chose Testcontainers: tests run against real Postgres, so locking, `NUMERIC` and constraint triggers behave as in production. H2 would hide Postgres-specific behaviour. A shared dev DB leaks state between runs and developers.

Gave up: some test speed, and Docker is required locally and in CI.

## Postgres 17 pinned, not latest

Chose to pin `postgres:17` in tests: a new major version cannot break CI unexpectedly. Upgrade deliberately.

Gave up: automatic upgrades. Revisit when 17 nears end of life.

## Supply-chain security: Renovate, a CycloneDX SBOM and Trivy

Three pieces, all in the pipeline:

- **Renovate** (`renovate.json`) opens pull requests for outdated dependencies, Maven libraries and GitHub Actions alike. It waits 3 days after a release before proposing it, groups minor and patch updates into one pull request, pins GitHub Actions to full commit hashes, and is told not to propose Spring Boot 4, Postgres 18 or a new Java major version (all deliberate decisions). It only works once the Renovate GitHub App is installed on the repository.
- **CycloneDX SBOM:** the `cyclonedx-maven-plugin` writes a list of every library the app ships (76 components, CycloneDX 1.6, with package URLs) to `target/classes/META-INF/sbom/application.cdx.json`. Spring Boot's parent POM already configures what the plugin runs, so declaring it in `pom.xml` switches it on. The file goes into the jar and is uploaded by CI as the `sbom` artifact.
- **Trivy** (`.github/workflows/security.yml`) scans the SBOM and fails the build on any HIGH or CRITICAL finding. It runs on every pull request and every push to `main`, and every Monday, because new vulnerabilities appear without code changes.

Trivy is pinned to a full commit, not a tag. In March 2026 an attacker moved 76 of the 77 `trivy-action` tags to malicious commits (CVE-2026-33634), so a tag proves nothing. The pinned commit is the immutable `v0.36.0` release, published after the incident, and Trivy itself is pinned to `v0.76.0` (not the action's default, `v0.70.0`, which is affected by three published Trivy advisories fixed in 0.71.0 to 0.72.0, including a high-severity path traversal through downloaded artifacts such as the vulnerability database). Release `v0.76.0` was checked with `gh release verify` and its published checksum.

The first scan found 11 HIGH or CRITICAL findings in 5 libraries. Four were patch updates, so `pom.xml` overrides Spring Boot's versions of Jackson, Tomcat and the PostgreSQL driver (each line has a comment saying to remove it once Boot catches up). The fifth, `spring-webmvc` 6.2.19, has two CRITICAL CVEs with no patched 6.2 release (GitHub's advisories list the fix only in Spring Framework 7). They are accepted in `.trivyignore.yaml` with a reason and an expiry date of 2026-12-31, and the Boot 4 migration (roadmap item 10) removes them.

Gave up: the build can now fail because of a vulnerability published overnight, and the scan sees only the libraries Maven resolves, not the operating system or a container image (there is no image yet). Revisit when a container image is built.

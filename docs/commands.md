# Commands

Every command you need to build, test and run the project. Run them from the project root. Use the tasks below for everyday work, and the Maven commands when you need something they don't cover.

On Windows PowerShell, use `.\task` and `.\mvnw.cmd` instead of `./task` and `./mvnw`, and put `-D` options in quotes (`"-DexcludedGroups=integration"`), or PowerShell splits them at the `=`.

## Tasks

`./task <name>` (`.\task <name>` in PowerShell) runs the project tasks. Developers and CI use the same ones. They are defined in [tasks.py](../tasks.py), which needs Python 3 and nothing else.

| Task | What it does |
|---|---|
| `./task test` | Clean build, unit tests, then lists the files and lines that are not covered |
| `./task integration` | Integration tests (needs Docker) |
| `./task coverage` | Lists the uncovered files and lines from the last test run, without running anything |
| `./task up` | Starts Docker Desktop if it isn't running, starts Postgres and waits until it is healthy, then runs the app. Ctrl+C stops the app but leaves Postgres running |
| `./task down` | Stops Postgres. Your data is kept |
| `./task down --reset` | Stops Postgres and deletes its data (needed if you change `V1__init.sql` after it has run) |

The sections below are the plain Maven commands behind them.

## Tests

| What | Command |
|---|---|
| Unit tests (no Docker). Also writes the coverage report and checks the coverage rule | `./mvnw test -DexcludedGroups=integration` |
| Integration tests (needs Docker) | `./mvnw test -Dgroups=integration` |
| Everything | `./mvnw test` |
| One test class | `./mvnw test -Dtest=TransferServiceTest` |
| One test method | `./mvnw test -Dtest=TransferServiceTest#transferMovesMoneyBetweenCustomers` |
| Clean build first (use if the test count looks doubled, or after moving files) | `./mvnw clean test -DexcludedGroups=integration` |

Tests that need Spring or a database must be tagged `@Tag("integration")`, or they run in the unit job.

## Coverage

- The report is written to `target/site/jacoco/index.html` by every test run.
- `./task test` and `./task coverage` print each file that is not fully covered, with its line coverage and the missing lines. CI prints the same output when a build fails.
- Save your files and use `clean` when checking coverage: Maven only sees saved files, and JaCoCo adds to `target/jacoco.exec` between runs, so old data can hide a drop.
- The build fails if coverage falls below the minimums set in the JaCoCo `check` in `pom.xml` (the `LINE` and `BRANCH` limits).
- Running a single test class will fail that check, because the rest of the code is then uncovered. Add `-Djacoco.skip=true` when you only want to run one test:

```bash
./mvnw test -Dtest=TransferServiceTest -Djacoco.skip=true
```

## Running the app

| What | Command |
|---|---|
| App with a throwaway Postgres (started by Testcontainers, needs Docker) | `./mvnw spring-boot:test-run` |
| Start the local Postgres (port 5432, data kept in a volume) | `docker compose up -d` |
| Stop it | `docker compose down` |
| Stop it and delete the data (needed if you change `V1__init.sql` after it has run, or Flyway fails on a checksum mismatch) | `docker compose down -v` |
| Run the app against that Postgres | `./mvnw spring-boot:run` |

Health endpoints (no details are shown):

| URL | Meaning |
|---|---|
| http://localhost:8080/actuator/health | Overall status, and the names of the two groups below |
| http://localhost:8080/actuator/health/liveness | The app is running. Stays UP if the database is down, so it is the one to use for "restart me" |
| http://localhost:8080/actuator/health/readiness | The app can take traffic. Includes the database, and returns 503 when Postgres is unreachable |

## Trying the API

Accounts and transfers need to know who is calling, so every request sends an `X-Customer-Id: <uuid>` header (the Postman collection does this with its `customerId` variable). Accounts opened through `POST /accounts` start at zero and there is no deposit endpoint yet, so load two funded demo accounts for that customer first (safe to run again, it resets the balances):

```bash
./task up        # in one terminal: Postgres and the app
docker compose exec -T postgres psql -U ledger -d ledger < docs/postman/seed-accounts.sql
```

PowerShell does not support `<`, so on Windows use this for the second command:

```powershell
Get-Content docs\postman\seed-accounts.sql | docker compose exec -T postgres psql -U ledger -d ledger
```

Then import [docs/postman/ledger-platform.postman_collection.json](postman/ledger-platform.postman_collection.json) into Postman and run it. It has the three health checks, then transfers split into a happy path (one successful transfer) and a sad path (the error cases, including a reused reference), each with tests. The two demo account ids are collection variables (`fromAccountId`, `toAccountId`), as is `baseUrl` (`http://localhost:8080`). Each request uses a new UUID as its `reference`.

## Build

| What | Command |
|---|---|
| Compile only | `./mvnw compile` |
| Package the jar, skipping tests | `./mvnw package -DskipTests` |
| Delete `target/` | `./mvnw clean` |

## CI

GitHub Actions ([.github/workflows/ci.yml](../.github/workflows/ci.yml)) runs on every pull request and on every push to `main` (not on both for the same branch, which would run everything twice):

- `unit-tests`: `./task test`, then prints the coverage summary.
- `integration-tests`: `./task integration` (the coverage rule belongs to the unit job).

To reproduce a CI failure locally, run the same command.

A second workflow, [security.yml](../.github/workflows/security.yml), runs on the same triggers, and every Monday:

- `./mvnw generate-resources` writes the SBOM to `target/classes/META-INF/sbom/application.cdx.json`.
- Trivy `v0.76.0` scans it and fails on any HIGH or CRITICAL finding, ignoring only what is listed in `.trivyignore.yaml`.

To run the scan on your machine, install Trivy `v0.76.0` (check the download against its published checksum), then:

```bash
./mvnw generate-resources
trivy sbom target/classes/META-INF/sbom/application.cdx.json --severity HIGH,CRITICAL --ignorefile .trivyignore.yaml --exit-code 1
```

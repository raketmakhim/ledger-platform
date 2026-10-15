# Architecture and domain code

## Hexagonal architecture, grouped by feature

Chose hexagonal (ports and adapters), with packages grouped by feature (`ledger`, `transfer`), each split into `domain`, `application` and `adapter`. The `domain` package is plain Java with no Spring or JPA imports, so rules like "a journal entry must balance" are unit-testable without a database. REST, JPA and the Kafka outbox are adapters behind interfaces.

Gave up: simplicity. More files and interfaces than the default layered Spring layout (`controller`, `service`, `repository`). Considered and not chosen: layered, vertical slices, microservices. Revisit adding Spring Modulith if module boundaries need enforcing.

## Account holds its balance and applies postings

`Account` holds a `Money` balance, and `applyPosting(posting)` returns a new `Account` with the balance changed, leaving the original untouched. It rejects postings for another account or currency, on a non-ACTIVE account, and ones that would overdraw a customer account ("Insufficient funds"). The constructor also rejects a customer account created with a negative balance, as a safety net. Keeping the balance in the domain means these rules are unit-testable without a database, and the database `balance` column is the stored copy of it.

Gave up: the account doing its own bookkeeping means the transfer service has to coordinate saving all the changed accounts together in one database transaction. `version` is not changed by `applyPosting`: the persistence layer increments it on save.

## Few custom exceptions, under DomainException

A custom exception exists only where the API has to react differently: `InsufficientFundsException`, `AccountNotActiveException`, `CurrencyMismatchException` and `UnbalancedEntryException`, all extending an abstract `DomainException` (a `RuntimeException`) in `accounting/domain`. Each carries the details the API needs (for example the balance and the requested amount). In the Transfers API they map to stable problem types such as `/problems/insufficient-funds`, and one handler can catch the whole `DomainException` family. Everything else (nulls, blanks, bad decimals, amount too large, the wrong account for a posting, the constructor's safety nets) stays `IllegalArgumentException`: those are bad input or bugs, and the API treats them as a generic 400 or 500.

Considered: extending `IllegalArgumentException`, so older tests pass untouched. Not chosen because "insufficient funds" is not really an illegal argument.

Gave up: a few more classes, and the API handler has to know each one. Add a new exception only when a new rule needs its own response.

## Hand-written mappers between JPA entities and domain objects

ArchUnit keeps JPA out of `domain`, so the database adapter has its own JPA entities and maps them to and from the domain objects with plain hand-written mapper classes, not MapStruct. There are only a few classes (`Account`, `JournalEntry`, `Posting`), `Money` is two columns, and the domain objects are immutable records with validating constructors, so most of the mapping needs custom code even with MapStruct. Hand-written code is plain Java: easy to read, debug and test, with no extra dependency or annotation-processor setup. Mapping back to the domain runs the constructor checks again, so a corrupt row fails when loaded.

Gave up: boilerplate, and a field added to a table must also be added to the mapper (tests should assert every field). MapStruct is the more common tool for cutting this boilerplate. Revisit if the number of mapped classes grows.

## Application layer: plain outbound port interfaces, no inbound interfaces

The `application` package holds the services (such as a deposit service) and the interfaces they depend on, called ports: for example `AccountRepository` (find an account, find the clearing account for a currency, save). The database adapter implements them. Ports are plain interfaces with no `Port` suffix. We do not add separate inbound "use case" interfaces: the service class is the entry point, because an interface for every service is over-engineering at this size. This follows the usual hexagonal convention (outbound ports as repository-style interfaces, implemented by adapters), though the naming varies between teams.

Gave up: a controller depends on the concrete service class, so swapping the service means editing the controller. Add an inbound interface only if a second implementation or a real need appears.

## Time and IDs are injected

Code that needs the current time takes a `java.time.Clock`, and code that needs a new id takes a small `IdGenerator` interface, whose implementation uses `java-uuid-generator` (see `data-model.md`). Both are passed in by the application layer, so tests use `Clock.fixed(...)` and a predictable generator. The domain objects keep taking `id` and `createdAt` as plain constructor arguments and never read the clock or generate ids themselves.

Gave up: a few extra constructor parameters. The `IdGenerator` interface is our own suggestion: the research found no standard for an id-generator port.

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

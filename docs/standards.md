# Standards and regulations

What affects this project, what we do about each, and how sure we are. "Checked" means a web search on 2026-10-15 found a source. "Official" means the source was the standard's own site (iso20022.org, owasp.org, docs.spring.io, SIX, the RFC Editor). Anything not marked official came from vendor docs or blogs, so confirm it before relying on it.

## Standards (voluntary)

Technical standards that the industry agrees to follow. Nothing forces us to, but banks and payment systems expect them, so we follow them where they apply.

| Name | Published by | What it is | How we use it | Status |
|---|---|---|---|---|
| ISO 4217 | ISO, maintained by SIX Group | Currency codes and decimal places | `java.util.Currency` gives the code and the number of decimals. The JDK's list can lag ISO changes (for example `ANG` replaced by `XCG`). | Official (SIX, the maintenance agency): latest amendment found is 180 (Sept 2025), adding the euro for Bulgaria from 1 Jan 2026. Recent changes: Caribbean guilder `XCG` replaced `ANG` (amendment 176) and Zimbabwe Gold `ZWG` replaced `ZWL` (177). |
| ISO 20022 | ISO (Registration Authority: SWIFT) | Payment message format | `journal_entry.reference` holds an external payment id (`EndToEndId` or `TxId`, free text, 1 to 35 characters). Amounts in messages are at most 18 digits with up to 5 decimals, narrower than our 20 integer digits, so a per-payment limit is needed (open decision). | Official (iso20022.org market-practice and maintenance documents): `Max35Text` is 1 to 35 characters, and amounts allow 18 total digits with 5 fraction digits. `EndToEndId` is set by the initiating party and passed on unchanged. Not the base XSD itself, and decimals must follow ISO 4217. Latest message version not checked. |
| UETR | SWIFT (format added to ISO 20022 by change request) | 36-character UUID (version 4, lowercase) that tracks a cross-border payment | Fits in `reference`. Not validated in the domain. | Checked (ISO change request CR0730) |
| ISO 8601 / RFC 3339 | ISO / IETF | Timestamp format | `TIMESTAMPTZ` and `Instant` internally, UTC. The API should send RFC 3339 timestamps, written with a `Z` or a numeric offset like `+01:00`, using uppercase `T` and `Z`. | RFC 3339 official (RFC Editor): it is a profile of ISO 8601. RFC 9557 updates it so `Z` means a known UTC time with an unknown local offset. ISO 8601 itself is a paid standard and was not read. |
| RFC 9457 | IETF | Problem Details for HTTP APIs (replaces RFC 7807) | The Transfers API returns errors as `ProblemDetail` with a stable `/problems/...` type per domain exception. | Official (RFC Editor): published July 2023, replaces RFC 7807. Official (docs.spring.io): Spring supports it through `ProblemDetail`, `ErrorResponse` and `ResponseEntityExceptionHandler` (since 6.1), and Boot has `spring.mvc.problemdetails.enabled`. We are on Spring Framework 6.2, so it is available. |
| IBAN (ISO 13616) / BIC (ISO 9362) | ISO (Registration Authority: SWIFT) | Bank account and bank identifiers | Only needed when we model external accounts or outbound payments. An IBAN is at most 34 characters. | Official (SWIFT is the Registration Authority, ISO): maximum 34 characters. The registry is at Release 103, but the pages gave two different dates, so check SWIFT. ISO 13616-1:2020 was confirmed in January 2026. BIC (ISO 9362) not checked. |
| OWASP API Security Top 10 | OWASP, a non-profit community | The most common API vulnerabilities | Roadmap item 6: check that the caller owns the source account (BOLA, number 1 in the 2023 edition). | Official (owasp.org): the 2023 edition is the latest found. It warns that comparing the session user id to the requested id is not enough on its own, and recommends unpredictable ids such as GUIDs, which we use. |
| `Idempotency-Key` header | IETF (draft) | How an API client sends a retry-safe key | Roadmap item 3. | Checked: still an Internet-Draft (draft 07, Oct 2025, expired Apr 2026), not an RFC. Treat as a convention. |

## Regulations and law (legally binding)

Rules made by governments and regulators. They bind regulated firms by law. This project is not a regulated firm, so these are background for how a real bank would have to operate, not requirements on us. Not legal advice.

| Name | Published by | What it is | How we use it | Status |
|---|---|---|---|---|
| GDPR / UK GDPR | UK Parliament, enforced by the ICO | Personal data and the right to erasure | Keep personal data out of the ledger. Reference customers by id only. | Official (ICO, legislation.gov.uk): the right to erasure does not apply where the law requires you to keep the data, and the ICO gives financial regulation as an example. Data kept longer than the law requires can still be erasable. This is guidance, not legal advice. |
| Record retention and audit rules | FCA, HM Treasury and UK Parliament | How long financial records must be kept | Immutability and reversals fit. Whether the rules apply depends on being a regulated firm, which this project is not. | Official (FCA Handbook, legislation.gov.uk): SYSC 9 says at least 5 years for MiFID business, otherwise as long as relevant. Anti-money-laundering rules (FCA Financial Crime Guide) say 5 years after the relationship ends, with transaction records not needed beyond 10 years. The Payment Services Regulations 2017 say at least 5 years. The Money Laundering Regulations text itself was not read. |

Probably out of scope: PCI DSS (only if we store card data), PSD2 and Open Banking APIs, and payment-scheme rules (Faster Payments, SEPA, ACH).

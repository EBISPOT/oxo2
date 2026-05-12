# ADR-0004: OxO2 is backwards compatible with OxO v1

- **Status**: Accepted
- **Date**: 2026-05-12

## Context

OxO v1 is in production with established users, scripts, and downstream integrations that depend on its query semantics. OxO2 is a SSSOM-compliant re-implementation that replaces v1's bespoke data model with the SSSOM standard. Without a compatibility guarantee, every v1 consumer would need bespoke migration work timed to the OxO2 release.

The compatibility need is *behavioural*: callers that ask the kinds of questions v1 answered expect OxO2 to answer them with semantically equivalent results, in a format their tooling can consume. It does not require URL-literal compatibility — OxO2's HTTP surface lives under `/api/v2/...` and uses SSSOM-shaped JSON; the contract is that the v2 API can answer the same questions a v1 caller asks.

## Decision

OxO2's API surface and semantics maintain backwards compatibility with OxO v1. Where SSSOM and v1 semantics diverge, the SSSOM model is authoritative for the data, but the API exposes the metadata v1-style consumers need to continue working.

## Consequences

- API design decisions are constrained by v1 semantics. New endpoints must be able to answer v1's questions, even when SSSOM's terms are richer.
- Breaking changes to query semantics require a deliberate decision (and likely a new ADR superseding this one) — they are not absorbed silently.
- The frontend can lean on SSSOM terms, but the public API documentation must describe both SSSOM-shaped responses and the v1 questions they answer.

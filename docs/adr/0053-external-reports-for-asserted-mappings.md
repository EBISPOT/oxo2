# ADR-0053: External reports for individual asserted mappings

- **Status**: Accepted
- **Date**: 2026-11-03

## Context

[Issue #132](https://github.com/EBISPOT/oxo2/issues/132) asks for a way to report incorrect
mappings to the resource that supplies them. A mapping group can contain assertions from
different source mapping sets, while an inferred mapping can depend on assertions from several
sources. Neither identifies a single source maintainer to receive a report.

## Decision

The reporting action targets one asserted mapping and its source mapping set. It prepares an
external report for the user to review and submit; OxO2 does not store reports or change mappings
through this workflow. Reporting destinations are supplied by mapping-set metadata: the existing
`issue_tracker` and a new optional `mailing_list` field.

SSSOM files are the sole source of reporting destinations. OLS will be amended upstream to include
its tracker and mailing-list information in those files. OxO2 does not fetch additional OLS
metadata, infer contacts from provenance URLs, or maintain a separate contact registry. If the
SSSOM file supplies neither field, the mapping set has no reporting destination.

The optional `mailing_list` field accepts an email address or a web URL. Email destinations open a
draft in the user's mail client; web destinations open the mailing-list website.

Inferred mappings are not report targets. This workflow treats the reasoning rules as sound and
directs concerns about inferred conclusions to their underlying asserted mappings. This is a
product scope decision, not a guarantee that the software cannot contain bugs.

## Consequences

A mapping group must not implicitly select a source for a report. The source assertion must be
identified before handing the user to its maintainers. Opening a report destination does not mean
a report has been submitted or a mapping has been judged incorrect.

The action appears on individual asserted mapping details pages. Users inspecting an inference
reach those pages through the existing asserted-evidence links. Grouped result rows and inferred
mapping details do not gain a report action.

When both contacts exist, the issue tracker is primary and the mailing list remains an alternative.
When neither is available, show "No reporting contact available for this mapping source." Do not
redirect source-data reports to OxO's issue tracker.

Prepare the subject, predicate including any negation, object, source mapping set, and OxO mapping
link. The user supplies the explanation of what is wrong. Prefill destinations that support it;
otherwise supply copyable report text alongside the destination link.

The first version prefills GitHub issue forms and email drafts. Other issue trackers and
mailing-list websites receive a normal link with copyable report text. The user always reviews
and submits the report externally.

Existing indexed mapping sets will only gain new contacts after a dataload from SSSOM files that
contain them. End-to-end metadata preservation is required through parsing, mapping-set storage,
API responses, and frontend models; adding a UI button alone would leave the contacts unavailable.

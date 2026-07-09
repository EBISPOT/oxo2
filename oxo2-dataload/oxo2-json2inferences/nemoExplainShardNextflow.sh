#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

echo Running nemoExplainShardNextflow.sh

# Chase ONE explanation shard (ADR-0028) and trace every conclusion it owns against that warm
# engine. The shard corpus is a few thousand facts rather than the whole 7.2M-quad corpus, which is
# what turns a ~6 s per-trace cost into ~0.3-0.9 ms.
if [ "$#" -ne 4 ]; then
  echo "Usage: $0 <rules_definition> <shard_nq> <targets_file> <trace_output>"
  exit 1
fi

RULES_DEFINITION=$1
SHARD_NQ=$2
TARGETS_FILE=$3
TRACE_OUTPUT=$4

# The parameter VALUE must itself be a quoted nemo string literal, hence the escaped quotes:
# `@import assertedMapping :- nquads{resource=$importfile} .` expects a string, not a bare path.
#
# `-e none` suppresses the inferredMapping turtle export -- INFER_CROSS_SET already produced it, and
# re-exporting per shard would write thousands of redundant partial TTLs. $exportfile is still bound
# because sssom.rls declares it as a @parameter.
#
# The targets file MUST be one semicolon-separated line. A newline-separated file makes nmo silently
# trace only its FIRST fact, which would leave every other conclusion in the shard unexplained.
nmo "$RULES_DEFINITION" \
    --param importfile=\"$SHARD_NQ\" \
    --param exportfile=\"unused-export.ttl\" \
    -e none -o \
    --trace-input-file "$TARGETS_FILE" \
    --trace-output "$TRACE_OUTPUT"

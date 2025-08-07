#!/bin/bash
#
#OXO2_INFERENCES=$OXO2_DATA/inferences
#SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
#
#
### Generate ttl from json files
#$SCRIPT_DIR/oxo2-json2inferences/json2ttl.sh $OXO2_DATA/sssom-as-json/mapping $OXO2_DATA/assertedMapping.ttl
#
### Run nmo to get initial inferences
## -o: overwrites existing files
## -e idb: trace idb inferences. See https://github.com/knowsys/nemo/issues/670
## -v: show progress of inferencing. See https://github.com/knowsys/nemo/issues/676
## See https://github.com/knowsys/nemo-examples/tree/main/examples/rdf-conversion as example externalizing import/export
#nmo $SCRIPT_DIR/oxo2-json2inferences/chain-rules.rls --param importfile=\"$OXO2_DATA/assertedMapping.ttl\" \
# --param exportfile=\"inferredMapping.ttl\" -o -v -D $OXO2_INFERENCES --report all
#
### Determine mappings for which we want explanations
#$SCRIPT_DIR/oxo2-json2inferences/inferences2trace.sh $OXO2_INFERENCES/inferredMapping.ttl $OXO2_INFERENCES/inferencesToTrace.txt
#
### Get explanations for facts to trace from nmo.
### IDB - Intensional Database Predicates are those that appear in the head of a rule, i.e. (head <- tail)
#nmo $SCRIPT_DIR/oxo2-json2inferences/chain-rules.rls --param importfile=\"$OXO2_DATA/assertedMapping.ttl\" \
#--param exportfile=\"inferredMapping.ttl\"  -o -v  -D $OXO2_INFERENCES \
#--trace-input-file $OXO2_INFERENCES/inferencesToTrace.txt \
#--trace-output $OXO2_INFERENCES/inferences-chains.json --report all

## Write out inferred mappings with their explanations.
$SCRIPT_DIR/oxo2-json2inferences/explanations2json.sh $OXO2_INFERENCES/inferences-chains.json \
$OXO2_DATA/sssom-as-json/mapping $OXO2_DATA/sssom-as-json/mapping/inferred-mappings.json


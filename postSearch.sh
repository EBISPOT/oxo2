#!/usr/bin/env bash
set -euo pipefail

curl -X 'POST' "https://wwwdev.ebi.ac.uk/oxo2/api/v2/mappings/search" \
     -H "Content-Type: application/json;charset=UTF-8" \
     -d '{
          "queries":["ICTV:MSL33/ICTV19990680"],
          "page":0,
          "size":100,
          "distance":4,
          "queryFields":["subject_id","object_id", "subject_label", "predicate_label", "object_label"],
          "fieldList":["mapping_set_id","subject_id","subject_label","predicate_id","predicate_label","predicate_modifier","object_id","object_label","mapping_justification"],
          "columnFilters":[],
          "facets":["predicate_id", "mapping_justification"],
          "sortedFields": [{"id": "subject_id", "desc": false}]}'

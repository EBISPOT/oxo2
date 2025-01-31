curl -X POST "http://localhost:8080/api/v2/mappings/search" \
     -H "Content-Type: application/json" \
     -d '{
           "queries": ["PIRSF:PIRSF028729", "EFO:0003777"],
           "queryFields": ["subject_id", "object_id"],
		   "fieldList": ["subject_id","predicate_id", "object_id", "mapping_justification"],
		   "facets": ["object_id_prefix", "subject_id_prefix"],
           "page": 0,
           "size": 10
         }'

#curl -X GET "http://localhost:8080/api/v2/mappings/EFO%3A0003777" \
#     -H "Content-Type: application/json"
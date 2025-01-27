curl -X POST "http://localhost:8080/api/v2/mappings/search" \
     -H "Content-Type: application/json" \
     -d '{ 
           "queries": ["OARCS:0000019", "IMDRF:E0125"],
           "queryFields": ["subject_id", "object_id"],
		   "fieldList": ["subject_id", "predicate_id", "object_id"],
		   "facets": [],
           "page": 0,
           "size": 10
         }'
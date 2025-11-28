curl -X POST "http://localhost:8081/api/v2/mappings/search" \
     -H "Content-Type: application/json;charset=UTF-8" \
     -H "Mode: cors" \
     -d '{
          "queries":["ICTV:MSL33/ICTV19990680"],
          "page":0,
          "size":1000,
          "distance":4,
          "queryFields":["subject_id","object_id", "subject_label", "predicate_label", "object_label"],
          "fieldList":["mapping_set_id","subject_id","subject_label","subject_id_prefix","predicate_id","predicate_label","predicate_modifier","object_id","object_label","object_id_prefix","mapping_justification"],
          "columnFilters":[],
          "facets":["object_id_prefix","subject_id_prefix", "predicate_id", "mapping_justification"],
          "sortedFields": [{"id": "subject_id", "desc": "false"}]}'
     

# curl -X POST "http://backend:8080/api/v2/mappings/search" \
#      -H "Content-Type: application/json;charset=UTF-8" \
#      -H "Mode: cors" \
#      -d '{
#           "queries":["ICTV:MSL33/ICTV19990680"],
#           "page":0,
#           "size":1000,
#           "distance":4,
#           "queryFields":["subject_id","object_id", "subject_label", "predicate_label", "object_label"],
#           "fieldList":["mapping_set_id","subject_id","subject_label","subject_id_prefix","predicate_id","predicate_label","predicate_modifier","object_id","object_label","object_id_prefix","mapping_justification"],
#           "columnFilters":[],
#           "facets":["object_id_prefix","subject_id_prefix", "predicate_id", "mapping_justification"],
#           "sortedFields": [{"id": "subject_id", "desc": "false"}]}'
     
    #  '{
    #        "queries": ["PIRSF:PIRSF028729", "EFO:0003777"],
    #        "queryFields": ["subject_id", "object_id"],
		#    "fieldList": [],
		#    "facets": ["object_id_prefix", "subject_id_prefix"],
    #        "page": 0,
    #        "size": 10
    #      }'

#curl -X GET "http://localhost:8080/api/v2/mappings/EFO%3A0003777" \
#     -H "Content-Type: application/json"
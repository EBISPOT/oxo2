#!/bin/bash

set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <inputDir> <solrUrl> where <solrUrl> has the form <solr_server_url>:<port>/<solr_core>"
  echo "i.e. http://localhost:8983/solr/oxo2-mappings"
  exit 1
fi

# Directory to traverse
DIRECTORY=$1
echo "Traversing directory $DIRECTORY..."

# Check if directory exists
if [ ! -d "$DIRECTORY" ]; then
    echo "Directory $DIRECTORY does not exist. Skipping."
    exit 0
fi

# Solr URL
SOLR_URL=$2
echo "Solr URL: $SOLR_URL"

# Function to post JSON files to Solr
post_to_solr() {
  local file=$1
  local solr_url=$2
  echo "Posting $file to Solr... $solr_url/update?commit=true"
  curl -fsS -X POST -H "Content-Type: application/json" -T "$file" "$solr_url/update?commit=true"
  echo "Successfully posted $file to Solr."
}

# Find and post all .json files
while IFS= read -r -d '' file; do
  post_to_solr "$file" "$SOLR_URL"
done < <(find "$DIRECTORY" -type f -name "*.json" -print0)

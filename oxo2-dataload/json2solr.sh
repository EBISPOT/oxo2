#!/bin/bash

# Directory to traverse
DIRECTORY=$1
echo "Traversing directory $DIRECTORY..."

# Solr URL
SOLR_URL=$2
echo "Solr URL: $SOLR_URL"

# Function to post JSON files to Solr
post_to_solr() {
  local file=$1
  local solr_url=$2
  echo "Posting $file to Solr... $solr_url"
  curl -X POST -H "Content-Type: application/json" --data-binary @$file $solr_url
  if [ $? -eq 0 ]; then
    echo "Successfully posted $file to Solr."
  else
    echo "Failed to post $file to Solr."
  fi
}

# Export the function to be used by find
export -f post_to_solr

# Find and post all .json files
find $DIRECTORY -type f -name "*.json" -exec bash -c 'post_to_solr "$0" "$1"' {} $SOLR_URL \;
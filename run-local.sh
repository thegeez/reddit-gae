#!/bin/bash
# Run local development web server

# Exit on error
set -e

lein compile
# We listen on 0.0.0.0 so can test from the outside
~/Programming/appengine-java-sdk/bin/dev_appserver.sh -a 0.0.0.0 war

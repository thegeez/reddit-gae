#!/bin/bash
# Push project to appengine

set -e

lein compile
/opt/appengine-java-sdk-1.3.8/bin/appcfg.sh update war

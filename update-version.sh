#!/bin/bash

# Update DriftKit Framework version
OLD_VERSION="0.8.9"
NEW_VERSION="0.9.0"

echo "Updating DriftKit Framework version from ${OLD_VERSION} to ${NEW_VERSION}..."

# Find all pom.xml files and update version
find . -name "pom.xml" -type f -exec sed -i '' "s/<version>${OLD_VERSION}<\/version>/<version>${NEW_VERSION}<\/version>/g" {} \;

echo "Version update complete!"
echo "Updated files:"
find . -name "pom.xml" -type f -exec grep -l "${NEW_VERSION}" {} \;
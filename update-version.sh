#!/bin/bash

# Update DriftKit Framework version from 0.5.0 to 0.5.1

echo "Updating DriftKit Framework version from 0.5.1 to 0.5.2..."

# Find all pom.xml files and update version
find . -name "pom.xml" -type f -exec sed -i '' 's/<version>0.7.9<\/version>/<version>0.8.0<\/version>/g' {} \;

echo "Version update complete!"
echo "Updated files:"
find . -name "pom.xml" -type f -exec grep -l "0.7.9" {} \;
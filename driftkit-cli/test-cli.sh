#!/bin/bash

# Test script for DriftKit CLI

echo "Testing DriftKit CLI..."
echo "======================"

# Build the CLI first
echo "Building CLI..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

# Test help command
echo -e "\n\n1. Testing help command:"
java -jar target/driftkit-cli-*-jar-with-dependencies.jar help

# Test invalid template
echo -e "\n\n2. Testing invalid template (should show available options):"
java -jar target/driftkit-cli-*-jar-with-dependencies.jar new test-app --template invalid

# Test new command help
echo -e "\n\n3. Testing new command help:"
java -jar target/driftkit-cli-*-jar-with-dependencies.jar new --help

# Test run command help
echo -e "\n\n4. Testing run command help:"
java -jar target/driftkit-cli-*-jar-with-dependencies.jar run --help

echo -e "\n\nAll tests completed!"
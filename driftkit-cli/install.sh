#!/bin/bash

# DriftKit CLI Installation Script

set -e

echo "Installing DriftKit CLI..."
echo "========================="

# Check if running with sufficient permissions
if [ "$EUID" -ne 0 ] && [ ! -w "/usr/local/bin" ]; then 
    echo "This script needs write access to /usr/local/bin"
    echo "Please run with sudo: sudo ./install.sh"
    exit 1
fi

# Build the CLI if not already built
if [ ! -f "target/driftkit-cli-*-jar-with-dependencies.jar" ]; then
    echo "Building DriftKit CLI..."
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "Build failed! Please ensure you have Maven and Java 21+ installed."
        exit 1
    fi
fi

# Find the built JAR
JAR_FILE=$(ls target/driftkit-cli-*-jar-with-dependencies.jar | head -n 1)
if [ -z "$JAR_FILE" ]; then
    echo "Error: Could not find built JAR file"
    exit 1
fi

# Create installation directory
INSTALL_DIR="/usr/local/lib/driftkit"
mkdir -p "$INSTALL_DIR"

# Copy JAR to installation directory
echo "Installing JAR to $INSTALL_DIR..."
cp "$JAR_FILE" "$INSTALL_DIR/driftkit-cli.jar"

# Create executable script
SCRIPT_PATH="/usr/local/bin/driftkit"
echo "Creating executable script at $SCRIPT_PATH..."

cat > "$SCRIPT_PATH" << 'EOF'
#!/bin/bash
# DriftKit CLI launcher script

# Find Java
if [ -n "$JAVA_HOME" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA="java"
fi

# Check Java version
JAVA_VERSION=$("$JAVA" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "Error: DriftKit CLI requires Java 21 or higher"
    echo "Current Java version: $("$JAVA" -version 2>&1 | head -n 1)"
    exit 1
fi

# Run DriftKit CLI
exec "$JAVA" -jar /usr/local/lib/driftkit/driftkit-cli.jar "$@"
EOF

# Make script executable
chmod +x "$SCRIPT_PATH"

# Create uninstall script
UNINSTALL_SCRIPT="/usr/local/lib/driftkit/uninstall.sh"
cat > "$UNINSTALL_SCRIPT" << 'EOF'
#!/bin/bash
# DriftKit CLI uninstall script

echo "Uninstalling DriftKit CLI..."
rm -f /usr/local/bin/driftkit
rm -rf /usr/local/lib/driftkit
echo "DriftKit CLI has been uninstalled."
EOF
chmod +x "$UNINSTALL_SCRIPT"

echo ""
echo "✅ DriftKit CLI has been installed successfully!"
echo ""
echo "You can now use 'driftkit' command from anywhere:"
echo "  driftkit new my-app"
echo "  driftkit help"
echo ""
echo "To uninstall, run: /usr/local/lib/driftkit/uninstall.sh"
echo ""

# Test the installation
if command -v driftkit &> /dev/null; then
    echo "Testing installation..."
    driftkit --version
else
    echo "⚠️  Warning: 'driftkit' command not found in PATH"
    echo "You may need to restart your terminal or add /usr/local/bin to your PATH"
fi
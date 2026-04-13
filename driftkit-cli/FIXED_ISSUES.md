# Fixed Issues in DriftKit CLI

## 1. Case-Insensitive Templates and Build Systems

### Problem
Users had to type template names in UPPERCASE which was not intuitive:
```bash
# This was confusing
driftkit new my-app --template CHATBOT
```

### Solution
Added custom converters for case-insensitive parsing:

**TemplateConverter.java:**
- Converts any case to uppercase before matching enum
- Provides helpful error message with available templates

**BuildSystemConverter.java:**
- Same for build system (maven/gradle)

### Usage Examples
```bash
# All of these now work:
driftkit new my-app --template chatbot
driftkit new my-app --template CHATBOT
driftkit new my-app --template Chatbot
driftkit new my-app --template ChAtBoT

# Same for build system:
driftkit new my-app --build maven
driftkit new my-app --build MAVEN
driftkit new my-app --build Maven
```

## 2. Global CLI Installation

### Problem
After creating a project, users couldn't use `driftkit` command:
```bash
cd my-project
driftkit dev  # command not found
```

### Solution
Created installation scripts for global access:

**install.sh (macOS/Linux):**
- Builds the CLI if needed
- Installs JAR to `/usr/local/lib/driftkit/`
- Creates executable script in `/usr/local/bin/driftkit`
- Includes Java version check
- Creates uninstall script

**install.ps1 (Windows):**
- Installs to `%ProgramFiles%\DriftKit`
- Adds to system PATH
- Creates batch and PowerShell wrappers
- Includes uninstall script

### Installation Process

**macOS/Linux:**
```bash
cd driftkit-cli
chmod +x install.sh
./install.sh  # or sudo ./install.sh
```

**Windows (Run PowerShell as Administrator):**
```powershell
cd driftkit-cli
.\install.ps1
```

### After Installation
```bash
# Works from anywhere
driftkit new my-app --template chatbot
cd my-app
driftkit dev
driftkit run test
driftkit add vector-pinecone
```

## Benefits

1. **Better User Experience**: No need to remember uppercase for templates
2. **Global Access**: Use `driftkit` command from any directory
3. **Easy Installation**: One command to install globally
4. **Cross-Platform**: Works on macOS, Linux, and Windows
5. **Clean Uninstall**: Includes uninstall scripts

## Technical Implementation

### Converters
```java
public class TemplateConverter implements ITypeConverter<ProjectTemplate> {
    @Override
    public ProjectTemplate convert(String value) throws Exception {
        try {
            return ProjectTemplate.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Show helpful error with available templates
        }
    }
}
```

### Command Usage
```java
@Option(names = {"--template"}, 
    converter = TemplateConverter.class)
private ProjectTemplate template = ProjectTemplate.SIMPLE;
```

This makes the CLI much more user-friendly and follows common CLI conventions where options are typically case-insensitive.
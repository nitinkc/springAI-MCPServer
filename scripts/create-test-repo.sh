#!/bin/bash

# ============================================================================
# create-test-repo.sh
# Creates a dummy GitHub repository for testing MCP Server capabilities
# ============================================================================

set -e

# Configuration
REPO_NAME="${1:-mcp-test-sandbox}"
GITHUB_USER="${2:-$(gh api user -q .login 2>/dev/null || echo 'YOUR_USERNAME')}"

echo "🚀 MCP Test Repository Setup"
echo "=============================="
echo ""

# Check prerequisites
check_prerequisites() {
    echo "📋 Checking prerequisites..."
    
    if ! command -v gh &> /dev/null; then
        echo "❌ GitHub CLI (gh) is not installed."
        echo "   Install with: brew install gh"
        echo "   Then run: gh auth login"
        exit 1
    fi
    
    if ! gh auth status &> /dev/null; then
        echo "❌ Not authenticated with GitHub CLI."
        echo "   Run: gh auth login"
        exit 1
    fi
    
    echo "✅ Prerequisites met"
    echo ""
}

# Create local repository structure
create_local_repo() {
    echo "📁 Creating local repository structure..."
    
    REPO_DIR="./test-repos/${REPO_NAME}"
    
    if [ -d "$REPO_DIR" ]; then
        echo "⚠️  Directory already exists: $REPO_DIR"
        read -p "   Delete and recreate? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            rm -rf "$REPO_DIR"
        else
            exit 1
        fi
    fi
    
    mkdir -p "$REPO_DIR"
    cd "$REPO_DIR"
    
    # Initialize git
    git init
    
    # Create Maven project structure
    mkdir -p src/main/java/com/example/calculator
    mkdir -p src/test/java/com/example/calculator
    
    # Create pom.xml
    cat > pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>calculator</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>Calculator Service</name>
    <description>A simple calculator service for MCP testing</description>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <junit.version>5.10.2</junit.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
EOF

    # Create Calculator.java
    cat > src/main/java/com/example/calculator/Calculator.java << 'EOF'
package com.example.calculator;

/**
 * A simple calculator service.
 * 
 * TODO: Add more operations like:
 * - multiply
 * - divide  
 * - modulo
 */
public class Calculator {
    
    /**
     * Adds two numbers together.
     */
    public int add(int a, int b) {
        return a + b;
    }
    
    /**
     * Subtracts b from a.
     */
    public int subtract(int a, int b) {
        return a - b;
    }
    
    // TODO: Implement multiply method
    
    // TODO: Implement divide method (handle division by zero!)
}
EOF

    # Create CalculatorTest.java
    cat > src/test/java/com/example/calculator/CalculatorTest.java << 'EOF'
package com.example.calculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorTest {
    
    private Calculator calculator;
    
    @BeforeEach
    void setUp() {
        calculator = new Calculator();
    }
    
    @Test
    @DisplayName("add() should return sum of two positive numbers")
    void testAddPositiveNumbers() {
        assertEquals(5, calculator.add(2, 3));
    }
    
    @Test
    @DisplayName("add() should handle negative numbers")
    void testAddNegativeNumbers() {
        assertEquals(-1, calculator.add(2, -3));
    }
    
    @Test
    @DisplayName("subtract() should return difference")
    void testSubtract() {
        assertEquals(2, calculator.subtract(5, 3));
    }
    
    // TODO: Add tests for multiply and divide when implemented
}
EOF

    # Create README.md
    cat > README.md << 'EOF'
# Calculator Service

A simple calculator service for testing MCP Server capabilities.

## Building

```bash
./mvnw clean package
```

## Testing

```bash
./mvnw test
```

## Current Features

- ✅ Addition
- ✅ Subtraction
- ❌ Multiplication (TODO)
- ❌ Division (TODO)

## Contributing

See the GitHub Issues for tasks that need to be completed.
EOF

    # Create Maven wrapper
    mvn wrapper:wrapper -q 2>/dev/null || echo "Note: Maven wrapper not created, using system Maven"
    
    # Create .gitignore
    cat > .gitignore << 'EOF'
target/
*.class
*.jar
*.log
.idea/
*.iml
.DS_Store
EOF

    # Initial commit
    git add .
    git commit -m "Initial commit: Calculator service with add and subtract"
    
    echo "✅ Local repository created"
    echo ""
}

# Create GitHub repository
create_github_repo() {
    echo "☁️  Creating GitHub repository..."
    
    # Check if repo already exists
    if gh repo view "${GITHUB_USER}/${REPO_NAME}" &> /dev/null; then
        echo "⚠️  Repository already exists: ${GITHUB_USER}/${REPO_NAME}"
        read -p "   Delete and recreate? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            gh repo delete "${GITHUB_USER}/${REPO_NAME}" --yes
        else
            echo "   Using existing repository"
            git remote add origin "https://github.com/${GITHUB_USER}/${REPO_NAME}.git" 2>/dev/null || true
            git push -u origin main --force
            return
        fi
    fi
    
    # Create new repo
    gh repo create "${REPO_NAME}" \
        --public \
        --description "Test repository for MCP Server experimentation" \
        --source . \
        --remote origin \
        --push
    
    echo "✅ GitHub repository created: https://github.com/${GITHUB_USER}/${REPO_NAME}"
    echo ""
}

# Create sample issues
create_sample_issues() {
    echo "📝 Creating sample issues..."
    
    # Issue 1: Add multiply method
    gh issue create \
        --title "Add multiply() method to Calculator" \
        --body "## Description
We need to add a multiply method to the Calculator class.

## Requirements
- Method signature: \`public int multiply(int a, int b)\`
- Should handle positive and negative numbers
- Add corresponding unit tests

## Acceptance Criteria
- [ ] Implement multiply() method
- [ ] Add at least 3 test cases
- [ ] All tests pass" \
        --label "enhancement"
    
    # Issue 2: Add divide method
    gh issue create \
        --title "Add divide() method with zero handling" \
        --body "## Description
Implement a divide method that handles division by zero gracefully.

## Requirements
- Method signature: \`public int divide(int a, int b)\`
- Throw \`IllegalArgumentException\` when dividing by zero
- Use integer division (floor)

## Acceptance Criteria
- [ ] Implement divide() method
- [ ] Handle division by zero
- [ ] Add test for normal division
- [ ] Add test for division by zero exception" \
        --label "enhancement"
    
    # Issue 3: Bug report
    gh issue create \
        --title "Bug: subtract() returns wrong result for certain inputs" \
        --body "## Bug Description
The subtract method might have edge cases that aren't properly handled.

## Steps to Reproduce
1. Create a Calculator instance
2. Call subtract(Integer.MIN_VALUE, 1)
3. Observe potential overflow

## Expected Behavior
Should either handle overflow gracefully or document the limitation.

## Investigation Needed
- Check for integer overflow scenarios
- Add defensive coding if needed
- Add relevant test cases" \
        --label "bug"
    
    # Create labels if they don't exist
    gh label create "enhancement" --description "New feature or request" --color "a2eeef" 2>/dev/null || true
    gh label create "bug" --description "Something isn't working" --color "d73a4a" 2>/dev/null || true
    
    echo "✅ Sample issues created"
    echo ""
}

# Print summary
print_summary() {
    echo ""
    echo "=============================================="
    echo "🎉 Test Repository Setup Complete!"
    echo "=============================================="
    echo ""
    echo "Repository: https://github.com/${GITHUB_USER}/${REPO_NAME}"
    echo "Local path: $(pwd)"
    echo ""
    echo "📋 Created Issues:"
    echo "   #1 - Add multiply() method"
    echo "   #2 - Add divide() method"  
    echo "   #3 - Bug: subtract() edge cases"
    echo ""
    echo "🔧 Next Steps:"
    echo "   1. Set your GitHub token:"
    echo "      export GITHUB_TOKEN=\$(gh auth token)"
    echo ""
    echo "   2. Start the MCP Server:"
    echo "      cd /Users/PSP1000909/Learn/SpringAI"
    echo "      ./mvnw spring-boot:run"
    echo ""
    echo "   3. Connect an AI client to http://localhost:8080/mcp"
    echo ""
    echo "   4. Try these prompts:"
    echo "      - \"List issues in ${GITHUB_USER}/${REPO_NAME}\""
    echo "      - \"Clone ${GITHUB_USER}/${REPO_NAME} and implement issue #1\""
    echo "      - \"Run the tests and fix any failures\""
    echo ""
}

# Main execution
main() {
    check_prerequisites
    create_local_repo
    create_github_repo
    create_sample_issues
    print_summary
}

main

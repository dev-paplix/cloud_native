#!/bin/bash
# Fix pom.xml files - Add dependencyManagement section before dependencies

echo "Fixing resilience4j-demo/pom.xml..."
cd ~/cloud_native/resilience4j-demo

# Check if dependencyManagement already exists after properties
if grep -A 5 "</properties>" pom.xml | grep -q "dependencyManagement"; then
    echo "✓ resilience4j-demo pom.xml already has dependencyManagement in correct position"
else
    # Create backup
    cp pom.xml pom.xml.backup
    
    # Add dependencyManagement after </properties> and before <dependencies>
    sed -i '/<\/properties>/a\
\
    <dependencyManagement>\
        <dependencies>\
            <dependency>\
                <groupId>com.azure.spring</groupId>\
                <artifactId>spring-cloud-azure-dependencies</artifactId>\
                <version>${spring-cloud-azure.version}</version>\
                <type>pom</type>\
                <scope>import</scope>\
            </dependency>\
        </dependencies>\
    </dependencyManagement>' pom.xml
    
    echo "✓ Fixed resilience4j-demo/pom.xml"
fi

echo ""
echo "Fixing eventhub-consumer/pom.xml..."
cd ~/cloud_native/day3/code/eventhub-consumer

if grep -A 5 "</properties>" pom.xml | grep -q "dependencyManagement"; then
    echo "✓ eventhub-consumer pom.xml already has dependencyManagement in correct position"
else
    cp pom.xml pom.xml.backup
    
    sed -i '/<\/properties>/a\
\
    <dependencyManagement>\
        <dependencies>\
            <!-- Spring Cloud Dependencies -->\
            <dependency>\
                <groupId>org.springframework.cloud</groupId>\
                <artifactId>spring-cloud-dependencies</artifactId>\
                <version>${spring-cloud.version}</version>\
                <type>pom</type>\
                <scope>import</scope>\
            </dependency>\
\
            <!-- Azure Spring Cloud Dependencies -->\
            <dependency>\
                <groupId>com.azure.spring</groupId>\
                <artifactId>spring-cloud-azure-dependencies</artifactId>\
                <version>${spring-cloud-azure.version}</version>\
                <type>pom</type>\
                <scope>import</scope>\
            </dependency>\
        </dependencies>\
    </dependencyManagement>' pom.xml
    
    echo "✓ Fixed eventhub-consumer/pom.xml"
fi

echo ""
echo "Fixing eventhub-producer/pom.xml..."
cd ~/cloud_native/day3/code/eventhub-producer

if grep -A 5 "</properties>" pom.xml | grep -q "dependencyManagement"; then
    echo "✓ eventhub-producer pom.xml already has dependencyManagement in correct position"
else
    cp pom.xml pom.xml.backup
    
    sed -i '/<\/properties>/a\
\
    <dependencyManagement>\
        <dependencies>\
            <!-- Spring Cloud Dependencies -->\
            <dependency>\
                <groupId>org.springframework.cloud</groupId>\
                <artifactId>spring-cloud-dependencies</artifactId>\
                <version>${spring-cloud.version}</version>\
                <type>pom</type>\
                <scope>import</scope>\
            </dependency>\
\
            <!-- Azure Spring Cloud Dependencies -->\
            <dependency>\
                <groupId>com.azure.spring</groupId>\
                <artifactId>spring-cloud-azure-dependencies</artifactId>\
                <version>${spring-cloud-azure.version}</version>\
                <type>pom</type>\
                <scope>import</scope>\
            </dependency>\
        </dependencies>\
    </dependencyManagement>' pom.xml
    
    echo "✓ Fixed eventhub-producer/pom.xml"
fi

echo ""
echo "=========================================="
echo "All pom.xml files have been fixed!"
echo "=========================================="
echo ""
echo "Now you can build the projects:"
echo "  cd ~/cloud_native/resilience4j-demo"
echo "  mvn clean package -DskipTests"
echo ""

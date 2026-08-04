# Assinador PDF Desktop

A desktop application built with Java and JavaFX for signing PDF documents using digital certificates (ICP-Brasil).

## Features

- **GUI Interface**: Modern desktop application with a clean user interface.
- **Digital Signature**: Support for signing PDF files with digital certificates.
- **Multiple Signatures**: Add multiple signatures to the same document.
- **File Operations**: Open, save, and manage PDF files.
- **Certificate Management**: Browse and select digital certificates from the system keystore.
- **Visual Feedback**: Real-time updates on signature status and document changes.

## Prerequisites

- **Java 17** or higher (to run the executable JAR or build from source)
- **Apache Maven** 3.6 or higher (only required if building from source)

## Download & Installation

### Download Ready-to-Use JAR (Recommended)
The GitHub Actions workflow automatically builds and publishes the executable JAR on every release. You don't need to build it manually!

1. Go to [GitHub Releases](https://github.com/antonioaguiar/assinadorpdf/releases).
2. Download the latest `assinador-pdf-v*.jar` file.
3. Run the application (Java 17+ required):
   ```bash
   java -jar assinador-pdf-v*.jar
   ```

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/antonioaguiar/assinadorpdf.git
   cd assinadorpdf
   ```

2. Build the project with Maven:
   ```bash
   mvn clean package
   ```

## Usage

### Running the Application

Using Maven exec plugin:

```bash
mvn javafx:run
```

Or running the compiled JAR:

```bash
java -jar target/assinador-pdf-1.0.0-SNAPSHOT.jar
```

### Application Workflow

1. **Open PDF**: Click the "Open" button to select a PDF file to sign.
2. **Select Certificate**: Click the "Certificado" button to browse and select your digital certificate.
3. **Position Signature**: Drag and drop the signature box to the desired location on the PDF preview.
4. **Add Signature**: Click the "Assinar" button to add the digital signature.
5. **Save**: Click the "Salvar" button to save the signed PDF.

## Development

### Project Structure

```
src/main/java/com/example/assinadorpdf/    # Main application code
├── controller/                              # FXML Controllers
├── model/                                   # Data models and services
├── view/                                    # FXML views
└── util/                                    # Utility classes
src/main/resources/                        # Resources and FXML files
src/test/java/                              # Test code
```

### Building with Maven

The project uses the `maven-shade-plugin` to create a shaded JAR file containing all dependencies.

```bash
# Clean and build
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Generate documentation
mvn javadoc:javadoc
```

### Running Tests

```bash
# Run all tests
mvn test

# Run with code coverage
mvn verify
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

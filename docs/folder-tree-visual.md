# Apex Spring Boot - Visual Folder Tree

```
🏗️ apex-idp/
│
├── 📦 src/
│   ├── 📂 main/
│   │   ├── ☕ java/com/apex/idp/
│   │   │   │
│   │   │   ├── 🚀 ApexIdpApplication.java
│   │   │   │
│   │   │   ├── 🌐 interfaces/
│   │   │   │   ├── 🎯 rest/
│   │   │   │   │   ├── 🔐 AuthController.java
│   │   │   │   │   ├── 📋 BatchController.java
│   │   │   │   │   ├── 📄 DocumentController.java
│   │   │   │   │   ├── 💰 InvoiceController.java
│   │   │   │   │   └── 🏢 VendorController.java
│   │   │   │   │
│   │   │   │   ├── 🔌 websocket/
│   │   │   │   │   └── 📡 BatchStatusWebSocketHandler.java
│   │   │   │   │
│   │   │   │   ├── 📦 dto/
│   │   │   │   │   ├── Authentication DTOs
│   │   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   │   ├── LoginResponse.java
│   │   │   │   │   │   ├── RefreshTokenRequest.java
│   │   │   │   │   │   └── UserInfoResponse.java
│   │   │   │   │   ├── Business DTOs
│   │   │   │   │   │   ├── BatchDTO.java
│   │   │   │   │   │   ├── DocumentDTO.java
│   │   │   │   │   │   ├── InvoiceDTO.java
│   │   │   │   │   │   └── VendorDTO.java
│   │   │   │   │   └── Utility DTOs
│   │   │   │   │       ├── CountResponseDTO.java
│   │   │   │   │       ├── AnalysisDTO.java
│   │   │   │   │       └── ChatResponseDTO.java
│   │   │   │   │
│   │   │   │   └── 🚨 exception/
│   │   │   │       └── GlobalExceptionHandler.java
│   │   │   │
│   │   │   ├── 💼 application/
│   │   │   │   ├── 🔧 service/
│   │   │   │   │   ├── Core Services
│   │   │   │   │   │   ├── AuthenticationService.java
│   │   │   │   │   │   ├── BatchService.java
│   │   │   │   │   │   ├── DocumentService.java
│   │   │   │   │   │   └── InvoiceService.java
│   │   │   │   │   └── Support Services
│   │   │   │   │       ├── VendorService.java
│   │   │   │   │       ├── AnalysisService.java
│   │   │   │   │       └── InvoiceExtractionService.java
│   │   │   │   │
│   │   │   │   └── Application Services
│   │   │   │       ├── VendorApplicationService.java
│   │   │   │       └── InvoiceApplicationService.java
│   │   │   │
│   │   │   ├── 🏛️ domain/
│   │   │   │   ├── 📦 batch/
│   │   │   │   │   ├── 🔷 Batch.java (Aggregate Root)
│   │   │   │   │   ├── 🏷️ BatchStatus.java
│   │   │   │   │   ├── 🗄️ BatchRepository.java
│   │   │   │   │   └── 🔍 BatchSpecification.java
│   │   │   │   │
│   │   │   │   ├── 📄 document/
│   │   │   │   │   ├── 🔷 Document.java
│   │   │   │   │   ├── 🏷️ DocumentStatus.java
│   │   │   │   │   └── 🗄️ DocumentRepository.java
│   │   │   │   │
│   │   │   │   ├── 💰 invoice/
│   │   │   │   │   ├── 🔷 Invoice.java
│   │   │   │   │   ├── 🏷️ InvoiceStatus.java
│   │   │   │   │   ├── 📋 LineItem.java (Value Object)
│   │   │   │   │   ├── 🗄️ InvoiceRepository.java
│   │   │   │   │   └── ⚙️ DomainInvoiceService.java
│   │   │   │   │
│   │   │   │   ├── 🏢 vendor/
│   │   │   │   │   ├── 🔷 Vendor.java
│   │   │   │   │   ├── 🏷️ VendorStatus.java
│   │   │   │   │   ├── 🗄️ VendorRepository.java
│   │   │   │   │   └── ⚙️ DomainVendorService.java
│   │   │   │   │
│   │   │   │   └── 📊 analysis/
│   │   │   │       ├── 🔷 Analysis.java
│   │   │   │       └── 🗄️ AnalysisRepository.java
│   │   │   │
│   │   │   ├── 🔧 infrastructure/
│   │   │   │   ├── 📨 kafka/
│   │   │   │   │   └── BatchEventProducer.java
│   │   │   │   │
│   │   │   │   ├── 💾 storage/
│   │   │   │   │   ├── StorageService.java
│   │   │   │   │   ├── MinIOStorageService.java
│   │   │   │   │   └── StorageException.java
│   │   │   │   │
│   │   │   │   ├── 👁️ ocr/
│   │   │   │   │   ├── OCRService.java
│   │   │   │   │   ├── OCRResult.java
│   │   │   │   │   └── TextRegion.java
│   │   │   │   │
│   │   │   │   ├── 🤖 ai/
│   │   │   │   │   └── OpenAIService.java
│   │   │   │   │
│   │   │   │   └── 📡 websocket/
│   │   │   │       └── WebSocketNotificationService.java
│   │   │   │
│   │   │   ├── ⚙️ config/
│   │   │   │   ├── 🔐 SecurityConfig.java
│   │   │   │   ├── 🔌 WebSocketConfig.java
│   │   │   │   ├── 📨 KafkaConfig.java
│   │   │   │   ├── 💾 RedisConfig.java
│   │   │   │   └── 📖 SwaggerConfig.java
│   │   │   │
│   │   │   └── 🛡️ security/
│   │   │       ├── 🔑 JwtTokenProvider.java
│   │   │       ├── 🚪 JwtAuthenticationFilter.java
│   │   │       └── 👤 CustomUserDetailsService.java
│   │   │
│   │   └── 📁 resources/
│   │       ├── ⚙️ application.yml
│   │       ├── 🔧 application-dev.yml
│   │       ├── 🏭 application-prod.yml
│   │       ├── 📝 logback-spring.xml
│   │       └── 🎨 banner.txt
│   │
│   └── 🧪 test/
│       └── ☕ java/com/apex/idp/
│           ├── 🧪 ApexIdpApplicationTests.java
│           ├── 🌐 interfaces/
│           ├── 💼 application/
│           ├── 🏛️ domain/
│           └── 🔧 infrastructure/
│
├── 📋 pom.xml
├── 🐳 docker-compose.yml
├── 🐋 Dockerfile
├── 📖 README.md
├── 🚫 .gitignore
└── 🔐 .env.example
```

## Icon Legend

- 🏗️ **Project Root**
- 📦 **Source Directory**
- 📂 **Main Directory**
- ☕ **Java Source**
- 🚀 **Main Application Class**
- 🌐 **Interfaces Layer** - External communication
- 💼 **Application Layer** - Use case orchestration
- 🏛️ **Domain Layer** - Business logic
- 🔧 **Infrastructure Layer** - Technical implementations
- ⚙️ **Configuration** - Spring configurations
- 🛡️ **Security** - Authentication & authorization
- 🧪 **Test** - Unit and integration tests

### Controller Icons
- 🔐 **Auth** - Authentication
- 📋 **Batch** - Batch processing
- 📄 **Document** - Document management
- 💰 **Invoice** - Invoice processing
- 🏢 **Vendor** - Vendor management

### Service Icons
- 🎯 **REST** - RESTful APIs
- 🔌 **WebSocket** - Real-time communication
- 📦 **DTO** - Data transfer objects
- 🚨 **Exception** - Error handling
- 📡 **Notification** - Event notifications

### Domain Icons
- 🔷 **Entity** - Domain entities
- 🏷️ **Enum** - Status enumerations
- 🗄️ **Repository** - Data access
- 🔍 **Specification** - Query specifications
- ⚙️ **Domain Service** - Business logic

### Infrastructure Icons
- 📨 **Kafka** - Event streaming
- 💾 **Storage** - File storage
- 👁️ **OCR** - Optical character recognition
- 🤖 **AI** - Artificial intelligence
- 🔑 **JWT** - Token management

## Quick Navigation Guide

1. **Start Here**: `ApexIdpApplication.java` - Main entry point
2. **API Endpoints**: `interfaces/rest/*Controller.java`
3. **Business Logic**: `application/service/*Service.java`
4. **Domain Models**: `domain/*/`
5. **External Integrations**: `infrastructure/*/`
6. **Security Setup**: `security/` and `config/SecurityConfig.java`
7. **Configuration**: `resources/application.yml`

## Development Workflow

```
Client Request → Controller → Service → Domain → Repository
                    ↓           ↓          ↓         ↓
                   DTO      Use Case   Business   Database
                            Logic      Rules
```
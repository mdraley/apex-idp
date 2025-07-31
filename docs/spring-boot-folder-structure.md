# Apex Spring Boot API - Folder Structure

## Complete Project Structure

```
apex-idp/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── apex/
│   │   │           └── idp/
│   │   │               ├── ApexIdpApplication.java                    # Main Spring Boot Application
│   │   │               │
│   │   │               ├── interfaces/                                # Presentation Layer
│   │   │               │   ├── rest/                                 # REST Controllers
│   │   │               │   │   ├── AuthController.java               # Authentication endpoints
│   │   │               │   │   ├── BatchController.java              # Batch processing endpoints
│   │   │               │   │   ├── DocumentController.java           # Document management endpoints
│   │   │               │   │   ├── InvoiceController.java            # Invoice management endpoints
│   │   │               │   │   └── VendorController.java             # Vendor management endpoints
│   │   │               │   │
│   │   │               │   ├── websocket/                            # WebSocket Handlers
│   │   │               │   │   └── BatchStatusWebSocketHandler.java  # Real-time batch updates
│   │   │               │   │
│   │   │               │   ├── dto/                                  # Data Transfer Objects
│   │   │               │   │   ├── LoginRequest.java                 # Authentication DTOs
│   │   │               │   │   ├── LoginResponse.java
│   │   │               │   │   ├── RefreshTokenRequest.java
│   │   │               │   │   ├── UserInfoResponse.java
│   │   │               │   │   ├── BatchDTO.java                     # Batch DTOs
│   │   │               │   │   ├── BatchStatusUpdate.java
│   │   │               │   │   ├── DocumentDTO.java                  # Document DTOs
│   │   │               │   │   ├── InvoiceDTO.java                   # Invoice DTOs
│   │   │               │   │   ├── VendorDTO.java                    # Vendor DTOs
│   │   │               │   │   ├── CountResponseDTO.java             # Utility DTOs
│   │   │               │   │   ├── AnalysisDTO.java
│   │   │               │   │   ├── ChatRequestDTO.java
│   │   │               │   │   ├── ChatResponseDTO.java
│   │   │               │   │   ├── SystemNotification.java
│   │   │               │   │   └── HeartbeatMessage.java
│   │   │               │   │
│   │   │               │   └── exception/                            # Exception Handling
│   │   │               │       └── GlobalExceptionHandler.java       # Global exception handler
│   │   │               │
│   │   │               ├── application/                              # Application Layer
│   │   │               │   ├── service/                              # Application Services
│   │   │               │   │   ├── AuthenticationService.java        # Authentication logic
│   │   │               │   │   ├── BatchService.java                 # Batch processing orchestration
│   │   │               │   │   ├── DocumentService.java              # Document processing orchestration
│   │   │               │   │   ├── InvoiceService.java               # Invoice processing orchestration
│   │   │               │   │   ├── VendorService.java                # Vendor management logic
│   │   │               │   │   ├── AnalysisService.java              # AI analysis orchestration
│   │   │               │   │   └── InvoiceExtractionService.java     # Invoice data extraction
│   │   │               │   │
│   │   │               │   ├── VendorApplicationService.java         # Vendor application service
│   │   │               │   └── InvoiceApplicationService.java        # Invoice application service
│   │   │               │
│   │   │               ├── domain/                                   # Domain Layer
│   │   │               │   ├── batch/                                # Batch Aggregate
│   │   │               │   │   ├── Batch.java                       # Batch entity
│   │   │               │   │   ├── BatchStatus.java                 # Batch status enum
│   │   │               │   │   ├── BatchRepository.java             # Batch repository interface
│   │   │               │   │   └── BatchSpecification.java          # JPA specifications
│   │   │               │   │
│   │   │               │   ├── document/                             # Document Aggregate
│   │   │               │   │   ├── Document.java                    # Document entity
│   │   │               │   │   ├── DocumentStatus.java              # Document status enum
│   │   │               │   │   └── DocumentRepository.java          # Document repository interface
│   │   │               │   │
│   │   │               │   ├── invoice/                              # Invoice Aggregate
│   │   │               │   │   ├── Invoice.java                     # Invoice entity
│   │   │               │   │   ├── InvoiceStatus.java               # Invoice status enum
│   │   │               │   │   ├── LineItem.java                    # Line item value object
│   │   │               │   │   ├── InvoiceRepository.java           # Invoice repository interface
│   │   │               │   │   └── DomainInvoiceService.java        # Invoice domain service
│   │   │               │   │
│   │   │               │   ├── vendor/                               # Vendor Aggregate
│   │   │               │   │   ├── Vendor.java                      # Vendor entity
│   │   │               │   │   ├── VendorStatus.java                # Vendor status enum
│   │   │               │   │   ├── VendorRepository.java            # Vendor repository interface
│   │   │               │   │   └── DomainVendorService.java         # Vendor domain service
│   │   │               │   │
│   │   │               │   └── analysis/                             # Analysis Aggregate
│   │   │               │       ├── Analysis.java                    # Analysis entity
│   │   │               │       └── AnalysisRepository.java          # Analysis repository interface
│   │   │               │
│   │   │               ├── infrastructure/                           # Infrastructure Layer
│   │   │               │   ├── kafka/                                # Event Streaming
│   │   │               │   │   └── BatchEventProducer.java          # Kafka event producer
│   │   │               │   │
│   │   │               │   ├── storage/                              # File Storage
│   │   │               │   │   ├── StorageService.java              # Storage interface
│   │   │               │   │   ├── MinIOStorageService.java         # MinIO implementation
│   │   │               │   │   └── StorageException.java            # Storage exceptions
│   │   │               │   │
│   │   │               │   ├── ocr/                                  # OCR Service
│   │   │               │   │   ├── OCRService.java                  # OCR service interface
│   │   │               │   │   ├── OCRResult.java                   # OCR result model
│   │   │               │   │   └── TextRegion.java                  # Text region model
│   │   │               │   │
│   │   │               │   ├── ai/                                   # AI Services
│   │   │               │   │   └── OpenAIService.java               # OpenAI integration
│   │   │               │   │
│   │   │               │   └── websocket/                            # WebSocket Infrastructure
│   │   │               │       └── WebSocketNotificationService.java # WebSocket notifications
│   │   │               │
│   │   │               ├── config/                                   # Configuration Classes
│   │   │               │   ├── SecurityConfig.java                   # Spring Security configuration
│   │   │               │   ├── WebSocketConfig.java                  # WebSocket configuration
│   │   │               │   ├── KafkaConfig.java                      # Kafka configuration
│   │   │               │   ├── RedisConfig.java                      # Redis cache configuration
│   │   │               │   └── SwaggerConfig.java                    # OpenAPI/Swagger configuration
│   │   │               │
│   │   │               └── security/                                 # Security Components
│   │   │                   ├── JwtTokenProvider.java                 # JWT token handling
│   │   │                   ├── JwtAuthenticationFilter.java          # JWT authentication filter
│   │   │                   └── CustomUserDetailsService.java         # User details service
│   │   │
│   │   └── resources/
│   │       ├── application.yml                                       # Main configuration
│   │       ├── application-dev.yml                                   # Development profile
│   │       ├── application-prod.yml                                  # Production profile
│   │       ├── logback-spring.xml                                    # Logging configuration
│   │       └── banner.txt                                            # Spring Boot banner
│   │
│   └── test/
│       └── java/
│           └── com/
│               └── apex/
│                   └── idp/
│                       ├── ApexIdpApplicationTests.java             # Main application tests
│                       ├── interfaces/
│                       │   └── rest/                                 # Controller tests
│                       │       ├── AuthControllerTest.java
│                       │       ├── BatchControllerTest.java
│                       │       ├── DocumentControllerTest.java
│                       │       ├── InvoiceControllerTest.java
│                       │       └── VendorControllerTest.java
│                       ├── application/
│                       │   └── service/                              # Service tests
│                       │       ├── BatchServiceTest.java
│                       │       ├── DocumentServiceTest.java
│                       │       ├── InvoiceServiceTest.java
│                       │       └── VendorServiceTest.java
│                       ├── domain/                                   # Domain tests
│                       │   ├── batch/
│                       │   │   └── BatchTest.java
│                       │   ├── document/
│                       │   │   └── DocumentTest.java
│                       │   ├── invoice/
│                       │   │   └── InvoiceTest.java
│                       │   └── vendor/
│                       │       └── VendorTest.java
│                       └── infrastructure/                           # Infrastructure tests
│                           ├── kafka/
│                           │   └── BatchEventProducerTest.java
│                           └── storage/
│                               └── MinIOStorageServiceTest.java
│
├── pom.xml                                                           # Maven configuration
├── docker-compose.yml                                                # Docker services setup
├── Dockerfile                                                        # Application container
├── README.md                                                         # Project documentation
├── .gitignore                                                        # Git ignore file
└── .env.example                                                      # Environment variables example
```

## Package Structure Summary

### 📁 **Interfaces Layer** (`com.apex.idp.interfaces`)
- **Purpose**: Handle external communication (HTTP, WebSocket)
- **Components**: REST controllers, WebSocket handlers, DTOs, Exception handling
- **Dependencies**: Application layer only

### 📁 **Application Layer** (`com.apex.idp.application`)
- **Purpose**: Orchestrate use cases and coordinate domain operations
- **Components**: Application services, Use case implementations
- **Dependencies**: Domain layer, Infrastructure layer

### 📁 **Domain Layer** (`com.apex.idp.domain`)
- **Purpose**: Core business logic and rules
- **Components**: Entities, Value objects, Domain services, Repository interfaces
- **Dependencies**: None (pure business logic)

### 📁 **Infrastructure Layer** (`com.apex.idp.infrastructure`)
- **Purpose**: Technical implementations and external integrations
- **Components**: Kafka, Storage, OCR, AI services, WebSocket infrastructure
- **Dependencies**: Domain layer interfaces

### 📁 **Config Package** (`com.apex.idp.config`)
- **Purpose**: Spring Boot configuration classes
- **Components**: Security, WebSocket, Kafka, Redis configurations

### 📁 **Security Package** (`com.apex.idp.security`)
- **Purpose**: Security-related components
- **Components**: JWT handling, Authentication filters, User details service

## Key Architectural Principles

1. **Dependency Rule**: Dependencies point inward (Infrastructure → Application → Domain)
2. **Separation of Concerns**: Each layer has distinct responsibilities
3. **Domain Isolation**: Domain layer has no external dependencies
4. **Interface Segregation**: Clean interfaces between layers
5. **Single Responsibility**: Each class has one reason to change

## File Count Summary

- **Controllers**: 5 REST controllers
- **Services**: 8 application services, 2 domain services
- **Entities**: 5 domain entities
- **DTOs**: 15+ data transfer objects
- **Repositories**: 5 repository interfaces
- **Infrastructure**: 7 infrastructure services
- **Configuration**: 5 configuration classes
- **Total Java Files**: ~65 files

This structure follows Domain-Driven Design (DDD) principles and provides a clean, maintainable architecture for the Apex Intelligent Document Processing system.
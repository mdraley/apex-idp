# Apex Architecture Visual Diagrams

## 1. Layered Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           PRESENTATION LAYER                             │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │     REST     │  │  WebSocket   │  │     DTOs     │  │ Exception  │ │
│  │ Controllers  │  │   Handlers   │  │   Objects    │  │  Handler   │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          APPLICATION LAYER                               │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │    Batch     │  │   Document   │  │   Invoice    │  │   Vendor   │ │
│  │   Service    │  │   Service    │  │   Service    │  │  Service   │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └────────────┘ │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │  Analysis    │  │ Invoice      │  │    Auth      │                  │
│  │   Service    │  │ Extraction   │  │   Service    │                  │
│  └──────────────┘  └──────────────┘  └──────────────┘                  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            DOMAIN LAYER                                  │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │    Batch     │  │   Document   │  │   Invoice    │  │   Vendor   │ │
│  │   Entity     │  │    Entity    │  │   Entity     │  │   Entity   │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └────────────┘ │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │   Domain     │  │   Domain     │  │ Repository   │                  │
│  │  Invoice     │  │   Vendor     │  │ Interfaces   │                  │
│  │   Service    │  │   Service    │  │              │                  │
│  └──────────────┘  └──────────────┘  └──────────────┘                  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         INFRASTRUCTURE LAYER                             │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │    MinIO     │  │    Kafka     │  │   OpenAI     │  │    OCR     │ │
│  │   Storage    │  │   Producer   │  │   Service    │  │  Service   │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └────────────┘ │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │   Database   │  │    Redis     │  │  WebSocket   │  │  Security  │ │
│  │ (PostgreSQL) │  │    Cache     │  │   Service    │  │    JWT     │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

## 2. Request Flow Diagram

```
┌────────┐     ┌──────────────┐     ┌───────────────┐     ┌──────────────┐
│ Client │────▶│ JWT Filter   │────▶│ REST          │────▶│ Application  │
└────────┘     └──────────────┘     │ Controller    │     │ Service      │
                                    └───────────────┘     └──────────────┘
                                                                   │
                                                                   ▼
┌────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────┐
│ Response   │◀────│ DTO Mapping  │◀────│ Domain       │◀────│ Domain   │
└────────────┘     └──────────────┘     │ Service      │     │ Entity   │
                                        └──────────────┘     └──────────┘
                                                                   │
                                                                   ▼
                                                             ┌──────────┐
                                                             │ Database │
                                                             └──────────┘
```

## 3. Document Processing Flow

```
┌─────────────┐
│   Upload    │
│  Documents  │
└──────┬──────┘
       │
       ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Batch     │────▶│   Store     │────▶│   Kafka     │
│  Creation   │     │  in MinIO   │     │   Event     │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │
                                               ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│     OCR     │────▶│  Extract    │────▶│   Store     │
│ Processing  │     │   Invoice   │     │  Invoice    │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │
                                               ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   OpenAI    │────▶│   Update    │────▶│  WebSocket  │
│  Analysis   │     │   Status    │     │   Update    │
└─────────────┘     └─────────────┘     └─────────────┘
```

## 4. Component Dependencies

```
                           ┌──────────────────┐
                           │   Controllers    │
                           └────────┬─────────┘
                                   │ uses
                                   ▼
                           ┌──────────────────┐
                           │ App Services     │
                           └────────┬─────────┘
                                   │ orchestrates
                                   ▼
                    ┌──────────────┴──────────────┐
                    │                             │
                    ▼                             ▼
            ┌──────────────┐             ┌──────────────┐
            │Domain Service│             │Infrastructure│
            └──────┬───────┘             └──────┬───────┘
                   │ uses                        │ implements
                   ▼                             ▼
            ┌──────────────┐             ┌──────────────┐
            │   Entities   │             │External APIs │
            └──────────────┘             └──────────────┘
```

## 5. Security Architecture

```
┌────────────┐     ┌──────────────┐     ┌──────────────┐     ┌────────────┐
│   Request  │────▶│ Spring       │────▶│     JWT      │────▶│   Auth     │
│            │     │ Security     │     │   Filter     │     │  Service   │
└────────────┘     └──────────────┘     └──────────────┘     └────────────┘
                                                │
                                                ▼
                                        ┌──────────────┐
                                        │ User Details │
                                        │   Service    │
                                        └──────────────┘
                                                │
                                                ▼
                                        ┌──────────────┐
                                        │   Security   │
                                        │   Context    │
                                        └──────────────┘
```

## 6. Event-Driven Architecture

```
┌──────────────┐          ┌──────────────┐          ┌──────────────┐
│    Batch     │ produces │              │ consumes │  Document    │
│   Service    │─────────▶│    Kafka     │◀─────────│  Processor   │
└──────────────┘          │              │          └──────────────┘
                          │  ┌────────┐  │
                          │  │ Topic  │  │
                          │  │ Batch  │  │
                          │  │Created │  │
                          │  └────────┘  │
                          │              │
                          │  ┌────────┐  │
                          │  │ Topic  │  │
                          │  │  OCR   │  │
                          │  │Complete│  │
                          │  └────────┘  │
                          │              │
                          │  ┌────────┐  │
                          │  │ Topic  │  │
                          │  │Analysis│  │
                          │  │Request │  │
                          │  └────────┘  │
                          └──────────────┘
```

## 7. Data Flow Summary

```
Frontend ──HTTP──▶ REST API ──▶ Service Layer ──▶ Domain Layer
   ▲                                │                    │
   │                                │                    ▼
   │                                │              PostgreSQL
   │                                │                    ▲
   │                                ▼                    │
   └──WebSocket──── Real-time ◀── Kafka ◀────────────────┘
                    Updates         Events
```

## Package Color Legend

- 🔵 **Blue**: Interfaces Layer (External Communication)
- 🟢 **Green**: Application Layer (Use Case Orchestration)
- 🟡 **Yellow**: Domain Layer (Business Logic)
- 🔴 **Red**: Infrastructure Layer (Technical Implementation)
- ⚫ **Black**: Configuration & Security (Cross-cutting Concerns)
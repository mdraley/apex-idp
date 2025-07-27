export interface User {
    id: string
    username: string
    email: string
    role: string
}

export interface Batch {
    id: string
    name: string
    status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
    documentCount: number
    processedCount: number
    createdAt: string
    updatedAt: string
}

export interface Document {
    id: string
    batchId: string
    fileName: string
    fileType: string
    status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
    ocrText?: string
    createdAt: string
    updatedAt: string
}

export interface Invoice {
    id: string
    documentId: string
    vendorId?: string
    vendorName?: string
    invoiceNumber?: string
    invoiceDate?: string
    dueDate?: string
    amount?: number
    status: 'PENDING' | 'PROCESSED' | 'APPROVED' | 'REJECTED'
    imageUrl?: string
    lineItems?: LineItem[]
    createdAt: string
    updatedAt: string
}

export interface LineItem {
    description: string
    quantity: number
    unitPrice: number
    amount: number
}

export interface Vendor {
    id: string
    name: string
    email?: string
    phone?: string
    address?: string
    taxId?: string
    status: 'ACTIVE' | 'INACTIVE'
    invoiceCount: number
    createdAt: string
    updatedAt: string
}

export interface Analysis {
    id: string
    batchId: string
    summary: string
    recommendations: string[]
    createdAt: string
}

export interface ChatMessage {
    id: string
    batchId: string
    message: string
    response: string
    createdAt: string
}

export interface ApiResponse<T> {
    data: T
    message?: string
    error?: string
}

export interface PaginatedResponse<T> {
    content: T[]
    totalElements: number
    totalPages: number
    page: number
    size: number
}

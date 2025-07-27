import { useState, useEffect } from 'react'
import { ChevronLeft, ChevronRight, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import ChatBox from './ChatBox'
import { Batch, Invoice } from '@/types'
import { api } from '@/lib/api'
import { useQuery } from '@tanstack/react-query'

interface InvoiceViewerProps {
    batch: Batch
    onClose: () => void
}

export default function InvoiceViewer({ batch, onClose }: InvoiceViewerProps) {
    const [currentIndex, setCurrentIndex] = useState(0)

    // 1. IMPROVEMENT: Explicitly type the data returned from useQuery for better type safety.
    const { data: invoices, isLoading } = useQuery<Invoice[]>({
        queryKey: ['batch-invoices', batch.id],
        queryFn: async () => {
            // The API call will now be expected to return Invoice[]
            const response = await api.get(`/batches/${batch.id}/invoices`)
            return response.data
        },
        // This query will refetch automatically when `batch.id` changes.
    })

    // 2. CRITICAL FIX: Add an effect to reset the index when the batch changes.
    // This prevents an out-of-bounds error if the new batch has fewer invoices.
    useEffect(() => {
        setCurrentIndex(0)
    }, [batch.id])

    const currentInvoice = invoices?.[currentIndex]

    const handlePrevious = () => {
        if (currentIndex > 0) {
            setCurrentIndex(currentIndex - 1)
        }
    }

    const handleNext = () => {
        if (invoices && currentIndex < invoices.length - 1) {
            setCurrentIndex(currentIndex + 1)
        }
    }

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-96">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
            </div>
        )
    }

    if (!invoices || invoices.length === 0) {
        return (
            <Card className="p-8 text-center">
                <p>No invoices found in this batch</p>
                <Button onClick={onClose} className="mt-4">Back to Batches</Button>
            </Card>
        )
    }

    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <h2 className="text-2xl font-bold">
                    Invoice {currentIndex + 1} of {invoices.length}
                </h2>
                <Button variant="ghost" size="icon" onClick={onClose}>
                    <X className="h-5 w-5" />
                </Button>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Image Viewer */}
                <div className="lg:col-span-2 space-y-4">
                    <Card className="p-4">
                        <div className="bg-gray-100 rounded-lg overflow-hidden" style={{ height: '600px' }}>
                            {currentInvoice?.imageUrl ? (
                                <img
                                    src={currentInvoice.imageUrl}
                                    alt={`Invoice ${currentIndex + 1}`}
                                    className="w-full h-full object-contain"
                                />
                            ) : (
                                <div className="flex items-center justify-center h-full text-gray-400">
                                    No image available
                                </div>
                            )}
                        </div>
                        <div className="flex items-center justify-between mt-4">
                            <Button
                                variant="outline"
                                onClick={handlePrevious}
                                disabled={currentIndex === 0}
                            >
                                <ChevronLeft className="h-4 w-4 mr-1" />
                                Previous
                            </Button>
                            <span className="text-sm text-muted-foreground">
                {currentIndex + 1} / {invoices.length}
              </span>
                            <Button
                                variant="outline"
                                onClick={handleNext}
                                disabled={currentIndex === invoices.length - 1}
                            >
                                Next
                                <ChevronRight className="h-4 w-4 ml-1" />
                            </Button>
                        </div>
                    </Card>

                    {/* AI Chat */}
                    <Card className="p-4">
                        <h3 className="font-semibold mb-3">AI Assistant</h3>
                        <ChatBox
                            batchId={batch.id}
                            invoiceId={currentInvoice?.id}
                        />
                    </Card>
                </div>

                {/* Data Panel */}
                <div className="space-y-4">
                    <Card className="p-4">
                        <h3 className="font-semibold mb-3">Invoice Details</h3>
                        {currentInvoice && (
                            <dl className="space-y-2">
                                <div>
                                    <dt className="text-sm text-muted-foreground">Invoice Number</dt>
                                    <dd className="font-medium">{currentInvoice.invoiceNumber || 'N/A'}</dd>
                                </div>
                                <div>
                                    <dt className="text-sm text-muted-foreground">Vendor</dt>
                                    <dd className="font-medium">{currentInvoice.vendorName || 'N/A'}</dd>
                                </div>
                                <div>
                                    <dt className="text-sm text-muted-foreground">Amount</dt>
                                    <dd className="font-medium text-lg">
                                        ${currentInvoice.amount?.toFixed(2) || '0.00'}
                                    </dd>
                                </div>
                                <div>
                                    <dt className="text-sm text-muted-foreground">Date</dt>
                                    <dd className="font-medium">
                                        {currentInvoice.invoiceDate
                                            ? new Date(currentInvoice.invoiceDate).toLocaleDateString()
                                            : 'N/A'}
                                    </dd>
                                </div>
                                <div>
                                    <dt className="text-sm text-muted-foreground">Due Date</dt>
                                    <dd className="font-medium">
                                        {currentInvoice.dueDate
                                            ? new Date(currentInvoice.dueDate).toLocaleDateString()
                                            : 'N/A'}
                                    </dd>
                                </div>
                                <div>
                                    <dt className="text-sm text-muted-foreground">Status</dt>
                                    <dd className="font-medium">
                    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                        currentInvoice.status === 'PROCESSED'
                            ? 'bg-green-100 text-green-800'
                            : currentInvoice.status === 'PENDING'
                                ? 'bg-yellow-100 text-yellow-800'
                                : 'bg-red-100 text-red-800'
                    }`}>
                      {currentInvoice.status}
                    </span>
                                    </dd>
                                </div>
                            </dl>
                        )}
                    </Card>

                    <Card className="p-4">
                        <h3 className="font-semibold mb-3">Line Items</h3>
                        {currentInvoice?.lineItems && currentInvoice.lineItems.length > 0 ? (
                            <div className="space-y-2">
                                {currentInvoice.lineItems.map((item, index) => (
                                    <div key={index} className="text-sm">
                                        <div className="flex justify-between">
                                            <span>{item.description}</span>
                                            <span className="font-medium">${item.amount.toFixed(2)}</span>
                                        </div>
                                        <div className="text-xs text-muted-foreground">
                                            Qty: {item.quantity} @ ${item.unitPrice.toFixed(2)}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <p className="text-sm text-muted-foreground">No line items found</p>
                        )}
                    </Card>
                </div>
            </div>
        </div>
    )
}
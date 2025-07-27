import { useState } from 'react'
import { FileText, Clock, CheckCircle, AlertCircle } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Batch } from '@/types'

interface BatchListProps {
    batches: Batch[]
    isLoading: boolean
    onSelectBatch: (batch: Batch) => void
}

const statusIcons = {
    PENDING: Clock,
    PROCESSING: Clock,
    COMPLETED: CheckCircle,
    FAILED: AlertCircle,
}

const statusColors = {
    PENDING: 'text-yellow-600',
    PROCESSING: 'text-blue-600',
    COMPLETED: 'text-green-600',
    FAILED: 'text-red-600',
}

export default function BatchList({ batches, isLoading, onSelectBatch }: BatchListProps) {
    if (isLoading) {
        return (
            <div className="grid gap-4">
                {[...Array(3)].map((_, i) => (
                    <Card key={i} className="animate-pulse">
                        <CardHeader>
                            <div className="h-6 bg-gray-200 rounded w-1/3"></div>
                        </CardHeader>
                        <CardContent>
                            <div className="h-4 bg-gray-200 rounded w-1/2 mb-2"></div>
                            <div className="h-4 bg-gray-200 rounded w-1/4"></div>
                        </CardContent>
                    </Card>
                ))}
            </div>
        )
    }

    if (batches.length === 0) {
        return (
            <Card>
                <CardContent className="text-center py-8">
                    <FileText className="mx-auto h-12 w-12 text-gray-400 mb-4" />
                    <p className="text-muted-foreground">No batches found</p>
                    <p className="text-sm text-muted-foreground mt-1">
                        Upload invoices to create your first batch
                    </p>
                </CardContent>
            </Card>
        )
    }

    return (
        <div className="grid gap-4">
            {batches.map((batch) => {
                const StatusIcon = statusIcons[batch.status]
                const statusColor = statusColors[batch.status]

                return (
                    <Card key={batch.id} className="hover:shadow-md transition-shadow">
                        <CardHeader className="flex flex-row items-center justify-between">
                            <CardTitle className="text-lg">
                                Batch #{batch.id.slice(0, 8)}
                            </CardTitle>
                            <div className={`flex items-center space-x-1 ${statusColor}`}>
                                <StatusIcon className="h-4 w-4" />
                                <span className="text-sm font-medium">{batch.status}</span>
                            </div>
                        </CardHeader>
                        <CardContent>
                            <div className="flex items-center justify-between">
                                <div>
                                    <p className="text-sm text-muted-foreground">
                                        {batch.documentCount} documents
                                    </p>
                                    <p className="text-xs text-muted-foreground mt-1">
                                        Created {new Date(batch.createdAt).toLocaleString()}
                                    </p>
                                </div>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => onSelectBatch(batch)}
                                >
                                    View Details
                                </Button>
                            </div>
                        </CardContent>
                    </Card>
                )
            })}
        </div>
    )
}

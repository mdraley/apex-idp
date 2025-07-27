'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import NavMenu from '@/components/layout/NavMenu'
import BatchList from '@/components/invoices/BatchList'
import InvoiceViewer from '@/components/invoices/InvoiceViewer'
import { api } from '@/lib/api'
import { Batch } from '@/types'

export default function InvoicesPage() {
    const [selectedBatch, setSelectedBatch] = useState<Batch | null>(null)

    const { data: batches, isLoading } = useQuery({
        queryKey: ['batches'],
        queryFn: async () => {
            const response = await api.get('/batches')
            return response.data
        },
    })

    return (
        <div className="flex h-screen bg-gray-50">
            <NavMenu />
            <div className="flex-1 overflow-y-auto">
                <div className="p-8">
                    <h1 className="text-3xl font-bold mb-8">Invoices</h1>

                    {selectedBatch ? (
                        <InvoiceViewer
                            batch={selectedBatch}
                            onClose={() => setSelectedBatch(null)}
                        />
                    ) : (
                        <BatchList
                            batches={batches || []}
                            isLoading={isLoading}
                            onSelectBatch={setSelectedBatch}
                        />
                    )}
                </div>
            </div>
        </div>
    )
}

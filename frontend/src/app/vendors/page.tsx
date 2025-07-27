'use client'

import { useQuery } from '@tanstack/react-query'
import NavMenu from '@/components/layout/NavMenu'
import VendorList from '@/components/vendors/VendorList'
import { api } from '@/lib/api'

export default function VendorsPage() {
    const { data: vendors, isLoading } = useQuery({
        queryKey: ['vendors'],
        queryFn: async () => {
            const response = await api.get('/vendors')
            return response.data
        },
    })

    return (
        <div className="flex h-screen bg-gray-50">
            <NavMenu />
            <div className="flex-1 overflow-y-auto">
                <div className="p-8">
                    <h1 className="text-3xl font-bold mb-8">Vendors</h1>
                    <VendorList vendors={vendors || []} isLoading={isLoading} />
                </div>
            </div>
        </div>
    )
}

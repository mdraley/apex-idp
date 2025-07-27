import { Building2 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Vendor } from '@/types'

interface VendorListProps {
    vendors: Vendor[]
    isLoading: boolean
}

export default function VendorList({ vendors, isLoading }: VendorListProps) {
    if (isLoading) {
        return (
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                {[...Array(6)].map((_, i) => (
                    <Card key={i} className="animate-pulse">
                        <CardHeader>
                            <div className="h-6 bg-gray-200 rounded w-3/4"></div>
                        </CardHeader>
                        <CardContent>
                            <div className="space-y-2">
                                <div className="h-4 bg-gray-200 rounded w-full"></div>
                                <div className="h-4 bg-gray-200 rounded w-2/3"></div>
                            </div>
                        </CardContent>
                    </Card>
                ))}
            </div>
        )
    }

    if (vendors.length === 0) {
        return (
            <Card>
                <CardContent className="text-center py-12">
                    <Building2 className="mx-auto h-12 w-12 text-gray-400 mb-4" />
                    <p className="text-muted-foreground">No vendors found</p>
                    <p className="text-sm text-muted-foreground mt-1">
                        Vendors will appear here as invoices are processed
                    </p>
                </CardContent>
            </Card>
        )
    }

    return (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {vendors.map((vendor) => (
                <Card key={vendor.id} className="hover:shadow-md transition-shadow">
                    <CardHeader>
                        <CardTitle className="flex items-center space-x-2">
                            <Building2 className="h-5 w-5" />
                            <span>{vendor.name}</span>
                        </CardTitle>
                    </CardHeader>
                    <CardContent>
                        <dl className="space-y-1 text-sm">
                            <div>
                                <dt className="text-muted-foreground">Email</dt>
                                <dd className="font-medium">{vendor.email || 'N/A'}</dd>
                            </div>
                            <div>
                                <dt className="text-muted-foreground">Phone</dt>
                                <dd className="font-medium">{vendor.phone || 'N/A'}</dd>
                            </div>
                            <div>
                                <dt className="text-muted-foreground">Total Invoices</dt>
                                <dd className="font-medium">{vendor.invoiceCount || 0}</dd>
                            </div>
                            <div>
                                <dt className="text-muted-foreground">Status</dt>
                                <dd>
                  <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                      vendor.status === 'ACTIVE'
                          ? 'bg-green-100 text-green-800'
                          : 'bg-gray-100 text-gray-800'
                  }`}>
                    {vendor.status}
                  </span>
                                </dd>
                            </div>
                        </dl>
                    </CardContent>
                </Card>
            ))}
        </div>
    )
}

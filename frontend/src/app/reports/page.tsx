'use client'

import NavMenu from '@/components/layout/NavMenu'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export default function ReportsPage() {
    return (
        <div className="flex h-screen bg-gray-50">
            <NavMenu />
            <div className="flex-1 overflow-y-auto">
                <div className="p-8">
                    <h1 className="text-3xl font-bold mb-8">Reports</h1>

                    <Card>
                        <CardHeader>
                            <CardTitle>Reports Coming Soon</CardTitle>
                            <CardDescription>
                                Advanced reporting and analytics features will be available in the next release.
                            </CardDescription>
                        </CardHeader>
                        <CardContent>
                            <p className="text-muted-foreground">
                                This section will include:
                            </p>
                            <ul className="list-disc list-inside mt-2 space-y-1 text-muted-foreground">
                                <li>Invoice processing trends</li>
                                <li>Vendor payment analytics</li>
                                <li>Monthly/quarterly summaries</li>
                                <li>Export to CPSI integration reports</li>
                            </ul>
                        </CardContent>
                    </Card>
                </div>
            </div>
        </div>
    )
}

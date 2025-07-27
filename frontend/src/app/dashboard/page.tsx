'use client'

import { useEffect, useState } from 'react'
import { FileUp, FileText, Building2, Activity } from 'lucide-react'
import NavMenu from '@/components/layout/NavMenu'
import StatsCard from '@/components/dashboard/StatsCard'
import RecentActivity from '@/components/dashboard/RecentActivity'
import UploadDropzone from '@/components/invoices/UploadDropzone'
import { useWebSocket } from '@/hooks/useWebSocket'
import { api } from '@/lib/api'

export default function DashboardPage() {
    const [stats, setStats] = useState({
        totalVendors: 0,
        totalInvoices: 0,
        processingBatches: 0,
        todayActivity: 0,
    })

    const { subscribe, unsubscribe } = useWebSocket()

    useEffect(() => {
        // Fetch initial stats
        const fetchStats = async () => {
            try {
                const [vendorsRes, invoicesRes] = await Promise.all([
                    api.get('/vendors/count'),
                    api.get('/invoices/count'),
                ])

                setStats(prev => ({
                    ...prev,
                    totalVendors: vendorsRes.data.count,
                    totalInvoices: invoicesRes.data.count,
                }))
            } catch (error) {
                console.error('Failed to fetch stats:', error)
            }
        }

        fetchStats()

        // Subscribe to real-time updates
        const unsubscribeInvoices = subscribe('/topic/invoices/count', (message) => {
            setStats(prev => ({
                ...prev,
                totalInvoices: message.count,
            }))
        })

        const unsubscribeVendors = subscribe('/topic/vendors/count', (message) => {
            setStats(prev => ({
                ...prev,
                totalVendors: message.count,
            }))
        })

        return () => {
            unsubscribeInvoices()
            unsubscribeVendors()
        }
    }, [subscribe, unsubscribe])

    return (
        <div className="flex h-screen bg-gray-50">
            <NavMenu />
            <div className="flex-1 overflow-y-auto">
                <div className="p-8">
                    <h1 className="text-3xl font-bold mb-8">Dashboard</h1>

                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
                        <StatsCard
                            title="Total Vendors"
                            value={stats.totalVendors}
                            icon={Building2}
                            trend="+12%"
                        />
                        <StatsCard
                            title="Total Invoices"
                            value={stats.totalInvoices}
                            icon={FileText}
                            trend="+23%"
                        />
                        <StatsCard
                            title="Processing"
                            value={stats.processingBatches}
                            icon={FileUp}
                        />
                        <StatsCard
                            title="Today's Activity"
                            value={stats.todayActivity}
                            icon={Activity}
                        />
                    </div>

                    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                        <div className="bg-white rounded-lg shadow p-6">
                            <h2 className="text-xl font-semibold mb-4">Upload Invoices</h2>
                            <UploadDropzone />
                        </div>

                        <div className="bg-white rounded-lg shadow p-6">
                            <h2 className="text-xl font-semibold mb-4">Recent Activity</h2>
                            <RecentActivity />
                        </div>
                    </div>
                </div>
            </div>
        </div>
    )
}

import { useQuery } from '@tanstack/react-query'
import { FileText, CheckCircle, Clock, AlertCircle } from 'lucide-react'
import { api } from '@/lib/api'

interface Activity {
    id: string
    type: 'upload' | 'processed' | 'error'
    description: string
    timestamp: string
}

const iconMap = {
    upload: Clock,
    processed: CheckCircle,
    error: AlertCircle,
}

export default function RecentActivity() {
    const { data: activities, isLoading } = useQuery({
        queryKey: ['recent-activity'],
        queryFn: async () => {
            const response = await api.get('/activity/recent')
            return response.data
        },
        refetchInterval: 30000, // Refresh every 30 seconds
    })

    if (isLoading) {
        return (
            <div className="space-y-4">
                {[...Array(3)].map((_, i) => (
                    <div key={i} className="animate-pulse">
                        <div className="flex items-center space-x-3">
                            <div className="h-8 w-8 bg-gray-200 rounded-full"></div>
                            <div className="flex-1">
                                <div className="h-4 bg-gray-200 rounded w-3/4 mb-1"></div>
                                <div className="h-3 bg-gray-200 rounded w-1/2"></div>
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        )
    }

    const activityList = activities || []

    return (
        <div className="space-y-4">
            {activityList.length === 0 ? (
                <p className="text-center text-muted-foreground py-8">No recent activity</p>
            ) : (
                activityList.map((activity: Activity) => {
                    const Icon = iconMap[activity.type]
                    return (
                        <div key={activity.id} className="flex items-center space-x-3">
                            <div className={`p-2 rounded-full ${
                                activity.type === 'error' ? 'bg-red-100' :
                                    activity.type === 'processed' ? 'bg-green-100' : 'bg-blue-100'
                            }`}>
                                <Icon className={`h-4 w-4 ${
                                    activity.type === 'error' ? 'text-red-600' :
                                        activity.type === 'processed' ? 'text-green-600' : 'text-blue-600'
                                }`} />
                            </div>
                            <div className="flex-1">
                                <p className="text-sm font-medium">{activity.description}</p>
                                <p className="text-xs text-muted-foreground">
                                    {new Date(activity.timestamp).toLocaleString()}
                                </p>
                            </div>
                        </div>
                    )
                })
            )}
        </div>
    )
}

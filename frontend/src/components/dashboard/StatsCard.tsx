import { LucideIcon } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

interface StatsCardProps {
    title: string
    value: number
    icon: LucideIcon
    trend?: string
}

export default function StatsCard({ title, value, icon: Icon, trend }: StatsCardProps) {
    return (
        <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{title}</CardTitle>
                <Icon className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
                <div className="text-2xl font-bold">{value.toLocaleString()}</div>
                {trend && (
                    <p className="text-xs text-muted-foreground">
                        <span className="text-green-500">{trend}</span> from last month
                    </p>
                )}
            </CardContent>
        </Card>
    )
}

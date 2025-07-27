'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { Home, FileText, Building2, BarChart3, LogOut } from 'lucide-react'
import { cn } from '@/lib/utils'
import useAuthStore from '@/store/authStore'

const navigation = [
    { name: 'Dashboard', href: '/dashboard', icon: Home },
    { name: 'Invoices', href: '/invoices', icon: FileText },
    { name: 'Vendors', href: '/vendors', icon: Building2 },
    { name: 'Reports', href: '/reports', icon: BarChart3 },
]

export default function NavMenu() {
    const pathname = usePathname()
    const router = useRouter()
    const logout = useAuthStore((state) => state.logout)

    const handleLogout = () => {
        logout()
        router.push('/login')
    }

    return (
        <div className="flex h-full w-64 flex-col bg-gray-900">
            <div className="flex h-16 items-center justify-center bg-gray-800">
                <h1 className="text-2xl font-bold text-white">APEX</h1>
            </div>
            <nav className="flex-1 space-y-1 px-2 py-4">
                {navigation.map((item) => {
                    const isActive = pathname === item.href
                    return (
                        <Link
                            key={item.name}
                            href={item.href}
                            className={cn(
                                'group flex items-center px-2 py-2 text-sm font-medium rounded-md',
                                isActive
                                    ? 'bg-gray-800 text-white'
                                    : 'text-gray-300 hover:bg-gray-700 hover:text-white'
                            )}
                        >
                            <item.icon
                                className={cn(
                                    'mr-3 h-6 w-6',
                                    isActive ? 'text-white' : 'text-gray-400 group-hover:text-gray-300'
                                )}
                            />
                            {item.name}
                        </Link>
                    )
                })}
            </nav>
            <div className="p-4">
                <button
                    onClick={handleLogout}
                    className="group flex w-full items-center px-2 py-2 text-sm font-medium rounded-md text-gray-300 hover:bg-gray-700 hover:text-white"
                >
                    <LogOut className="mr-3 h-6 w-6 text-gray-400 group-hover:text-gray-300" />
                    Logout
                </button>
            </div>
        </div>
    )
}

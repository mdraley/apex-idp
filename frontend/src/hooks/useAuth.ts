import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import useAuthStore from '@/store/authStore'

export function useAuth(requireAuth: boolean = true) {
    const router = useRouter()
    const { isAuthenticated, token, checkAuth } = useAuthStore()

    useEffect(() => {
        checkAuth()

        if (requireAuth && !isAuthenticated) {
            router.push('/login')
        }
    }, [isAuthenticated, requireAuth, router, checkAuth])

    return { isAuthenticated, token }
}

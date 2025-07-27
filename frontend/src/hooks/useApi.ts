import { useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { api } from '@/lib/api'
import useAuthStore from '@/store/authStore'
import { useToast } from '@/components/ui/use-toast'

export function useApi() {
    const router = useRouter()
    const { toast } = useToast()
    const logout = useAuthStore((state) => state.logout)

    const handleError = useCallback(
        (error: any) => {
            if (error.response?.status === 401) {
                logout()
                router.push('/login')
                toast({
                    title: 'Session expired',
                    description: 'Please login again',
                    variant: 'destructive',
                })
            } else if (error.response?.data?.message) {
                toast({
                    title: 'Error',
                    description: error.response.data.message,
                    variant: 'destructive',
                })
            } else {
                toast({
                    title: 'Error',
                    description: 'An unexpected error occurred',
                    variant: 'destructive',
                })
            }
        },
        [logout, router, toast]
    )

    const apiCall = useCallback(
        async <T = any>(
            method: 'get' | 'post' | 'put' | 'delete',
            url: string,
            data?: any,
            config?: any
        ): Promise<T> => {
            try {
                const response = await api[method](url, data, config)
                return response.data
            } catch (error) {
                handleError(error)
                throw error
            }
        },
        [handleError]
    )

    return {
        get: <T = any>(url: string, config?: any) => apiCall<T>('get', url, undefined, config),
        post: <T = any>(url: string, data?: any, config?: any) => apiCall<T>('post', url, data, config),
        put: <T = any>(url: string, data?: any, config?: any) => apiCall<T>('put', url, data, config),
        delete: <T = any>(url: string, config?: any) => apiCall<T>('delete', url, undefined, config),
    }
}

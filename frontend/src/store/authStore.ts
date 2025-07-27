import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface User {
    id: string
    username: string
    email: string
    role: string
}

interface AuthState {
    token: string | null
    user: User | null
    isAuthenticated: boolean
    login: (token: string, user: User) => void
    logout: () => void
    checkAuth: () => void
}

const useAuthStore = create<AuthState>()(
    persist(
        (set) => ({
            token: null,
            user: null,
            isAuthenticated: false,

            login: (token: string, user: User) => {
                set({ token, user, isAuthenticated: true })
            },

            logout: () => {
                set({ token: null, user: null, isAuthenticated: false })
            },

            checkAuth: () => {
                const token = localStorage.getItem('token')
                const userStr = localStorage.getItem('user')

                if (token && userStr) {
                    try {
                        const user = JSON.parse(userStr)
                        set({ token, user, isAuthenticated: true })
                    } catch {
                        set({ token: null, user: null, isAuthenticated: false })
                    }
                } else {
                    set({ token: null, user: null, isAuthenticated: false })
                }
            },
        }),
        {
            name: 'auth-storage',
            partialize: (state) => ({ token: state.token, user: state.user }),
        }
    )
)

export default useAuthStore

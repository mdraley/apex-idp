import { useEffect, useRef, useCallback } from 'react'
import { Client, IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import useAuthStore from '@/store/authStore'

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || 'ws://localhost:8080/ws'

export function useWebSocket() {
    const clientRef = useRef<Client | null>(null)
    const subscriptionsRef = useRef<Map<string, any>>(new Map())
    const token = useAuthStore((state) => state.token)

    useEffect(() => {
        if (!token) return

        const client = new Client({
            webSocketFactory: () => new SockJS(WS_URL),
            connectHeaders: {
                Authorization: `Bearer ${token}`,
            },
            debug: (str) => {
                console.log('STOMP:', str)
            },
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
        })

        client.onConnect = () => {
            console.log('WebSocket connected')
        }

        client.onStompError = (frame) => {
            console.error('STOMP error:', frame.headers['message'])
        }

        client.activate()
        clientRef.current = client

        return () => {
            subscriptionsRef.current.forEach((subscription) => {
                subscription.unsubscribe()
            })
            subscriptionsRef.current.clear()
            client.deactivate()
        }
    }, [token])

    const subscribe = useCallback((destination: string, callback: (message: any) => void) => {
        if (!clientRef.current || !clientRef.current.connected) {
            console.warn('WebSocket not connected')
            return () => {}
        }

        const subscription = clientRef.current.subscribe(destination, (message: IMessage) => {
            try {
                const data = JSON.parse(message.body)
                callback(data)
            } catch (error) {
                console.error('Failed to parse WebSocket message:', error)
            }
        })

        subscriptionsRef.current.set(destination, subscription)

        return () => {
            subscription.unsubscribe()
            subscriptionsRef.current.delete(destination)
        }
    }, [])

    const unsubscribe = useCallback((destination: string) => {
        const subscription = subscriptionsRef.current.get(destination)
        if (subscription) {
            subscription.unsubscribe()
            subscriptionsRef.current.delete(destination)
        }
    }, [])

    const send = useCallback((destination: string, body: any) => {
        if (!clientRef.current || !clientRef.current.connected) {
            console.warn('WebSocket not connected')
            return
        }

        clientRef.current.publish({
            destination,
            body: JSON.stringify(body),
        })
    }, [])

    return { subscribe, unsubscribe, send }
}

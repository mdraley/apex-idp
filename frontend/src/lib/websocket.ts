import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || 'ws://localhost:8080/ws'

class WebSocketService {
    private client: Client | null = null
    private subscriptions: Map<string, any> = new Map()

    connect(token: string): Promise<void> {
        return new Promise((resolve, reject) => {
            this.client = new Client({
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
                onConnect: () => {
                    console.log('WebSocket connected')
                    resolve()
                },
                onStompError: (frame) => {
                    console.error('STOMP error:', frame)
                    reject(new Error('WebSocket connection failed'))
                },
            })

            this.client.activate()
        })
    }

    disconnect(): void {
        this.subscriptions.forEach((subscription) => {
            subscription.unsubscribe()
        })
        this.subscriptions.clear()

        if (this.client) {
            this.client.deactivate()
            this.client = null
        }
    }

    subscribe(destination: string, callback: (message: any) => void): () => void {
        if (!this.client || !this.client.connected) {
            throw new Error('WebSocket not connected')
        }

        const subscription = this.client.subscribe(destination, (message) => {
            try {
                const data = JSON.parse(message.body)
                callback(data)
            } catch (error) {
                console.error('Failed to parse message:', error)
            }
        })

        this.subscriptions.set(destination, subscription)

        return () => {
            subscription.unsubscribe()
            this.subscriptions.delete(destination)
        }
    }

    send(destination: string, body: any): void {
        if (!this.client || !this.client.connected) {
            throw new Error('WebSocket not connected')
        }

        this.client.publish({
            destination,
            body: JSON.stringify(body),
        })
    }
}

export const wsService = new WebSocketService()

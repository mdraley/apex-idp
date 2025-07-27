import * as React from 'react'
import { useEffect, useState } from 'react'

export interface Toast {
    id: string
    title?: string
    description?: string
    action?: React.ReactNode
    variant?: 'default' | 'destructive'
}

interface ToastState {
    toasts: Toast[]
}

// 1. Define a specific Action type for the reducer for type safety
type Action =
    | { type: 'ADD_TOAST'; toast: Toast }
    | { type: 'UPDATE_TOAST'; toast: Partial<Toast> & { id: string } }
    | { type: 'DISMISS_TOAST'; toastId?: string }
    | { type: 'REMOVE_TOAST'; toastId?: string }

const listeners: Array<(state: ToastState) => void> = []
let memoryState: ToastState = { toasts: [] }

function dispatch(action: Action) {
    memoryState = reducer(memoryState, action)
    listeners.forEach((listener) => {
        listener(memoryState)
    })
}

// 2. Use the specific Action type in the reducer instead of 'any'
function reducer(state: ToastState, action: Action): ToastState {
    switch (action.type) {
        case 'ADD_TOAST':
            return {
                ...state,
                toasts: [action.toast, ...state.toasts],
            }

        case 'UPDATE_TOAST':
            return {
                ...state,
                toasts: state.toasts.map((t) =>
                    t.id === action.toast.id ? { ...t, ...action.toast } : t
                ),
            }

        case 'DISMISS_TOAST': {
            const { toastId } = action
            if (toastId) {
                return {
                    ...state,
                    toasts: state.toasts.filter((t) => t.id !== toastId),
                }
            }
            return {
                ...state,
                toasts: [],
            }
        }

        case 'REMOVE_TOAST':
            if (action.toastId === undefined) {
                return {
                    ...state,
                    toasts: [],
                }
            }
            return {
                ...state,
                toasts: state.toasts.filter((t) => t.id !== action.toastId),
            }

        // 3. Add a default case as a best practice for reducers
        default:
            return state
    }
}

let count = 0

function genId() {
    count = (count + 1) % Number.MAX_VALUE
    return count.toString()
}

export function toast(props: Omit<Toast, 'id'>) {
    const id = genId()

    const update = (props: Partial<Toast>) =>
        dispatch({
            type: 'UPDATE_TOAST',
            toast: { ...props, id },
        })
    const dismiss = () => dispatch({ type: 'DISMISS_TOAST', toastId: id })

    dispatch({
        type: 'ADD_TOAST',
        toast: {
            ...props,
            id,
        },
    })

    return {
        id,
        dismiss,
        update,
    }
}

export function useToast() {
    const [state, setState] = useState<ToastState>(memoryState)

    useEffect(() => {
        listeners.push(setState)
        return () => {
            const index = listeners.indexOf(setState)
            if (index > -1) {
                listeners.splice(index, 1)
            }
        }
        // 4. CRITICAL FIX: Change dependency array from [state] to []
    }, []) // An empty array ensures this effect runs only once on mount

    return {
        ...state,
        toast,
        dismiss: (toastId?: string) => dispatch({ type: 'DISMISS_TOAST', toastId }),
    }
}
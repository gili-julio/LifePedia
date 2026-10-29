import { useMemo, useSyncExternalStore } from 'react'
import { formatCompactCurrency, formatCurrency } from './format'

const KEY = 'lifepedia.hideValues'
export const HIDDEN_MONEY = 'R$ ••••'

const listeners = new Set<() => void>()
let hidden = read()

function read() {
  try {
    return localStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}

function notify() {
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  const onStorage = (event: StorageEvent) => {
    if (event.key !== KEY) return
    hidden = read()
    notify()
  }
  window.addEventListener('storage', onStorage)
  return () => {
    listeners.delete(listener)
    window.removeEventListener('storage', onStorage)
  }
}

/** Liga/desliga "ocultar valores"; lembrado no aparelho (só a preferência, nenhum valor). */
export function setHideValues(next: boolean) {
  hidden = next
  try {
    localStorage.setItem(KEY, next ? '1' : '0')
  } catch {
    // sem armazenamento: vale só até recarregar
  }
  notify()
}

export function useHideValues() {
  return useSyncExternalStore(subscribe, () => hidden)
}

/** Formatadores de dinheiro para a tela: com "ocultar valores" ligado, todo valor vira `R$ ••••`. */
export function useMoney() {
  const isHidden = useHideValues()
  return useMemo(() => ({
    hidden: isHidden,
    formatCurrency: (value: number) => (isHidden ? HIDDEN_MONEY : formatCurrency(value)),
    formatCompactCurrency: (value: number) => (isHidden ? HIDDEN_MONEY : formatCompactCurrency(value)),
  }), [isHidden])
}

import { useThemeColor } from '@/lib/pwa'

/** Mantém a cor da barra do sistema igual ao tema do app. */
export function ThemeColor() {
  useThemeColor()
  return null
}

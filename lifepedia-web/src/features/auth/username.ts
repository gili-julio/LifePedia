import { z } from 'zod'

/** Mesma regra da API (`Usernames`): 3 a 30, minúsculas, números, ponto e _ (não nas pontas). */
const USERNAME = /^[a-z0-9][a-z0-9._]{1,28}[a-z0-9]$/

/** Tira o @ e espaços e passa para minúsculas. */
export function normalizeUsername(value: string) {
  return value.trim().replace(/^@/, '').toLowerCase()
}

export const usernameSchema = z
  .string()
  .transform(normalizeUsername)
  .refine((v) => v.length >= 3 && v.length <= 30, 'Use de 3 a 30 caracteres.')
  .refine((v) => USERNAME.test(v), 'Só letras minúsculas, números, ponto ou _ (sem ponto ou _ nas pontas).')

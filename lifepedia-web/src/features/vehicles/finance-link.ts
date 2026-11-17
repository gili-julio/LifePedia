import { useState } from 'react'
import { useCategoryLookup } from '@/features/finance/use-category-lookup'
import type { FinanceLink } from './types'

export type FinanceState = { on: boolean; categoryId: string; groupIds: string[] }

/** Estado inicial: ligado com "Transporte" (ou o que já está no financeiro, ao editar). */
export function useFinanceState(current?: { categoryId: string } | null, editing = false): [FinanceState, (s: FinanceState) => void] {
  const { ofType } = useCategoryLookup()
  const transport = ofType('EXPENSE').find((c) => c.name.toLowerCase() === 'transporte')?.id ?? ''
  return useState<FinanceState>({
    on: editing ? !!current : true,
    categoryId: current?.categoryId ?? transport,
    groupIds: [],
  })
}

export function toFinanceLink(state: FinanceState): FinanceLink | null {
  if (!state.on || !state.categoryId) return null
  return { categoryId: state.categoryId, groupIds: state.groupIds.length ? state.groupIds : undefined }
}

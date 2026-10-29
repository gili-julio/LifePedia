/** Tela de uma nota (pessoal ou do grupo). */
export function noteUrl(groupId: string | null, id: string) {
  return groupId ? `/grupos/${groupId}/notas/${id}` : `/notas/${id}`
}

/** Lista de notas: página Notas ou a aba Notas do grupo. */
export function notesListUrl(groupId: string | null) {
  return groupId ? `/grupos/${groupId}?aba=notas` : '/notas'
}

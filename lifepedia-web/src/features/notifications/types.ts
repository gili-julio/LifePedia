/** Espelha `NotificationType` da API; tipo novo = texto novo em `notification-content.tsx`. */
export type NotificationType =
  | 'GROUP_INVITE'
  | 'MEMBER_ACCOUNT_DELETED'
  | 'TASK_ASSIGNED'
  | 'TASK_TURN'
  | 'TASK_COMPLETED'
  | 'TASK_COMMENT'
  | 'TASK_OVERDUE'
  | 'TASK_REMINDER'

export type AppNotification = {
  id: string
  type: NotificationType
  /** Parâmetros do texto, gravados no envio */
  data: Record<string, string>
  groupId: string | null
  /** Imagem do grupo, se houver */
  imageUrl: string | null
  read: boolean
  createdAt: string
}

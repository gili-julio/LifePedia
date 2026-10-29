/** Espelha `AttendanceDtos.DayStatus`: marcado, pendente (já aconteceu sem marcação) ou ainda por vir. */
export type DayStatus = 'PRESENT' | 'ABSENT' | 'CANCELED' | 'PENDING' | 'UPCOMING'
/** Espelha `CommitmentMark.Mark`. */
export type Mark = 'PRESENT' | 'ABSENT' | 'CANCELED'

export type LinkedTask = { id: string; title: string; dueTime: string | null; repeating: boolean }

/** `remaining`: faltas que ainda cabem no limite (negativo = passou). */
export type AttendanceStats = { presences: number; absences: number; pending: number; canceled: number; remaining: number }

export type Commitment = {
  id: string
  name: string
  color: string
  /** 1 = segunda ... 7 = domingo */
  weekdays: number[]
  time: string
  durationMinutes: number
  startDate: string
  endDate: string | null
  maxAbsences: number
  task: LinkedTask | null
  stats: AttendanceStats
}

export type CommitmentRequest = Omit<Commitment, 'id' | 'task' | 'stats'> & { taskId: string | null }

/** `fromTask`: presença vinda da tarefa ligada concluída. */
export type CommitmentDay = { date: string; status: DayStatus; fromTask: boolean }

/** Dia de um compromisso na agenda, com as faltas do compromisso inteiro. */
export type AttendanceAgendaItem = {
  commitmentId: string
  name: string
  color: string
  date: string
  time: string
  durationMinutes: number
  status: DayStatus
  fromTask: boolean
  taskId: string | null
  absences: number
  maxAbsences: number
}

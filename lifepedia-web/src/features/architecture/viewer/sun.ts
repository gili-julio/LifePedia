/** Posição do sol (aproximação simples, hora solar): altura e azimute em graus (azimute a partir do norte, sentido horário). */
export function sunPosition(latitude: number, dayOfYear: number, hour: number) {
  const rad = Math.PI / 180
  const declination = 23.44 * Math.sin((2 * Math.PI * (284 + dayOfYear)) / 365) * rad
  const hourAngle = 15 * (hour - 12) * rad
  const lat = latitude * rad
  const sinAlt = Math.sin(lat) * Math.sin(declination) + Math.cos(lat) * Math.cos(declination) * Math.cos(hourAngle)
  const altitude = Math.asin(Math.max(-1, Math.min(1, sinAlt)))
  const cosAz = (Math.sin(declination) - Math.sin(altitude) * Math.sin(lat)) / (Math.cos(altitude) * Math.cos(lat) || 1e-9)
  let azimuth = Math.acos(Math.max(-1, Math.min(1, cosAz))) / rad
  if (hourAngle > 0) azimuth = 360 - azimuth
  return { elevation: altitude / rad, azimuth }
}

/**
 * Direção para o sol no espaço da maquete (y para cima). {@code north} = quantos graus o norte está girado, no
 * sentido horário, a partir do "fundo" da maquete (-z).
 */
export function sunDirection(elevation: number, azimuth: number, north: number): [number, number, number] {
  const rad = Math.PI / 180
  const theta = (azimuth + north) * rad
  const alt = elevation * rad
  return [Math.cos(alt) * Math.sin(theta), Math.sin(alt), -Math.cos(alt) * Math.cos(theta)]
}

/** Dia do ano (1 a 365) a partir de mês (1 a 12) e dia. */
export function dayOfYear(month: number, day: number) {
  const days = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  return days.slice(0, month - 1).reduce((sum, d) => sum + d, 0) + day
}

/** Latitude usada quando o projeto não informa (Brasília). */
export const DEFAULT_LATITUDE = -15.8

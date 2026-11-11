import { useEffect, useRef, useState } from 'react'
import { ApiError, apiBinary } from '@/lib/api'
import { ModelViewer } from './ModelViewer'

type Callbacks = ConstructorParameters<typeof ModelViewer>[1]

/**
 * Cria o visualizador no elemento e abre a maquete: de uma URL da API (com progresso) ou de um GLB já em memória
 * (prévia da conversão). Troca de maquete recria o visualizador.
 */
export function useModelViewer(source: string | Uint8Array | null, callbacks: Callbacks = {}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const callbacksRef = useRef(callbacks)
  const [viewer, setViewer] = useState<ModelViewer | null>(null)
  const [progress, setProgress] = useState<number | null>(0)
  const [error, setError] = useState<string | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    callbacksRef.current = callbacks
  })

  useEffect(() => {
    const container = containerRef.current
    if (!container || !source) return
    let cancelled = false
    let instance: ModelViewer
    try {
      instance = new ModelViewer(container, {
        onPick: (tool, hit) => callbacksRef.current?.onPick?.(tool, hit),
        onMiss: (reason) => callbacksRef.current?.onMiss?.(reason),
        onPinClick: (id) => callbacksRef.current?.onPinClick?.(id),
        onChange: () => callbacksRef.current?.onChange?.(),
        onToolChange: (tool) => callbacksRef.current?.onToolChange?.(tool),
      })
    } catch {
      queueMicrotask(() => setError('Este navegador não consegue mostrar 3D (WebGL desligado).'))
      return
    }
    queueMicrotask(() => {
      if (cancelled) return
      setViewer(instance)
      setReady(false)
      setError(null)
      setProgress(0)
    })
    const load = async () => {
      const buffer = typeof source === 'string' ? await apiBinary(source, (p) => !cancelled && setProgress(p))
        : (source.buffer.slice(source.byteOffset, source.byteOffset + source.byteLength) as ArrayBuffer)
      if (cancelled) return
      await instance.load(buffer)
      if (!cancelled) setReady(true)
    }
    load().catch((e) => {
      if (!cancelled) setError(e instanceof ApiError ? e.message : 'Não foi possível abrir a maquete.')
    })
    return () => {
      cancelled = true
      instance.dispose()
      setViewer(null)
    }
  }, [source])

  return { containerRef, viewer: ready ? viewer : null, progress, error }
}

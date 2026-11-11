import { useRef, useState } from 'react'

type Props = { onMove: (x: number, y: number) => void }

const RADIUS = 44

/** Joystick na tela (celular) para andar no modo caminhar; olhar é arrastando no resto da tela. */
export function WalkJoystick({ onMove }: Props) {
  const baseRef = useRef<HTMLDivElement>(null)
  const [knob, setKnob] = useState({ x: 0, y: 0 })

  function update(e: React.PointerEvent) {
    const rect = baseRef.current!.getBoundingClientRect()
    let x = e.clientX - (rect.left + rect.width / 2)
    let y = e.clientY - (rect.top + rect.height / 2)
    const length = Math.hypot(x, y)
    if (length > RADIUS) {
      x = (x / length) * RADIUS
      y = (y / length) * RADIUS
    }
    setKnob({ x, y })
    onMove(x / RADIUS, -y / RADIUS)
  }

  function stop() {
    setKnob({ x: 0, y: 0 })
    onMove(0, 0)
  }

  return (
    <div ref={baseRef} role="presentation"
      className="absolute bottom-4 left-4 z-10 flex size-28 touch-none items-center justify-center rounded-full border border-white/40 bg-black/25 backdrop-blur-sm"
      onPointerDown={(e) => {
        e.currentTarget.setPointerCapture(e.pointerId)
        update(e)
      }}
      onPointerMove={(e) => e.buttons > 0 && update(e)}
      onPointerUp={stop}
      onPointerCancel={stop}>
      <div className="size-12 rounded-full bg-white/80 shadow" style={{ transform: `translate(${knob.x}px, ${knob.y}px)` }} />
    </div>
  )
}

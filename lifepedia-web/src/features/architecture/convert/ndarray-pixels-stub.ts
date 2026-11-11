/**
 * Substitui `ndarray-pixels` (via alias no `vite.config.ts`): o pacote gera código com `new Function` ao carregar, o que
 * a CSP barra. Ele só serve ao `textureCompress` do gltf-transform, que não é usado (as texturas viram WebP no canvas).
 */
function unavailable(): never {
  throw new Error('ndarray-pixels não está disponível neste build.')
}

export const getPixels = unavailable
export const savePixels = unavailable

import { type Accessor, type Document, type Material as GltfMaterial, type Primitive, type TextureInfo, WebIO } from '@gltf-transform/core'
import { ALL_EXTENSIONS, EXTTextureWebP, KHRTextureTransform } from '@gltf-transform/extensions'
import { dedup, meshopt, prune, weld } from '@gltf-transform/functions'
import { unzipSync } from 'fflate'
import { MeshoptDecoder, MeshoptEncoder } from 'meshoptimizer'
import {
  Box3,
  DoubleSide,
  Group,
  LineBasicMaterial,
  LineSegments,
  LoadingManager,
  type Material,
  Mesh,
  MeshStandardMaterial,
  type Object3D,
  type Texture,
  Vector3,
} from 'three'
import { GLTFExporter } from 'three/addons/exporters/GLTFExporter.js'
import { ColladaLoader } from 'three/addons/loaders/ColladaLoader.js'
import { FBXLoader } from 'three/addons/loaders/FBXLoader.js'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { MTLLoader } from 'three/addons/loaders/MTLLoader.js'
import { OBJLoader } from 'three/addons/loaders/OBJLoader.js'
import { MeshoptDecoder as ThreeMeshoptDecoder } from 'three/addons/libs/meshopt_decoder.module.js'
import { type ConvertedModel, ConvertError, type LoadedModel, MAX_TEXTURE, type Stage, type Unit, UNITS } from './options'

const MODEL_ORDER = ['glb', 'gltf', 'dae', 'fbx', 'obj'] as const
type ModelExt = (typeof MODEL_ORDER)[number]

/** Lê o que a pessoa escolheu (arquivos soltos ou .zip) e monta o modelo no three.js. */
export async function loadModel(files: File[], onStage: (stage: Stage) => void): Promise<LoadedModel> {
  onStage('reading')
  const byName = await collect(files)
  const main = pickMain(byName)
  const sourceBytes = files.reduce((sum, f) => sum + f.size, 0)
  const urls = new Map<string, string>()
  const urlOf = (name: string) => {
    const blob = byName.get(name)
    if (!blob) return null
    if (!urls.has(name)) urls.set(name, URL.createObjectURL(blob))
    return urls.get(name)!
  }
  const manager = new LoadingManager()
  const counts = { loaded: 0, total: 0 }
  // Conta cada pedido (texturas, .bin) para esperar todos, mesmo quando o modelo é lido de texto sem passar pelo manager.
  const itemStart = manager.itemStart.bind(manager)
  const itemEnd = manager.itemEnd.bind(manager)
  manager.itemStart = (url) => {
    counts.total++
    itemStart(url)
  }
  manager.itemEnd = (url) => {
    counts.loaded++
    itemEnd(url)
  }
  const missing = new Set<string>()
  const names = new Map<string, string>()
  manager.onError = (url) => {
    const name = names.get(url)
    if (name) missing.add(name)
  }
  // Texturas e .bin pedidos pelo modelo vêm dos arquivos escolhidos (pelo nome); nada é buscado fora.
  manager.setURLModifier((url) => {
    // Caminho relativo a um blob (ex.: textura do .mtl) chega como "blob:<origem>/piso.png": resolve pelo nome.
    if (url.startsWith('data:') || [...urls.values()].includes(url)) return url
    const name = baseName(url)
    const found = urlOf(name)
    if (!found) {
      missing.add(name)
      return 'data:,'
    }
    names.set(found, name)
    return found
  })

  onStage('building')
  try {
    const mainUrl = urlOf(main.name)!
    const { object, declared } = await load(main.ext, mainUrl, manager, byName, urlOf)
    await texturesLoaded(counts)
    standardize(object)
    object.updateMatrixWorld(true)
    const box = new Box3().setFromObject(object)
    if (box.isEmpty()) throw new ConvertError('O arquivo não tem nenhuma forma para mostrar.')
    const size = box.getSize(new Vector3())
    return {
      object,
      sourceName: files.find((f) => baseName(f.name) === main.name)?.name ?? files[0].name,
      sourceBytes,
      unit: declared ? 'm' : guessUnit(Math.max(size.x, size.y, size.z)),
      rawSize: [size.x, size.y, size.z],
      triangles: countTriangles(object),
      missing: [...missing].sort(),
    }
  } finally {
    // Dá tempo das texturas terminarem de carregar antes de liberar as URLs.
    setTimeout(() => urls.forEach((u) => URL.revokeObjectURL(u)), 30_000)
  }
}

/**
 * Aplica unidade e posição (deitado → em pé), põe o chão em y = 0 e o centro em x/z = 0, exporta GLB e comprime:
 * texturas em WebP (até 2048 px) e geometria com meshopt.
 */
export async function convertModel(model: LoadedModel, unit: Unit, upright: boolean,
  onStage: (stage: Stage) => void): Promise<ConvertedModel> {
  // Cópia: dá para voltar e converter de novo com outra unidade sem acumular escala.
  const object = model.object.clone()
  const root = placed(object, unit, upright)
  onStage('textures')
  const exported = await new GLTFExporter().parseAsync(root, { binary: true, onlyVisible: true, maxTextureSize: MAX_TEXTURE })
  root.remove(object)
  await MeshoptEncoder.ready
  await MeshoptDecoder.ready
  const io = new WebIO().registerExtensions(ALL_EXTENSIONS)
    .registerDependencies({ 'meshopt.encoder': MeshoptEncoder, 'meshopt.decoder': MeshoptDecoder })
  const doc = await io.readBinary(new Uint8Array(exported as ArrayBuffer))
  await texturesToWebp(doc)
  onStage('geometry')
  await doc.transform(dedup(), prune(), weld())
  normalizeTexcoords(doc)
  await doc.transform(meshopt({ encoder: MeshoptEncoder, level: 'high', quantizeTexcoord: 16 }))
  const glb = await io.writeBinary(doc)
  return { glb, sizeBytes: glb.byteLength }
}

/** Grupo com a escala/rotação escolhidas, chão em y = 0 e centralizado. */
function placed(object: Object3D, unit: Unit, upright: boolean) {
  const root = new Group()
  const k = UNITS.find((u) => u.value === unit)!.meters
  object.scale.setScalar(object.scale.x * k)
  if (upright) object.rotation.x -= Math.PI / 2
  root.add(object)
  root.updateMatrixWorld(true)
  const box = new Box3().setFromObject(object)
  const center = box.getCenter(new Vector3())
  object.position.sub(new Vector3(center.x, box.min.y, center.z))
  root.updateMatrixWorld(true)
  return root
}

async function collect(files: File[]) {
  const byName = new Map<string, Blob>()
  for (const file of files) {
    if (file.name.toLowerCase().endsWith('.zip')) {
      let entries: Record<string, Uint8Array>
      try {
        entries = unzipSync(new Uint8Array(await file.arrayBuffer()))
      } catch {
        throw new ConvertError('Não foi possível abrir o .zip.')
      }
      for (const [path, data] of Object.entries(entries)) {
        if (path.endsWith('/') || path.startsWith('__MACOSX')) continue
        byName.set(baseName(path), new Blob([data as Uint8Array<ArrayBuffer>]))
      }
    } else {
      byName.set(baseName(file.name), file)
    }
  }
  return byName
}

function pickMain(byName: Map<string, Blob>): { name: string; ext: ModelExt } {
  for (const ext of MODEL_ORDER) {
    const name = [...byName.keys()].find((n) => n.endsWith(`.${ext}`))
    if (name) return { name, ext }
  }
  if ([...byName.keys()].some((n) => n.endsWith('.skp'))) {
    throw new ConvertError('O arquivo .skp do SketchUp não abre fora dele. No SketchUp, exporte o modelo 3D em COLLADA (.dae) '
      + 'ou glTF (.glb) e envie esse arquivo (com a pasta das texturas, ou tudo num .zip).')
  }
  throw new ConvertError('Nenhum modelo 3D encontrado. Envie .dae, .glb, .gltf, .fbx ou .obj (ou um .zip com ele).')
}

/**
 * O SketchUp exporta cada componente colocado como `instance_N` apontando para a definição ("Porta", "Cadeira"); o
 * ColladaLoader troca o nome da definição pelo da instância. Aqui a instância passa a ter o nome da definição, que é o
 * que o visualizador usa para separar as partes e mostrar a ficha da peça.
 */
export function keepComponentNames(dae: string) {
  const names = new Map<string, string>()
  for (const match of dae.matchAll(/<node id="([^"]+)" name="([^"]*)"/g)) names.set(match[1], match[2])
  return dae.replace(/(<node(?: id="[^"]*")? name=")(instance_?\d*)("[^>]*>\s*(?:<matrix>[^<]*<\/matrix>\s*)?<instance_node url="#([^"]+)")/g,
    (all, start: string, _instance: string, rest: string, id: string) => {
      const name = names.get(id)
      return name && !/^(group|instance|component|componente)[_\s#]*\d*$/i.test(name) ? `${start}${name}${rest}` : all
    })
}

/** Modelo no three.js; {@code declared} = já em metros (o arquivo diz a unidade). */
async function load(ext: ModelExt, url: string, manager: LoadingManager, byName: Map<string, Blob>,
  urlOf: (name: string) => string | null): Promise<{ object: Object3D; declared: boolean }> {
  try {
    switch (ext) {
      case 'glb':
      case 'gltf': {
        const loader = new GLTFLoader(manager).setMeshoptDecoder(ThreeMeshoptDecoder)
        return { object: (await loader.loadAsync(url)).scene, declared: true }
      }
      case 'dae': {
        const text = keepComponentNames(await (await fetch(url)).text())
        const result = new ColladaLoader(manager).parse(text, '')
        if (!result) throw new ConvertError('Arquivo COLLADA inválido.')
        return { object: result.scene, declared: true }
      }
      case 'fbx': {
        const group = await new FBXLoader(manager).loadAsync(url)
        // UnitScaleFactor = centímetros por unidade do arquivo.
        const factor = Number(group.userData.unitScaleFactor)
        if (factor > 0) group.scale.multiplyScalar(factor / 100)
        return { object: group, declared: factor > 0 }
      }
      case 'obj': {
        const loader = new OBJLoader(manager)
        const mtl = await mtlName(byName)
        if (mtl) {
          const materials = await new MTLLoader(manager).loadAsync(urlOf(mtl)!)
          materials.preload()
          loader.setMaterials(materials)
        }
        return { object: await loader.loadAsync(url), declared: false }
      }
    }
  } catch (e) {
    if (e instanceof ConvertError) throw e
    throw new ConvertError(ext === 'glb' || ext === 'gltf'
      ? 'Não foi possível ler o glTF. Se ele usa compressão Draco, exporte de novo sem ela.'
      : 'Não foi possível ler o modelo. Confira se o arquivo exportado está inteiro.')
  }
}

async function mtlName(byName: Map<string, Blob>) {
  const obj = [...byName.keys()].find((n) => n.endsWith('.obj'))
  if (obj) {
    const head = await byName.get(obj)!.slice(0, 64 * 1024).text()
    const match = /^mtllib\s+(.+)$/m.exec(head)
    if (match && byName.has(baseName(match[1].trim()))) return baseName(match[1].trim())
  }
  return [...byName.keys()].find((n) => n.endsWith('.mtl')) ?? null
}

/** Espera as texturas pedidas pelo modelo terminarem de carregar (ou falharem), até 60 s. */
async function texturesLoaded(counts: { loaded: number; total: number }) {
  const deadline = performance.now() + 60_000
  while (counts.loaded < counts.total && performance.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, 50))
  }
}

/** Textura sem imagem válida (arquivo faltando ou formato que o navegador não lê) sai: o exportador não aceita. */
function usableMap(map: Texture | null | undefined) {
  const image = map?.image as { width?: number; height?: number } | undefined
  return map && image && (image.width ?? 0) > 0 && (image.height ?? 0) > 0 ? map : null
}

/** Materiais dos exportadores viram PBR simples, dos dois lados (faces do SketchUp não têm "frente" garantida). */
function standardize(object: Object3D) {
  const cache = new Map<Material, Material>()
  object.traverse((node) => {
    if (node instanceof Mesh) {
      const convert = (m: Material) => {
        if (!cache.has(m)) cache.set(m, toStandard(m))
        return cache.get(m)!
      }
      node.material = Array.isArray(node.material) ? node.material.map(convert) : convert(node.material)
    } else if (node instanceof LineSegments) {
      const old = node.material as Material & { color?: { getHex(): number } }
      node.material = new LineBasicMaterial({ color: old.color?.getHex() ?? 0x222222 })
    }
  })
}

function toStandard(m: Material): Material {
  if (m instanceof MeshStandardMaterial) {
    m.side = DoubleSide
    for (const key of ['map', 'normalMap', 'roughnessMap', 'metalnessMap', 'aoMap', 'emissiveMap', 'alphaMap'] as const) {
      m[key] = usableMap(m[key])
    }
    return m
  }
  const old = m as Material & {
    color?: MeshStandardMaterial['color']; map?: MeshStandardMaterial['map']; vertexColors?: boolean
  }
  const material = new MeshStandardMaterial({
    name: m.name,
    color: old.color?.clone(),
    map: usableMap(old.map),
    opacity: m.opacity,
    transparent: m.transparent || m.opacity < 1,
    alphaTest: m.alphaTest,
    vertexColors: !!old.vertexColors,
    side: DoubleSide,
    roughness: 0.9,
    metalness: 0,
  })
  if (material.map) material.map.colorSpace = 'srgb'
  return material
}

/**
 * O SketchUp repete a textura com UV fora de 0 a 1 (ex.: 0 a 40), e assim o UV não é comprimido (fica em float). Aqui o
 * UV de cada material vai para 0 a 1 e a repetição vira `KHR_texture_transform` no material (o three.js aplica).
 */
function normalizeTexcoords(doc: Document) {
  const extension = doc.createExtension(KHRTextureTransform)
  const byMaterial = new Map<GltfMaterial, Primitive[]>()
  for (const mesh of doc.getRoot().listMeshes()) {
    for (const primitive of mesh.listPrimitives()) {
      const material = primitive.getMaterial()
      if (!material || !primitive.getAttribute('TEXCOORD_0')) continue
      byMaterial.set(material, [...(byMaterial.get(material) ?? []), primitive])
    }
  }
  const owner = new Map<Accessor, GltfMaterial>()
  const uv = [0, 0]
  let used = false
  for (const [material, primitives] of byMaterial) {
    const infos = textureInfos(material)
    if (infos.length === 0) continue
    let minU = Infinity
    let minV = Infinity
    let maxU = -Infinity
    let maxV = -Infinity
    for (const primitive of primitives) {
      const accessor = primitive.getAttribute('TEXCOORD_0')!
      for (let i = 0; i < accessor.getCount(); i++) {
        accessor.getElement(i, uv)
        minU = Math.min(minU, uv[0])
        maxU = Math.max(maxU, uv[0])
        minV = Math.min(minV, uv[1])
        maxV = Math.max(maxV, uv[1])
      }
    }
    if (minU >= 0 && minV >= 0 && maxU <= 1 && maxV <= 1) continue
    const su = Math.max(maxU - minU, 1e-6)
    const sv = Math.max(maxV - minV, 1e-6)
    for (const primitive of primitives) {
      let accessor = primitive.getAttribute('TEXCOORD_0')!
      // Mesmo UV usado por outro material (geometria com vários materiais): cada um fica com a sua cópia.
      if (owner.has(accessor) && owner.get(accessor) !== material) {
        accessor = accessor.clone()
        primitive.setAttribute('TEXCOORD_0', accessor)
      }
      if (owner.get(accessor) === material) continue
      owner.set(accessor, material)
      for (let i = 0; i < accessor.getCount(); i++) {
        accessor.getElement(i, uv)
        accessor.setElement(i, [(uv[0] - minU) / su, (uv[1] - minV) / sv])
      }
    }
    for (const info of infos) {
      info.setExtension('KHR_texture_transform', extension.createTransform().setOffset([minU, minV]).setScale([su, sv]))
    }
    used = true
  }
  if (!used) extension.dispose()
}

function textureInfos(m: GltfMaterial): TextureInfo[] {
  return ([
    [m.getBaseColorTexture(), m.getBaseColorTextureInfo()],
    [m.getNormalTexture(), m.getNormalTextureInfo()],
    [m.getMetallicRoughnessTexture(), m.getMetallicRoughnessTextureInfo()],
    [m.getOcclusionTexture(), m.getOcclusionTextureInfo()],
    [m.getEmissiveTexture(), m.getEmissiveTextureInfo()],
  ] as const).filter(([texture, info]) => texture && info && info.getTexCoord() === 0).map(([, info]) => info!)
}

/** Texturas para WebP (quando o navegador sabe gerar e ficou menor). */
async function texturesToWebp(doc: Document) {
  if (!canEncodeWebp()) return
  let used = false
  for (const texture of doc.getRoot().listTextures()) {
    const image = texture.getImage()
    const type = texture.getMimeType()
    if (!image || type === 'image/webp') continue
    try {
      const bitmap = await createImageBitmap(new Blob([image as Uint8Array<ArrayBuffer>], { type }))
      const canvas = document.createElement('canvas')
      canvas.width = bitmap.width
      canvas.height = bitmap.height
      canvas.getContext('2d')!.drawImage(bitmap, 0, 0)
      bitmap.close()
      const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/webp', 0.82))
      if (!blob || blob.type !== 'image/webp' || blob.size >= image.byteLength) continue
      texture.setImage(new Uint8Array(await blob.arrayBuffer())).setMimeType('image/webp')
      used = true
    } catch {
      // Textura que o navegador não decodifica fica como veio.
    }
  }
  if (used) doc.createExtension(EXTTextureWebP).setRequired(true)
}

function canEncodeWebp() {
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 1
  return canvas.toDataURL('image/webp').startsWith('data:image/webp')
}

function countTriangles(object: Object3D) {
  let total = 0
  object.traverse((node) => {
    if (node instanceof Mesh) {
      const g = node.geometry
      total += (g.index ? g.index.count : g.attributes.position.count) / 3
    }
  })
  return Math.round(total)
}

/** SketchUp exporta OBJ/FBX em várias unidades: o tamanho da casa indica qual. */
function guessUnit(largest: number): Unit {
  if (largest > 2000) return 'mm'
  if (largest > 200) return 'cm'
  return 'm'
}

function baseName(path: string) {
  let name = path
  try {
    name = decodeURIComponent(path)
  } catch {
    // nome com % solto
  }
  return name.split(/[\\/]/).pop()!.split('?')[0].toLowerCase()
}

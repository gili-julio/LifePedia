import {
  Box3,
  BufferAttribute,
  BufferGeometry,
  Group,
  Line,
  LineSegments,
  type Material,
  Matrix3,
  Mesh,
  type Object3D,
  Vector3,
} from 'three'
import { CATEGORY_LABELS, CATEGORY_ORDER, type Category, categoryOf, isGeneric, linkInName, prettyName } from './categories'

/** Peça da maquete (móvel, porta, parede...), para a ficha do objeto. */
export type ModelObject = { id: number; name: string; category: Category; link: string | null; box: Box3 }

/** Dados presos em cada malha juntada. */
export type MergedData = { category: Category; materialIndex: number; objectOf: Uint32Array }

export type Optimized = { layers: Group[]; objects: ModelObject[] }

type Naming = {
  /** Nome original do nó (o GLTFLoader tira pontos e barras do `name`) */
  nameOf: (node: Object3D) => string
  /** Índice do material no GLB (estável por versão; é a chave das variantes) */
  indexOf: (material: Material) => number
}

/** Peça grande demais para ser "um objeto" (ex.: o grupo da casa inteira). */
const MAX_OBJECT_SIZE = 8

/**
 * Junta as malhas por categoria e material (e as arestas por cor), já na posição final. Exportações do SketchUp chegam
 * com dezenas de milhares de malhas (cada componente repetido é uma): cada uma custa uma chamada de desenho por quadro,
 * e é isso que trava, não o número de triângulos. Cada vértice guarda de qual peça veio (`objectOf`).
 */
export function optimizeModel(root: Object3D, naming: Naming): Optimized {
  root.updateMatrixWorld(true)
  const boxes = nodeBoxes(root)
  const objects: ModelObject[] = []
  const objectIds = new Map<Object3D, number>()
  const meshes = new Map<Category, Map<Material, { list: Mesh[]; ids: number[] }>>()
  const lines = new Map<Category, Map<Material, Line[]>>()

  /** `category` e `link` vêm do nó mais de fora que tem (a cama manda na manta que está nela). */
  const visit = (node: Object3D, chain: Object3D[], category: Category | null, link: string | null) => {
    if (!node.visible) return
    if (node instanceof Mesh && !Array.isArray(node.material)) {
      const material = node.material as Material
      const kind = category ?? fallbackCategory(material.name)
      const owner = objectNode(chain, node, naming, boxes)
      let id = objectIds.get(owner)
      if (id == null) {
        id = objects.length
        objects.push({ id, name: displayName(chain, owner, naming), category: kind, link,
          box: boxes.get(owner) ?? new Box3().setFromObject(node) })
        objectIds.set(owner, id)
      }
      const byMaterial = getOrSet(meshes, kind, () => new Map())
      const entry = getOrSet(byMaterial, material, () => ({ list: [], ids: [] }))
      entry.list.push(node)
      entry.ids.push(id)
    } else if (node instanceof Line && !Array.isArray(node.material)) {
      getOrSet(getOrSet(lines, category ?? 'structure', () => new Map()), node.material as Material, () => []).push(node)
    }
    if (node.children.length === 0) return
    const name = node === root ? '' : naming.nameOf(node)
    const next = node === root ? chain : [...chain, node]
    const nextCategory = category ?? (name ? categoryOf([name]) : null)
    const nextLink = link ?? (name ? linkInName(name) : null)
    node.children.forEach((child) => visit(child, next, nextCategory, nextLink))
  }
  visit(root, [], null, null)

  const layers = CATEGORY_ORDER.filter((c) => meshes.has(c) || lines.has(c)).map((category) => {
    const layer = new Group()
    layer.name = CATEGORY_LABELS[category]
    layer.userData.category = category
    meshes.get(category)?.forEach(({ list, ids }, material) => {
      const { geometry, objectOf } = mergeMeshes(list, ids, hasMap(material))
      const mesh = new Mesh(geometry, material)
      mesh.userData = { category, materialIndex: naming.indexOf(material), objectOf } satisfies MergedData
      layer.add(mesh)
    })
    lines.get(category)?.forEach((list, material) => layer.add(new LineSegments(mergeLines(list), material)))
    return layer
  })

  const old = new Set<BufferGeometry>()
  root.traverse((node) => {
    if (node instanceof Mesh || node instanceof Line) old.add(node.geometry)
  })
  old.forEach((g) => g.dispose())
  return { layers, objects }
}

/** Câmera salva no arquivo (no SketchUp, a última vista salva): posição e para onde olha. */
export function savedCamera(root: Object3D): { position: Vector3; direction: Vector3 } | null {
  let found: { position: Vector3; direction: Vector3 } | null = null
  root.updateMatrixWorld(true)
  root.traverse((node) => {
    if (found || !('isPerspectiveCamera' in node)) return
    const position = new Vector3().setFromMatrixPosition(node.matrixWorld)
    const direction = new Vector3(0, 0, -1).transformDirection(node.matrixWorld)
    found = { position, direction }
  })
  return found
}

/**
 * A peça a que a malha pertence: o grupo de fora com nome de verdade e tamanho de objeto; senão o de dentro com nome.
 * Grande demais (o grupo das paredes da casa) vira a própria malha, para a ficha não mostrar a casa inteira.
 */
function objectNode(chain: Object3D[], mesh: Mesh, naming: Naming, boxes: Map<Object3D, Box3>) {
  const size = new Vector3()
  const fits = (node: Object3D) => (boxes.get(node)?.getSize(size).length() ?? Infinity) <= MAX_OBJECT_SIZE
  const named = chain.filter((node) => !isGeneric(naming.nameOf(node)))
  const owner = named.find(fits) ?? named[named.length - 1] ?? chain[chain.length - 1] ?? mesh
  return fits(owner) ? owner : mesh
}

function displayName(chain: Object3D[], owner: Object3D, naming: Naming) {
  const own = prettyName(naming.nameOf(owner))
  if (own && !isGeneric(naming.nameOf(owner))) return own
  for (let i = chain.length - 1; i >= 0; i--) {
    const name = naming.nameOf(chain[i])
    if (!isGeneric(name) && prettyName(name)) return prettyName(name)
  }
  return ''
}

/** Material de vidro ou telha ajuda quando o nome da peça não diz nada. */
function fallbackCategory(materialName: string): Category {
  const category = categoryOf([materialName])
  return category === 'windows' || category === 'roof' ? category : 'structure'
}

/** Caixa (no mundo) de cada nó, somando as malhas de dentro: uma passada só, de baixo para cima. */
function nodeBoxes(root: Object3D) {
  const boxes = new Map<Object3D, Box3>()
  const corner = new Box3()
  const walk = (node: Object3D): Box3 | null => {
    let box: Box3 | null = null
    if (node instanceof Mesh) {
      node.geometry.computeBoundingBox()
      box = corner.copy(node.geometry.boundingBox!).applyMatrix4(node.matrixWorld).clone()
    }
    for (const child of node.children) {
      const inner = walk(child)
      if (inner) box = box ? box.union(inner) : inner.clone()
    }
    if (box) boxes.set(node, box)
    return box
  }
  walk(root)
  return boxes
}

function mergeMeshes(list: Mesh[], ids: number[], withUv: boolean) {
  let vertices = 0
  let indices = 0
  let missingNormals = false
  for (const mesh of list) {
    const g = mesh.geometry
    vertices += g.attributes.position.count
    indices += g.index ? g.index.count : g.attributes.position.count
    if (!g.attributes.normal) missingNormals = true
  }
  const position = new Float32Array(vertices * 3)
  const normal = new Float32Array(vertices * 3)
  const uv = withUv ? new Float32Array(vertices * 2) : null
  const index = new Uint32Array(indices)
  const objectOf = new Uint32Array(vertices)
  const v = new Vector3()
  const normalMatrix = new Matrix3()
  let vo = 0
  let io = 0
  list.forEach((mesh, m) => {
    const g = mesh.geometry
    const p = g.attributes.position
    const n = g.attributes.normal
    const t = g.attributes.uv
    normalMatrix.getNormalMatrix(mesh.matrixWorld)
    objectOf.fill(ids[m], vo, vo + p.count)
    for (let i = 0; i < p.count; i++) {
      v.fromBufferAttribute(p, i).applyMatrix4(mesh.matrixWorld)
      position[(vo + i) * 3] = v.x
      position[(vo + i) * 3 + 1] = v.y
      position[(vo + i) * 3 + 2] = v.z
      if (n) {
        v.fromBufferAttribute(n, i).applyMatrix3(normalMatrix).normalize()
        normal[(vo + i) * 3] = v.x
        normal[(vo + i) * 3 + 1] = v.y
        normal[(vo + i) * 3 + 2] = v.z
      }
      if (uv && t) {
        uv[(vo + i) * 2] = t.getX(i)
        uv[(vo + i) * 2 + 1] = t.getY(i)
      }
    }
    if (g.index) for (let j = 0; j < g.index.count; j++) index[io++] = g.index.getX(j) + vo
    else for (let j = 0; j < p.count; j++) index[io++] = j + vo
    vo += p.count
  })
  const geometry = new BufferGeometry()
  geometry.setAttribute('position', new BufferAttribute(position, 3))
  geometry.setAttribute('normal', new BufferAttribute(normal, 3))
  if (uv) geometry.setAttribute('uv', new BufferAttribute(uv, 2))
  geometry.setIndex(new BufferAttribute(index, 1))
  if (missingNormals) geometry.computeVertexNormals()
  geometry.computeBoundingSphere()
  geometry.computeBoundingBox()
  return { geometry, objectOf }
}

/** Arestas (segmentos ou linhas contínuas) viram uma lista só de segmentos. */
function mergeLines(list: Line[]) {
  let pairs = 0
  for (const line of list) pairs += segmentCount(line)
  const position = new Float32Array(pairs * 6)
  const v = new Vector3()
  let o = 0
  for (const line of list) {
    const g = line.geometry
    const p = g.attributes.position
    const at = (k: number) => (g.index ? g.index.getX(k) : k)
    const count = g.index ? g.index.count : p.count
    const segments = line instanceof LineSegments
    for (let k = 0; k + 1 < count; k += segments ? 2 : 1) {
      for (const idx of [at(k), at(k + 1)]) {
        v.fromBufferAttribute(p, idx).applyMatrix4(line.matrixWorld)
        position[o++] = v.x
        position[o++] = v.y
        position[o++] = v.z
      }
    }
  }
  const geometry = new BufferGeometry()
  geometry.setAttribute('position', new BufferAttribute(position, 3))
  geometry.computeBoundingSphere()
  return geometry
}

function segmentCount(line: Line) {
  const g = line.geometry
  const count = g.index ? g.index.count : g.attributes.position.count
  return line instanceof LineSegments ? Math.floor(count / 2) : Math.max(count - 1, 0)
}

function hasMap(material: Material) {
  return 'map' in material && !!(material as Material & { map: unknown }).map
}

function getOrSet<K, V>(map: Map<K, V>, key: K, create: () => V): V {
  let value = map.get(key)
  if (value === undefined) {
    value = create()
    map.set(key, value)
  }
  return value
}

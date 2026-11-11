/** Categoria da peça, achada pelo nome do componente (o `.dae` do SketchUp não leva as etiquetas). */
export type Category =
  | 'structure'
  | 'roof'
  | 'doors'
  | 'windows'
  | 'furniture'
  | 'decor'
  | 'fixtures'
  | 'appliances'
  | 'lighting'

export const CATEGORY_LABELS: Record<Category, string> = {
  structure: 'Paredes, pisos e forros',
  roof: 'Telhado',
  doors: 'Portas',
  windows: 'Janelas e vidros',
  furniture: 'Móveis',
  decor: 'Decoração e plantas',
  fixtures: 'Louças e metais',
  appliances: 'Eletrodomésticos',
  lighting: 'Iluminação e tomadas',
}

/** Ordem da lista; na busca por palavra, a ordem de RULES decide (ex.: "vaso sanitário" antes de "vaso"). */
export const CATEGORY_ORDER: Category[] = ['structure', 'roof', 'doors', 'windows', 'furniture', 'decor', 'fixtures',
  'appliances', 'lighting']

const RULES: [Category, string[]][] = [
  ['fixtures', ['vaso sanitario', 'bacia', 'pia', 'cuba', 'torneira', 'chuveiro', 'ducha', 'tanque', 'lavatorio', 'banheira',
    'misturador', 'ralo', 'registro', 'sifao', 'bide', 'toalheiro', 'papeleira', 'faucet', 'sink', 'toilet', 'shower']],
  ['appliances', ['fogao', 'geladeira', 'refrigerador', 'microondas', 'micro ondas', 'forno', 'televis', 'tv ', 'smart tv',
    'maquina', 'lavadora', 'secadora', 'cooktop', 'coifa', 'depurador', 'ar condicionado', 'split', 'electrolux', 'brastemp',
    'lava loucas', 'adega', 'frigobar', 'fridge', 'oven', 'stove', 'washer', 'cafeteira', 'liquidificador', 'garrafao']],
  ['lighting', ['tomada', 'interruptor', 'luminaria', 'pendente', 'lampada', 'spot', 'lustre', 'plafon', 'arandela', 'fita led',
    'trilho', 'abajur', 'lamp', 'light', 'socket']],
  ['doors', ['porta', 'door', 'fechadura', 'macaneta', 'puxador porta', 'batente', 'portao']],
  ['windows', ['janela', 'window', 'vidro', 'glass', 'esquadria', 'basculante', 'veneziana', 'claraboia', 'guarda corpo']],
  ['roof', ['telhado', 'telha', 'roof', 'cumeeira', 'rufo', 'calha', 'cobertura']],
  ['furniture', ['cadeira', 'chair', 'mesa', 'table', 'cama', 'bed', 'sofa', 'poltrona', 'armario', 'rack', 'estante', 'banco',
    'banqueta', 'comoda', 'criado', 'guarda roupa', 'cabeceira', 'puff', 'pufe', 'aparador', 'buffet', 'escrivaninha',
    'prateleira', 'nicho', 'gaveta', 'colchao', 'bancada', 'painel', 'balcao', 'cristaleira', 'sapateira', 'berco', 'desk',
    'shelf', 'cabinet', 'wardrobe', 'stool', 'bench', 'couch']],
  ['decor', ['planta', 'plant', 'leaf', 'folha', 'vaso', 'arranjo', 'hera', 'philodendron', 'cortina', 'persiana', 'tapete',
    'quadro', 'almofada', 'manta', 'plaid', 'blanket', 'fringe', 'livro', 'decor', 'espelho', 'garrafa', 'prato', 'flor',
    'arvore', 'tree', 'bush', 'arbusto', 'cesto', 'escultura', 'relogio', 'curtain', 'rug', 'pillow', 'book', 'mirror', 'playmat']],
]

const GENERIC_WORDS = /\b(sketchup|group|grupo|node|mesh|instance|component|componente|id|assembly|object|objeto|box|3dgeom|geom|pattern2d|diferenca|defintion|definition|scene|cena|model|modelo|layer|untitled|null|undefined|root|solid|bodies|copy|copia|circle|sphere|cube|plane|cylinder|skp\w*)\b/g

/** Nome sem acento, em minúsculas, com `_`/`-` virando espaço. */
export function normalize(name: string) {
  return name.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase().replace(/[_\-.]+/g, ' ').replace(/\s+/g, ' ').trim()
}

/** Nome que não diz nada (group_12, instance_3, Componente#4). */
export function isGeneric(name: string | undefined) {
  if (!name) return true
  return normalize(name).replace(/\d+/g, ' ').replace(GENERIC_WORDS, ' ').replace(/[^a-z]/g, '').length < 2
}

export function categoryOf(names: string[]): Category | null {
  for (const raw of names) {
    const name = ` ${normalize(raw)} `
    for (const [category, words] of RULES) {
      if (words.some((w) => name.includes(` ${w}`))) return category
    }
  }
  return null
}

/** Nome para mostrar: sem `_`, sem número no fim, sem link. */
export function prettyName(name: string) {
  const clean = name.replace(/LINK_*https?_+\S*/i, '').replace(/_+/g, ' ').replace(/\s*#?\d+$/, '').replace(/\s+/g, ' ').trim()
  return clean ? clean.charAt(0).toUpperCase() + clean.slice(1) : ''
}

/** Link que o SketchUp trocou por `_` no nome (ex.: `LINK__https___amzn.to_42Pcaj9` → https://amzn.to/42Pcaj9). */
export function linkInName(name: string): string | null {
  const match = /(https?)_+([a-z0-9-]+(?:\.[a-z0-9-]+)+)_?([A-Za-z0-9_\-.~%]*)/i.exec(name)
  if (!match) return null
  const url = `${match[1].toLowerCase()}://${match[2].toLowerCase()}${match[3] ? `/${match[3].replace(/_/g, '/')}` : ''}`
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.toString() : null
  } catch {
    return null
  }
}

import type { PriceSource } from './types'

export const SOURCE_LABEL: Record<PriceSource, string> = {
  BUSCAPE: 'Buscapé',
  LINK: 'Link da loja',
  MANUAL: 'Anotado',
}

/** "-12,5%" / "+3%" (variação do menor preço); nulo sem dado. */
export function formatChange(value: number | null) {
  if (value === null || value === undefined) return null
  const text = Math.abs(value).toLocaleString('pt-BR', { maximumFractionDigits: 1 })
  return `${value > 0 ? '+' : value < 0 ? '-' : ''}${text}%`
}

/**
 * Botão do navegador (bookmarklet): na página da loja, lê nome e preço (dados de produto da página) e abre o LifePedia
 * para confirmar. Nada é enviado sem a pessoa confirmar.
 */
export function bookmarkletCode(origin: string) {
  const target = `${origin}/precos/capturar`
  return `javascript:(function(){var d=document,n=null,p=null;d.querySelectorAll('script[type="application/ld+json"]').forEach(function(s){try{[].concat(JSON.parse(s.textContent)).forEach(function g(o){if(!o||typeof o!=='object')return;if(o['@graph'])[].concat(o['@graph']).forEach(g);if(/Product/.test([].concat(o['@type']).join(' '))){n=n||o.name;var f=[].concat(o.offers||[])[0];if(f)p=p||f.price||f.lowPrice}})}catch(e){}});function m(k){var e=d.querySelector('meta[property="'+k+'"],meta[name="'+k+'"]');return e&&e.content}p=p||m('product:price:amount')||m('og:price:amount');n=n||m('og:title')||d.title;window.open('${target}?url='+encodeURIComponent(location.href)+'&nome='+encodeURIComponent(n||'')+'&preco='+encodeURIComponent(p||'')+'&loja='+encodeURIComponent(location.hostname.replace(/^www\\./,'')),'_blank')})();`
}

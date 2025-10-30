import { config } from 'dotenv'
import express from 'express'
import cors from 'cors'
import bodyParser from 'body-parser'
import fetch from 'node-fetch'
import fs from 'fs'
import { parse } from 'csv-parse/sync'

config({ path: './config.env' })

const app = express()
app.use(cors())
app.use(bodyParser.json())

const APP_ID = process.env.JAN_APP_ID ?? ''            // JANCODE LOOKUP の appId
const GEMINI_API_KEY = process.env.GEMINI_API_KEY ?? ''

console.log('[BOOT] JAN_APP_ID set:', !!APP_ID)
console.log('[BOOT] GEMINI_API_KEY set:', !!GEMINI_API_KEY)

const GEMINI_MODELS = [
  'gemini-2.0-flash',
  'gemini-2.0-flash-lite',
  'gemini-1.5-flash-8b'
] as const

type GeminiResp = {
  candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>
  error?: { code?: number; message?: string }
}

function promptFor(stage: 'category'|'product', q: string) {
  return stage === 'category'
    ? `ユーザー入力:「${q}」。日本のコンビニ/スーパーで買えるカテゴリ候補を最大5件、JSON配列のみで出力。例:
[{"id":"チョコ","label":"チョコ"},{"id":"コーヒー","label":"コーヒー"}]`
    : `カテゴリ:「${q}」。日本で流通する具体的な商品名を最大8件、JSON配列のみで出力（メーカー名は任意）。例:
[{"id":"キットカット","label":"キットカット"},{"id":"ブラックサンダー","label":"ブラックサンダー"}]`
}

function tryParseJson(text: string): any[] {
  try { return JSON.parse(text) } catch {}
  const m = text.match(/\[[\s\S]*\]/)
  if (m) { try { return JSON.parse(m[0]) } catch {} }
  return []
}

async function geminiGenerateText(prompt: string): Promise<{model?: string; text?: string; error?: string}> {
  if (!GEMINI_API_KEY) return { error: 'no_api_key' }
  for (const model of GEMINI_MODELS) {
    try {
      const url = `https://generativelanguage.googleapis.com/v1/models/${model}:generateContent?key=${encodeURIComponent(GEMINI_API_KEY)}`
      const body = { contents: [{ role: 'user', parts: [{ text: prompt }] }] }
      const r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
      const j = (await r.json()) as GeminiResp
      if (!r.ok) {
        const msg = j?.error?.message || `HTTP ${r.status}`
        console.warn(`[GEMINI] ${model} -> ${msg}`)
        continue
      }
      const text =
        j?.candidates?.[0]?.content?.parts?.[0]?.text ||
        j?.candidates?.[0]?.content?.parts?.map(p => p.text).filter(Boolean).join('\n')
      if (text && text.trim()) return { model, text }
    } catch (e: any) {
      console.warn(`[GEMINI] ${model} exception:`, String(e).slice(0, 160))
      continue
    }
  }
  return { error: 'no_working_model' }
}

function heuristic(stage: 'category'|'product', q: string) {
  const s = q ?? ''
  if (stage === 'category') {
    if (s.includes('苦')) return ['コーヒー','ビターチョコ','抹茶菓子','カカオ高含有チョコ','エスプレッソ']
    if (s.includes('甘')) return ['チョコ','アイス','グミ','ビスケット','和菓子']
    if (s.includes('塩') || s.includes('しょっぱ')) return ['スナック','せんべい','柿の種','ナッツ','ポテトチップス']
  } else {
    if (s.includes('コーヒ')) return ['UCC ブラック無糖','BOSS ブラック','ジョージア 深煎りブラック','タリーズ ブラック']
    if (s.includes('チョコ')) return ['キットカット','ブラックサンダー','明治ザ・チョコレート','ガーナ ブラック','DARS ビター']
  }
  return []
}

app.post('/suggest', async (req, res) => {
  const { query, stage = 'category' } = req.body || {}
  if (!query) return res.status(400).json({ error: 'query required' })

  let source = 'none'
  let options: Array<{id:string; label:string}> = []

  if (GEMINI_API_KEY) {
    const g = await geminiGenerateText(promptFor(stage, query))
    if (g.text) {
      const parsed = tryParseJson(g.text)
      if (parsed?.length) { options = parsed; source = `gemini:${g.model}` }
      else source = `gemini-empty:${g.model}`
    } else {
      source = `gemini-error:${g.error}`
    }
  } else {
    source = 'no-api-key'
  }

  if (!options.length) {
    const h = heuristic(stage, query).map(x => ({ id: x, label: x }))
    if (h.length) { options = h; source += '+heuristic' }
  }

  res.json({ stage, options, source })
})

app.post('/search', async (req, res) => {
  const { query, limit = 50 } = req.body || {}
  if (!query) return res.status(400).json({ error: 'query required' })

  const terms = [query]
  const seen = new Map<string, any>()
  for (const t of terms) {
    const url = `https://api.jancodelookup.com/?appId=${APP_ID}&query=${encodeURIComponent(t)}&hits=30&type=keyword`
    try {
      const r = await fetch(url); const j: any = await r.json()
      const items = j.products || j.product || []
      for (const p of items) {
        const jan = p.codeNumber; if (!jan) continue
        if (!seen.has(jan)) seen.set(jan, {
          jan,
          name: p.itemName,
          category: p.categoryName ?? null,
          tags: [],
          score_query_match: 0.5
        })
        if (seen.size >= limit) break
      }
      if (seen.size >= limit) break
    } catch (e) {
      return res.status(502).json({ error: 'JAN lookup failed', detail: String(e) })
    }
  }
  res.json({ candidates: Array.from(seen.values()) })
})

function loadStores(): Array<{ id: string; name: string; lat: number; lng: number }> {
  try {
    const csvContent = fs.readFileSync('./data/stores.csv', 'utf-8')
    const records = parse(csvContent, {
      columns: true,
      skip_empty_lines: true,
      trim: true
    })
    return records.map((r: any) => ({
      id: r.store_id,
      name: r.name,
      lat: parseFloat(r.lat),
      lng: parseFloat(r.lng)
    }))
  } catch (e) {
    console.warn('[CSV] stores.csv load failed:', e)
    return []
  }
}

function loadPosSales(): Array<{ store_id: string; jan: string; sold_at: string; qty: number }> {
  try {
    const csvContent = fs.readFileSync('./data/pos_sales.csv', 'utf-8')
    const records = parse(csvContent, {
      columns: true,
      skip_empty_lines: true,
      trim: true
    })
    return records.map((r: any) => ({
      store_id: r.store_id,
      jan: r.jan,
      sold_at: r.sold_at,
      qty: parseInt(r.qty, 10)
    }))
  } catch (e) {
    console.warn('[CSV] pos_sales.csv load failed:', e)
    return []
  }
}

const stores = loadStores()
const posSales = loadPosSales()

console.log(`[CSV] Loaded ${stores.length} stores`)
console.log(`[CSV] Loaded ${posSales.length} pos_sales records`)

app.post('/stores', (req, res) => {
  const { jan_list, lat, lng, radius_km = 3 } = req.body || {}
  if (!jan_list || !Array.isArray(jan_list) || typeof lat !== 'number' || typeof lng !== 'number') {
    return res.status(400).json({ error: 'jan_list, lat, lng required' })
  }
  const now = Date.now()
  const results = stores.map(s => {
    const dKm = haversine(lat, lng, s.lat, s.lng)
    if (dKm > radius_km) return null
    const sales = posSales.filter(p => jan_list.includes(p.jan) && p.store_id === s.id)
    const last = sales.map(x => new Date(x.sold_at).getTime()).sort((a,b)=>b-a)[0]
    const count7d = sales.filter(x => now - new Date(x.sold_at).getTime() <= 7*86400000).length
    const freshness = !last ? 0 : freshnessScore(now - last)
    const velocity = Math.min(count7d / 10, 1)
    const distance = 1 / (1 + dKm)
    const score = freshness * velocity * distance
    return {
      store_id: s.id, name: s.name, lat: s.lat, lng: s.lng,
      last_sold_at: last ? new Date(last).toISOString() : null,
      weekly_count: count7d, score_availability: score,
      grade: score >= 0.7 ? '◎' : score >= 0.4 ? '○' : '△'
    }
  }).filter(Boolean)
  res.json({ stores: results })
})

function freshnessScore(ms: number){
  const d = ms/86400000
  if (d <= 1) return 1.0
  if (d <= 3) return 0.7
  if (d <= 7) return 0.4
  if (d <= 14) return 0.2
  return 0.1
}
function haversine(lat1:number,lon1:number,lat2:number,lon2:number){
  const R=6371; const toRad=(x:number)=>x*Math.PI/180
  const dLat=toRad(lat2-lat1); const dLon=toRad(lon2-lon1)
  const a=Math.sin(dLat/2)**2+Math.cos(toRad(lat1))*Math.cos(toRad(lat2))*Math.sin(dLon/2)**2
  return 2*R*Math.asin(Math.sqrt(a))
}

app.listen(3000, () => console.log('API on :3000'))

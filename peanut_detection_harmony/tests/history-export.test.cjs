const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const Module = require('node:module')
const ts = require('typescript')

const file = path.resolve(__dirname, '../entry/src/main/ets/db/HistoryExport.ets')
const source = fs.readFileSync(file, 'utf8')
const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
const loaded = new Module(file, module)
loaded.filename = file
loaded.paths = module.paths
loaded._compile(output, file)
const { serializeHistory, writeAll } = loaded.exports

test('export is versioned, preserves scores, omits private URIs and unknown provenance', () => {
  const record = { id: 3, timestamp: 1000, confidence: 0.3, imageUri: 'content://private/image', isMoldy: true }
  const json = JSON.parse(serializeHistory([record]))
  assert.equal(json.schema, 'peanut-demo-history-v1')
  assert.equal(json.validation_status, 'unvalidated_demo')
  assert.equal(json.records.length, 1)
  assert.equal(json.records[0].timestamp_ms, 1000)
  assert.equal(json.records[0].demo_score, 0.3)
  assert.equal(json.records[0].model_sha256, null)
  assert.equal(json.records[0].scaler_sha256, null)
  assert.equal(json.records[0].pipeline_version, null)
  assert.equal(json.records[0].input_sha256, null)
  assert.equal(json.input_hash_definition, null)
  assert.equal(JSON.stringify(json).includes('content://private'), false)
})

test('empty history is an empty array', () => {
  assert.deepEqual(JSON.parse(serializeHistory([])).records, [])
})

test('invalid scores fail instead of being silently changed to null', () => {
  assert.throws(() => serializeHistory([{ id: 1, timestamp: 1, confidence: NaN }]))
  assert.throws(() => serializeHistory([{ id: 1, timestamp: 1, confidence: 1.1 }]))
})

test('partial writes are retried until every UTF-8 byte is written', async () => {
  const input = Buffer.from('花生 A', 'utf8')
  const parts = []
  await writeAll(input, async data => {
    const chunk = Buffer.from(data)
    const count = Math.min(2, chunk.length)
    parts.push(chunk.subarray(0, count))
    return count
  })
  assert.deepEqual(Buffer.concat(parts), input)
})

test('a non-progressing or invalid writer fails without looping forever', async () => {
  await assert.rejects(writeAll(Uint8Array.of(1, 2), async () => 0))
  await assert.rejects(writeAll(Uint8Array.of(1, 2), async () => 3))
})

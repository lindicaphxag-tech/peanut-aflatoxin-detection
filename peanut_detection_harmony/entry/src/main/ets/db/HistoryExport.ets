export interface ExportSource {
  id: number
  timestamp: number
  confidence: number
}

interface ExportRecord {
  id: number
  timestamp_ms: number
  demo_score: number
  model_sha256: null
  scaler_sha256: null
  pipeline_version: null
  input_sha256: null
}

interface ExportDocument {
  schema: string
  validation_status: string
  input_hash_definition: null
  records: ExportRecord[]
}

export function serializeHistory(records: ExportSource[]): string {
  const exported: ExportRecord[] = records.map((item: ExportSource): ExportRecord => {
    if (!Number.isSafeInteger(item.id) || !Number.isSafeInteger(item.timestamp) ||
      !Number.isFinite(item.confidence) || item.confidence < 0 || item.confidence > 1) {
      throw new Error('历史记录包含无效数值')
    }
    return {
      id: item.id,
      timestamp_ms: item.timestamp,
      demo_score: item.confidence,
      model_sha256: null,
      scaler_sha256: null,
      pipeline_version: null,
      input_sha256: null
    }
  })
  const document: ExportDocument = {
    schema: 'peanut-demo-history-v1',
    validation_status: 'unvalidated_demo',
    input_hash_definition: null,
    records: exported
  }
  return JSON.stringify(document, null, 2)
}

export async function writeAll(bytes: Uint8Array, writer: (data: ArrayBuffer) => Promise<number>): Promise<void> {
  let offset = 0
  while (offset < bytes.length) {
    const remaining = new Uint8Array(bytes.length - offset)
    remaining.set(bytes.subarray(offset))
    const count = await writer(remaining.buffer as ArrayBuffer)
    if (!Number.isInteger(count) || count <= 0 || count > remaining.length) {
      throw new Error('写入文件失败')
    }
    offset += count
  }
}

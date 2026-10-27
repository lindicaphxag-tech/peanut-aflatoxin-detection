import { relationalStore } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';

export interface HistoryRecord {
  id: number;
  timestamp: number;
  imageUri: string;
  isMoldy: boolean;
  confidence: number;
}

export class HistoryDb {
  private store: relationalStore.RelationalStore | null = null;

  async init(context: common.UIAbilityContext) {
    const config: relationalStore.StoreConfig = {
      name: 'detection_history.db',
      securityLevel: relationalStore.SecurityLevel.S1
    };
    this.store = await relationalStore.getRdbStore(context, config);
    await this.store.executeSql(`CREATE TABLE IF NOT EXISTS history (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      timestamp INTEGER NOT NULL,
      image_uri TEXT NOT NULL,
      is_moldy INTEGER NOT NULL,
      confidence REAL NOT NULL
    )`);
  }

  async insert(record: Omit<HistoryRecord, 'id'>) {
    if (!this.store) return;
    const valueBucket: Record<string, number | string | boolean> = {
      'timestamp': record.timestamp,
      'image_uri': record.imageUri,
      'is_moldy': record.isMoldy ? 1 : 0,
      'confidence': record.confidence
    };
    await this.store.insert('history', valueBucket);
  }

  async getAll(): Promise<HistoryRecord[]> {
    if (!this.store) return [];
    const result = await this.store.querySql('SELECT * FROM history ORDER BY timestamp DESC');
    const list: HistoryRecord[] = [];
    while (result.goToNextRow()) {
      list.push({
        id: result.getLong(result.getColumnIndex('id')),
        timestamp: result.getLong(result.getColumnIndex('timestamp')),
        imageUri: result.getString(result.getColumnIndex('image_uri')),
        isMoldy: result.getLong(result.getColumnIndex('is_moldy')) === 1,
        confidence: result.getDouble(result.getColumnIndex('confidence'))
      });
    }
    return list;
  }
}

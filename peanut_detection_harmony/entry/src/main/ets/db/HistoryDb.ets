import relationalStore from '@ohos.data.relationalStore';
import common from '@ohos.app.ability.common';

export interface HistoryRecord {
  id: number;
  timestamp: number;
  imageUri: string;
  isMoldy: boolean;
  confidence: number;
}
export interface NewHistoryRecord {
  timestamp: number;
  imageUri: string;
  isMoldy: boolean;
  confidence: number;
}

export class HistoryDb {
  private store: relationalStore.RdbStore | null = null;

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

  async insert(record: NewHistoryRecord) {
    if (!this.store) throw new Error('历史记录尚未初始化');
    const valueBucket: relationalStore.ValuesBucket = {
      'timestamp': record.timestamp,
      'image_uri': record.imageUri,
      'is_moldy': record.isMoldy ? 1 : 0,
      'confidence': record.confidence
    };
    await this.store.insert('history', valueBucket);
  }

  async getAll(): Promise<HistoryRecord[]> {
    if (!this.store) throw new Error('历史记录尚未初始化');
    const result: relationalStore.ResultSet = await this.store.querySql('SELECT * FROM history ORDER BY timestamp DESC');
    const list: HistoryRecord[] = [];
    try {
      while (result.goToNextRow()) {
        const record: HistoryRecord = {
          id: result.getLong(result.getColumnIndex('id')),
          timestamp: result.getLong(result.getColumnIndex('timestamp')),
          imageUri: result.getString(result.getColumnIndex('image_uri')),
          isMoldy: result.getLong(result.getColumnIndex('is_moldy')) === 1,
          confidence: result.getDouble(result.getColumnIndex('confidence'))
        };
        list.push(record);
      }
    } finally {
      result.close();
    }
    return list;
  }
}

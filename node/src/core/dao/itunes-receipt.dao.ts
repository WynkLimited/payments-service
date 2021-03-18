import {DB} from '../../models/db/db.model';
import {MongoConnectionFactory as connectionFactory} from '../../common/mongo-connection.factory';
import {
  iTunesReceipt,
  ITunesReceiptDoc
} from '../../models/itunes/itunes-receipt.model';

import { IReceiptDao} from './receipt.dao';

export class ITunesReceiptDao implements IReceiptDao<ITunesReceiptDoc> {
  private db: DB;

  constructor() {
    const env = process.env as any;
    this.db = new DB(env.URI, env.USERNAME, env.PASSWORD, env.DATABASE);
  }

  public findOne(filter: {}): Promise<ITunesReceiptDoc | null> {
    return connectionFactory(this.db).then(() => {
      return iTunesReceipt.findOne(filter);
    });
  }

  public find(filter: {}): Promise<ITunesReceiptDoc[]> {
    return connectionFactory(this.db).then(() => {
      return iTunesReceipt.find(filter);
    });
  }
}

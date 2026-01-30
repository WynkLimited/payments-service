import { Receipt } from '../../models/receipt.model';

export interface IReceiptDao<T extends Receipt> {
  find(filter: {}): Promise<Array<T>>;
  findOne(filter: {}): Promise<T | null>;
}

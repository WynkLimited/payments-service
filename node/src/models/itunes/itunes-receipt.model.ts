import {Schema, model, Model} from 'mongoose';

import {Receipt, ReceiptSchema} from '../receipt.model';

export class ITunesReceiptDoc extends Receipt {
  receipt: string;
  type: string;
  transactionId: number;
}

const ITunesReceiptSchema: Schema = new Schema({
  ...ReceiptSchema.obj,
  receipt: { type: String, required: true },
  type: { type: String, required: false },
  transactionId: { type: String, required: false }
});

export const iTunesReceipt: Model<ITunesReceiptDoc> = model(
  'itunesReceipt',
  ITunesReceiptSchema,
  'ReceiptDetails'
);

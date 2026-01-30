import { Schema, Document } from 'mongoose';

export abstract class Receipt extends Document<String> {
  uid: string;
  msisdn: string;
  planId: number;
  expiry: number;
}
export const ReceiptSchema: Schema = new Schema({
  _id: { type: String, required: true },
  msisdn: { type: String, required: true },
  uid: { type: String, required: true },
  planId: { type: Number, required: true },
  expiry: { type: Number, required: false }
});

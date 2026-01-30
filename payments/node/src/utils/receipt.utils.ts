import { Base64 } from 'js-base64';

import { ITunesReceiptDoc } from '../models/itunes/itunes-receipt.model';
import { AppConstant } from '../constant/app.constant';

export class ReceiptUtils {
  public static getEncodedReceipt(receipt: ITunesReceiptDoc): string {
    let decodedReceipt = '';
    if (receipt.type === AppConstant.SIX) {
      const receiptObj = JSON.parse(receipt.receipt);
      decodedReceipt = JSON.stringify(receiptObj);
    } else {
      const receiptObj = JSON.parse(receipt.receipt);
      decodedReceipt = JSON.stringify({'receipt-data': receiptObj['receipt-data']});
    }
    return ReceiptUtils.base64Encode(decodedReceipt);
  }

  private static encloseInDoubleQuotes(data: string): string {
    const doubleQuotes: string = '"';
    return doubleQuotes + data + doubleQuotes;
  }

  private static base64Encode(payload: string): string {
    return Base64.btoa(payload);
  }
}

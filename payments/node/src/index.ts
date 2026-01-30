import {Logger} from 'log4js';

import { IReceiptDao } from './core/dao/receipt.dao';
import { ITunesReceiptDao } from './core/dao/itunes-receipt.dao';
import {ITunesReceiptDoc} from './models/itunes/itunes-receipt.model';
import { AppLogger } from './core/service/app-logger.service';
import { ITunesCallbackRequest } from './models/dto/request/itunes-callback.request';
import { PaymentService } from './core/service/payment-client.service';
import { ReceiptUtils } from './utils/receipt.utils';

function reconReceipts() {
  const logger: Logger = AppLogger.getLogger({
    appenders: { payment: { type: 'file', filename: 'payment.log' } },
    categories: { default: { appenders: ['payment'], level: 'info' } }
  });
  const paymentClient: PaymentService = new PaymentService();
  const receiptDao: IReceiptDao<ITunesReceiptDoc> = new ITunesReceiptDao();
  logger.info('starting recon process...');
  receiptDao
    .find({$and: [{_class: 'in.wynk.payment.core.dao.entity.ItunesReceiptDetails'}, {$or: [{'expiry': {$lt: new Date().getTime()}}, {'expiry': {$exists: false}}]}]})
    .then((receipts) => {
      logger.info(`found total ${receipts.length} receipts`);
      receipts
        .map((receipt: ITunesReceiptDoc) => ({
          receipt,
          request: {
            latest_receipt: ReceiptUtils.getEncodedReceipt(receipt),
            notification_type: 'DID_RENEW',
            latest_receipt_info: {original_transaction_id: receipt._id}
          } as ITunesCallbackRequest
        }))
        .map((pair) => {
          logger.info(
            `posting request for uid ${pair.receipt.uid} => `,
            pair.request
          );
          paymentClient.postReceiptCallback(pair.request).then((res) => {
            logger.info(
              'received response for uid => ',
              pair.receipt.uid,
              res
            );
          }).catch(error => logger.error("an error occured for uid => ", pair.receipt.uid, error));
        });
    })
    .catch((err) => logger.error(err));
}

reconReceipts();

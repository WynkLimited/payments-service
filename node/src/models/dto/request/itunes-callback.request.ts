export class ITunesCallbackRequest {
  latest_receipt_info: LatestReceiptInfo;
  latest_receipt: string;
  notification_type: string;
}

class LatestReceiptInfo {
  cancellation_date: string;
  cancellation_date_ms: string;
  cancellation_date_pst: string;
  expires_date_ms: string;
  expires_date: string;
  cancellation_reason: string;
  expires_date_pst: string;
  is_in_intro_offer_period: string;
  is_trial_period: string;
  is_upgraded: string;
  original_purchase_date: string;
  original_purchase_date_ms: string;
  original_purchase_date_pst: string;
  original_transaction_id: string;
  product_id: string;
  purchase_date: string;
  purchase_date_ms: string;
  purchase_date_pst: string;
  subscription_group_identifier: string;
  transaction_id: string;
  web_order_line_item_id: string;
}

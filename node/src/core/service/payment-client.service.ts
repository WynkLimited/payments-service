

import { ITunesCallbackRequest } from '../../models/dto/request/itunes-callback.request';
import { ITunesCallbackResponse } from '../../models/dto/response/itunes-callback.response';

import { HttpClient } from './http-client.service';
import { AxiosResponse } from 'axios';

export class PaymentService extends HttpClient {
  constructor() {
    super(process.env.PAYMENT_SERVICE_HOST as string);
  }

  public postReceiptCallback(
    payload: ITunesCallbackRequest
  ): Promise<AxiosResponse<ITunesCallbackResponse | {success: boolean}>> {
    return this.instance.post<ITunesCallbackResponse | {success: boolean}>(
      '/wynk/v1/callback/itunes',
      payload
    );
  }
}

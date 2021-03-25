import axios, { AxiosInstance, AxiosResponse } from 'axios';
import { Logger } from 'log4js';

import { AppLogger } from './app-logger.service';

declare module 'axios' {
  interface AxiosResponse<T = any> extends Promise<T> {}
}

export abstract class HttpClient {
  protected readonly instance: AxiosInstance;
  private logger: Logger = AppLogger.getLogger({
    appenders: { httpClient: { type: 'file', filename: 'payment.log' } },
    categories: { default: { appenders: ['httpClient'], level: 'info' } }
  });

  private _initializeResponseInterceptor = () => {
    this.instance.interceptors.response.use(
      this._handleResponse,
      this._handleError
    );
  };

  private _handleResponse = ({data}: AxiosResponse) => {
    this.logger.info(data);
    return data;
  };

  protected _handleError = (error: any) => {
    this.logger.info(error);
    return Promise.reject(error);
  };

  public constructor(baseURL: string) {
    this.instance = axios.create({
      baseURL
    });
    this._initializeResponseInterceptor();
  }
}

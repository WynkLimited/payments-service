import {configure, getLogger, Configuration} from 'log4js';

export class AppLogger {
  public static getLogger(config: Configuration) {
    configure(config);
    return getLogger(Object.keys(config.appenders)[0]);
  }
}

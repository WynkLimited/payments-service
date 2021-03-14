import { Mongoose, connect } from 'mongoose';

import { DB } from '../models/db/db.model';

let isConnected: boolean = false;

export const MongoConnectionFactory = (db: DB): Promise<any> => {
  if (isConnected) {
    return Promise.resolve(true);
  }
  return connect(db.getUri(), {
    useUnifiedTopology: true,
    useNewUrlParser: true
  })
    .then((db: Mongoose) => {
      isConnected = db.connection.readyState == 1;
      return Promise.resolve();
    })
    .catch((error) => {
      return Promise.reject(error);
    });
};

export class DB {
  uri: string;
  username: string;
  password: string;
  database: string;
  constructor(
    uri: string,
    username: string,
    password: string,
    database: string
  ) {
    if (uri) this.uri = uri;
    else throw 'uri can not be null';
    if (username) this.username = username;
    else throw 'username can not be null';
    if (password) this.password = password;
    else throw 'password can not be null';
    if (database) this.database = database;
    else throw 'database can not be null';
  }

  public getUri(): string {
    return `mongodb://${this.username}:${this.password}@${this.uri}/${this.database}` as string;
  }
}

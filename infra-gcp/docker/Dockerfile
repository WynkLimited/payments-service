FROM asia-south1-docker.pkg.dev/prj-wynk-prd-cmn-svc-01/devops-infra-cmn-prd/java:openjdk-8u111-jdk
ARG profile
ENV profile $profile
ENV JAR_NAME=payments-0.0.1.jar
ENV KEY_NAME=authkeystore.jks
ENV JAVA_OPTS="-Duser.timezone=Asia/Calcutta -Dfile.encoding=UTF-8 -Dspring.profiles.active=$profile"
ENV JMX_OPTS="-Xmx2g -XX:+HeapDumpOnOutOfMemoryError -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -XX:+AggressiveOpts -XX:+UseFastAccessorMethods"
ADD $JAR_NAME /opt/
ADD $KEY_NAME /opt/
WORKDIR /opt/
RUN mkdir -p /data
RUN mkdir -p /logs
RUN echo $JAVA_OPTS
EXPOSE 8081
CMD exec java $JMX_OPTS $JAVA_OPTS -jar /opt/$JAR_NAME

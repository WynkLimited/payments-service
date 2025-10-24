FROM asia-south1-docker.pkg.dev/prj-wynk-prd-cmn-svc-01/devops-infra-cmn-prd/java:openjdk-8u111-jdk
ARG profile
ENV profile $profile
ENV JAR_NAME=payments-0.0.1.jar
ENV KEY_NAME=authkeystore.jks
ENV PUB_KEY_NAME=public.key
ENV JAVA_OPTS="-Duser.timezone=Asia/Calcutta -Dfile.encoding=UTF-8 -Dspring.profiles.active=$profile"
ENV JMX_OPTS="-Xmx2g -XX:+HeapDumpOnOutOfMemoryError -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -XX:+AggressiveOpts -XX:+UseFastAccessorMethods"
ADD $JAR_NAME /opt/
ADD $KEY_NAME /opt/
ADD $PUB_KEY_NAME /opt/
ADD AppDynamics /opt/AppDynamics
WORKDIR /opt/
RUN mkdir -p /data
RUN mkdir -p /logs
RUN echo $JAVA_OPTS
EXPOSE 8081
ENV APPDYNAMICS_CONTROLLER_HOST_NAME='bhartiairtellimited.saas.appdynamics.com'
ENV APPDYNAMICS_CONTROLLER_PORT='443'
ENV APPDYNAMICS_CONTROLLER_SSL_ENABLED='true'
ENV APPDYNAMICS_AGENT_APPLICATION_NAME='WCF-SUBSCRIPTION-PROD'
ENV APPDYNAMICS_AGENT_TIER_NAME='WCF'
ENV APPDYNAMICS_AGENT_NODE_NAME='SUBSCRIPTION-PROD'
ENV APPDYNAMICS_AGENT_ACCOUNT_NAME='bhartiairtellimited'
ENV APPDYNAMICS_AGENT_ACCOUNT_ACCESS_KEY='51f9cd81-e855-4b05-b5a1-48ddd9a88eda'
CMD exec java $JMX_OPTS $JAVA_OPTS -jar /opt/$JAR_NAME

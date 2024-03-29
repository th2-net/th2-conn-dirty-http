# th2-conn-dirty-http v.0.1.0

This microservice allows performing HTTP requests and receive HTTP responses. It also can perform basic authentication

# Configuration

+ *sessions* - list of session settings
+ *maxBatchSize* - max size of outgoing message batch (`1000` by default)
+ *maxFlushTime* - max message batch flush time (`1000` by default)
+ *publishSentEvents* - enables/disables publish of "message sent" events (`true` by default)
+ *publishConnectEvents* - enables/disables publish of "connect/disconnect" events (`true` by default)
+ *sendLimit* - global send limit in bytes (`0` by default which means no limit)
+ *receiveLimit* - global receive limit in bytes (`0` by default which means no limit)

## Session settings

+ *sessionAlias* - session alias for incoming/outgoing th2 messages
+ *handler* - handler settings
+ *mangler* - mangler settings (`null` by default)

**NOTE**: current implementation has no mangler

### Handler settings

+ *security* - security settings
+ *host* - server host
+ *port* - server port (by default `443` if `security.ssl` is `true` otherwise `80`)
+ *session* - session settings

#### Security settings

+ *ssl* - enables SSL on connection (`false` by default)
+ *sni* - enables SNI support (`false` by default)
+ *certFile* - path to server certificate (`null` by default)
+ *acceptAllCerts* - accept all server certificates (`false` by default, takes precedence over `certFile`)

**NOTE**: when using infra 1.7.0+ it is recommended to load value for `certFile` from a secret by using `${secret_path:secret_name}` syntax.

#### Sessions settings

+ *auth* - basic authentication settings (`null` by default)
+ *headers* - map of headers to add to each request (empty by default)

##### Authentication settings

+ *username* - username to use
+ *password* - password to use

**NOTE**: if header from `headers` is already set in request it will not be replaced

## MQ pins

* input queue with `subscribe` and `send` attributes for requests
* output queue with `publish` and `raw` attributes for incoming/outgoing messages

## Box configuration example

```yaml
apiVersion: th2.exactpro.com/v1
kind: Th2Box
metadata:
  name: http-client
spec:
  image-name: ghcr.io/th2-net/th2-conn-dirty-http
  image-version: 0.0.1
  type: th2-conn
  custom-config:
    maxBatchSize: 1000
    maxFlushTime: 1000
    publishSentEvents: true
    publishConnectEvents: true
    sessions:
      - sessionAlias: client
        mangler: { }
        handler:
          security:
            ssl: false
            sni: false
            certFile: ${secret_path:cert_secret}
            acceptAllCerts: false
          host: 127.0.0.1
          port: 4567
          session:
            auth:
              username: user
              password: pass
            headers:
              header-name: header-value
  pins:
    - name: to_send
      connection-type: mq
      attributes:
        - subscribe
        - send
        - raw
      settings:
        storageOnDemand: false
        queueLength: 1000
    - name: incoming_messages
      connection-type: mq
      attributes:
        - publish
        - store
        - raw
      filters:
        - metadata:
            - field-name: direction
              expected-value: FIRST
              operation: EQUAL
    - name: outgoing_messages
      connection-type: mq
      attributes:
        - publish
        - store
        - raw
      filters:
        - metadata:
            - field-name: direction
              expected-value: SECOND
              operation: EQUAL
  extended-settings:
    service:
      enabled: false
    resources:
      limits:
        memory: 500Mi
        cpu: 1000m
      requests:
        memory: 100Mi
        cpu: 100m
```

# Changelog

## 0.1.0

* add option to set global send/receive limit
* disable mangling if no mangler settings are specified
* bump `common` dependency to `3.44.0`
* bump `common-utils` dependency to `0.0.3`
* bump `conn-dirty-tcp-core` dependency to `2.0.5`

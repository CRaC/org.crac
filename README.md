# org.crac

## Use

```
<dependency>
  <groupId>org.crac</groupId>
  <artifactId>crac</artifactId>
  <version>0.1.3</version>
</dependency>
```

## Javadoc

https://javadoc.io/doc/org.crac/crac/latest/index.html

## Description

The org.crac is designed to provide smooth CRaC adoption.
Users of the library can build against and use CRaC API on Java runtimes with `jdk.crac`, `javax.crac`, or without any implementation.
* In compile-time, `org.crac` package totally mirrors `jdk.crac` and `javax.crac`.
* In runtime, org.crac uses reflection to detect CRaC implementation.
If the one is available, all requests to `org.crac` are passed to the implementation.
Otherwise, requests are forwarded to a dummy implementation.

The dummy implementation allows an application to run but not to use CRaC:
* resources can be registered for notification,
* checkpoint request fails with an exception.

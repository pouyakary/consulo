## About

Consulo - multi-language ide. Project was started in 2013 year by forking [IDEA Community Edition](https://github.com/JetBrains/intellij-community).

Main goal - create **open** IDE where you don't need select IDE for different languages. Provide a standard for language implementation inside IDE.

## Contributing

If you can't describe issue, you can use our [forum](https://discuss.consulo.io/), or you can read [contributing guide](https://github.com/consulo/consulo/blob/master/CONTRIBUTING.md)  and report issue at GitHub

## Building & Running

### Build Status

| JVM           | Github Actions|
| ------------- |-----------------:|
| Java 17       | ![jdk17](https://github.com/consulo/consulo/workflows/jdk17/badge.svg) |
| Java 18       | ![jdk18](https://github.com/consulo/consulo/workflows/jdk18/badge.svg) |
| Java 19       | ![jdk19](https://github.com/consulo/consulo/workflows/jdk19/badge.svg) |
| Java 20       | ![jdk20](https://github.com/consulo/consulo/workflows/jdk20/badge.svg) |
| Java 21       | ![jdk21](https://github.com/consulo/consulo/workflows/jdk21/badge.svg) |

First of all, you need those tools:

 * Maven 3.5+
 * JDK 17+

Then execute from command line:

```sh
mvn package
```

If you want run Consulo from repository
 * as desktop application

   ```sh
    mvn install

    mvn consulo:run-desktop -pl consulo:consulo-sandbox-desktop
   ```

 * as web application

   first need build web sandbox
   ```
   mvn package -am -pl consulo:consulo-sandbox-web
   ```

   then need start code server (since we used gwt as frontend)

   ```sh
   cd modules/web/web-ui-impl-client

   mvn -am vaadin:run-codeserver
   ```

   and start web server

   ```sh
   cd modules/web/web-bootstrap

   mvn -am jetty:run
   ```

## Sandbox Projects

 * Profiler API [link](https://github.com/consulo/profiler-sandbox)
 * Diagram support [link](https://github.com/consulo/consulo/tree/master/modules/base/graph-api)
 * Web IDE [link](https://github.com/consulo/consulo/tree/master/modules/web)
 * SWT UI [link](https://github.com/consulo/consulo/tree/master/modules/desktop-swt)

## Links

* [Contributing Guide](https://github.com/consulo/consulo/blob/master/CONTRIBUTING.md)
* [Download](https://consulo.app)
* [Issues](https://github.com/consulo/consulo/issues)
* [Forum](https://https://github.com/consulo/consulo/discussions/)
* [Discord Channel](https://discord.gg/Ab3Ka5gTFv)

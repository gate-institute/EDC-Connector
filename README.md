# EDC Connector

This is a fork of the [EDC Connector](https://github.com/eclipse-edc/Connector)
repo and contains gate-specific changes for testing and deployment purposes.

The `gate/0.12.0` branch is the main branch used for GATE deployments of the
EDC connector. It contains some changes to EDC's `v0.12.0` tag
(ref. [8f27b9c](https://github.com/eclipse-edc/Connector/tree/8f27b9c82161c62b670c88b581f544e003a3466c)),
_some_ of which are listed below:

* changed default gradle build to ignore some tasks by default (openapi, javadoc, etc.).
* `jersey-core`: added logging of incoming HTTP requests if their path matches
  a custom regular expression (config key `web.http.logging.filter.pattern`).
* `connector-core`: added logging of outgoing HTTP requests if their path matches
  a custom regular expression (config key `edc.http.client.logging.filter.pattern`).
* `connector-core`: added `user-agent` HTTP header for outgoing requests
  (config key `edc.http.client.useragent`).
* `connector-core`: added auto-generated `request-id` header for outgoing HTTP
  requests.

The list above is likely out-of-date. For a detailed view of *all* changes, see
[here](https://github.com/gate-institute/EDC-Connector/compare/v0.12.0...gate-0.12.0?expand=1).

## Quick start

Compile the project:

```bash
# See Makefile for full build command
make build
```

This is not a _real_ quick start, as the build artifacts cannot be used to
start anything -- they are just libs which can be used to build a runtime,
(runtimes are entirely separate projects, see 
[runtime-sample](https://github.com/gate-institute/gate-dsv2/tree/main/source/runtime-sample)
for example).

## Caveats

This project builds java artifacts versioned as `0.12.0` (and not `0.12.0-gate`).
This works around the need to compile *all* dependencies with that
version as well (such as the EDC
[RuntimeMetamodel](https://github.com/eclipse-edc/Runtime-Metamodel/blob/main/build.gradle.kts),
[GradlePlugins](https://github.com/eclipse-edc/GradlePlugins) and possibly
others). Compiling all dependencies is not a straight-forward process and
(as of July 2025) is not well documented.

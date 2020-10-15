# Tinyc

Tinyc is a POC of tiny utility for incremental compilation of the Java sources.

## Running

```
Fast Java compilerThis app compile the folder with Java sources incrementally
Usage: tinyc [-hV] [-b=<directory>] [-c=<path>] [-d=<directory>]
             [-s=<directory>]
Options:
  -b, --builddir=<directory>
                           Specify where to place the build artifacts.
                           Default: '../build'
  -c, --classpath=<path>   Specify where to find user class files and
                             annotation processors.
  -d, --destination=<directory>
                           Specify where to place the generated class files.
                           Default: '../build.classes'
  -h, --help               Show this help message and exit.
  -s, --sourceroot=<directory>
                           Specify where to find sources root folder.
                           Default: '.'
  -V, --version            Print version information and exit.

Exit codes:
  0   Successful program execution.
  1   Compilation error
  2   Can't create the destination directory where to place generated class
        files
  3   Incorrect source directory
  4   The incremental compilation not supported without debug information in
        the class files
  5   The environment variable JAVA_HOME is not set
  6   The javac executable is not found

Credits go to the 'Fast Java compiler' project:
https://github.com/vektory79/tinyc
```

## Build

Prerequisites:
* Java 11
* Maven 3.6.3

Build command:
```
mvn
```

### Native image

Project support compilation to the GraalVM native-image. It needs the GraalVM CE 20.2.0 JDK 11:
```
sdk install java 20.2.0.r11-grl
```

To build native image run:
```
mvn -Dnative
```

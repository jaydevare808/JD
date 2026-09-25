# JD Code Studio

Professional, local-first Android IDE foundation for tablet use.

## Language targets
PHP, Python, JavaScript, HTML, CSS, C, C++, Java, SQL, Bash.

## Embedded execution in v1
- HTML/CSS preview using Android WebView
- JavaScript execution using the WebView JavaScript engine
- SQL using the Android SQLite engine

## Runtime architecture
PHP, Python, C, C++, Java and Bash are represented as runtime adapters. Native toolchains are intentionally not faked in v1; runtime packs can be integrated without replacing the editor/project UI.

## Build
Android Gradle Plugin 9.4.0, Gradle 9.6.0, JDK 17.

Build verification is handled by the repository CI workflow.


CI trigger verified.


v1.2: real execution backend integration with OneCompiler API and stdin support.


v1.2 build correction: safe JavaScript quoting.

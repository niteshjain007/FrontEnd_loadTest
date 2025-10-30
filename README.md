## Selenium Java + TestNG (Maven)

Minimal Selenium project for UI load testing using Maven and TestNG.

### Prerequisites
- JDK 11+
- Maven 3.8+
- Google Chrome installed

### URL data
- Place URL list in `src/test/resources/urls.csv`.
- Expected format:
  ```csv
  url
  https://example.com
  https://wikipedia.org
  ```

### How it works
- `LoadTimeTest` reads URLs from the CSV and runs N worker threads (parallel TestNG methods).
- Each worker opens a Firefox browser (UI or headless) and measures load duration using Navigation Timing.
- Results are written to `target/load-results.csv` with columns: `timestamp,url,load_ms,status,error`.
- Slow URLs (>20s) are additionally captured in `target/slow-results.csv` with columns: `timestamp,url,load_ms`.
  - `timestamp` here is when the URL navigation started (time of opening the URL).

### Run tests
```bash
mvn clean test
```

### Configure parallelism
- Parallel mode is `methods`. Defaults are controlled via Maven properties: `threads` and `dpThreads`.
- Override at runtime:
  ```bash
  mvn clean test -Dthreads=12 -DdpThreads=12
  ```
- Defaults (if not provided): `threads=8`, `dpThreads=8`.

### Files of interest
- `src/test/java/com/example/selenium/LoadTimeTest.java`
- `src/test/java/com/example/selenium/ResultsWriter.java`
- `src/test/resources/urls.csv`
- `src/test/resources/testng.xml`

WebDriver binaries are managed automatically via WebDriverManager.

### Duration-based runs (loop URLs)
- The suite can run for a fixed duration, continuously cycling through URLs.
- Configure duration (minutes) and threads at runtime:
  ```bash
  mvn clean test -DdurationMinutes=10 -Dthreads=8 -DdpThreads=8
  ```
- Each worker opens browsers and measures load times repeatedly until the duration elapses.

### Headless vs UI mode
- Toggle Firefox headless mode with a system property:
  - UI (default):
    ```bash
    mvn clean test -DdurationMinutes=5 -Dthreads=4 -DdpThreads=4
    ```
  - Headless:
    ```bash
    mvn clean test -DdurationMinutes=5 -Dthreads=4 -DdpThreads=4 -Dheadless=true
    ```


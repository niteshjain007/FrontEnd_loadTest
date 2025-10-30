package com.example.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertNotNull;

/**
 * UI load test runner.
 *
 * - Reads URLs from urls.csv once and shares across worker threads.
 * - Spawns N workers (from -Dthreads) via a TestNG DataProvider (parallel=true).
 * - Each worker loops for -DdurationMinutes, cycling through URLs in round-robin.
 * - For each URL, opens a real Firefox browser (supports -Dheadless=true/false), measures load via Navigation Timing,
 *   writes a row to load-results.csv, and if load >= 20s, also to slow-results.csv with open-time.
 */
public class LoadTimeTest {
	private static volatile List<String> sharedUrls;
	private static final AtomicInteger nextUrlIndex = new AtomicInteger(0);

	/**
	 * Creates one row per worker index, enabling parallel execution.
	 * Count comes from -Dthreads (defaults to 8 when absent/invalid).
	 */
	@DataProvider(name = "workers", parallel = true)
	public Object[][] workers() {
		int workers = parsePositiveInt(System.getProperty("threads"), 8);
		Object[][] data = new Object[workers][1];
		for (int i = 0; i < workers; i++) {
			data[i][0] = i;
		}
		return data;
	}

	/**
	 * Main worker loop: runs until the requested duration elapses, cycling URLs.
	 */
	@Test(dataProvider = "workers")
	public void runForDuration(int workerIndex) throws Exception {
		ensureUrlsLoaded();
		long durationMinutes = parsePositiveLong(System.getProperty("durationMinutes"), 10L);
		long endAtMillis = System.currentTimeMillis() + Duration.ofMinutes(durationMinutes).toMillis();
		while (System.currentTimeMillis() < endAtMillis) {
			// Round-robin across the shared URL list using an atomic index
			int index = nextUrlIndex.getAndIncrement();
			String url = sharedUrls.get(index % sharedUrls.size());
			measureOnce(url);
		}
		assertNotNull(sharedUrls);
	}

	/**
	 * Opens one browser instance, navigates to the URL, and measures load time.
	 * Writes to results files and then always quits the browser.
	 */
	private void measureOnce(String url) {
		WebDriverManager.firefoxdriver().setup();
		FirefoxOptions options = new FirefoxOptions();
		// Toggle headless vs UI via -Dheadless=true|false (default: false => UI)
		boolean headless = Boolean.parseBoolean(System.getProperty("headless", "false"));
		if (headless) {
			options.addArguments("-headless");
		}
		WebDriver driver = new FirefoxDriver(options);
		driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
		long loadMs = -1L;
		Exception error = null;
		String openedTimestamp = null;
		try {
			// Capture navigation start time for slow-results.csv
			openedTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
			driver.get(url);
			// Prefer Navigation Timing L2; fall back to legacy timing if not available
			Object result = ((JavascriptExecutor) driver).executeScript(
					"var nav = performance.getEntriesByType('navigation')[0];" +
					"if (nav) { return Math.round(nav.duration); }" +
					"var t = performance.timing;" +
					"if (t && t.loadEventEnd && t.navigationStart) { return t.loadEventEnd - t.navigationStart; }" +
					"return -1;"
			);
			if (result instanceof Number) {
				loadMs = ((Number) result).longValue();
			}
		} catch (Exception e) {
			error = e;
		} finally {
			// Ensure browser closes even on failures
			try { driver.quit(); } catch (Exception ignored) {}
		}
		ResultsWriter.append(url, loadMs, error);
		if (loadMs >= 20000) {
			ResultsWriter.appendSlow(url, loadMs, openedTimestamp != null ? openedTimestamp : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()));
		}
	}

	/**
	 * Lazily loads URLs once per JVM using double-checked locking.
	 */
	private void ensureUrlsLoaded() throws Exception {
		if (sharedUrls == null) {
			synchronized (LoadTimeTest.class) {
				if (sharedUrls == null) {
					sharedUrls = readUrlsFromCsv("urls.csv");
				}
			}
		}
	}

	/**
	 * Reads URLs from a CSV resource; skips an optional header row if it starts with 'url'.
	 */
	private List<String> readUrlsFromCsv(String resourceName) throws Exception {
		List<String> urls = new ArrayList<>();
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try (InputStream is = cl.getResourceAsStream(resourceName)) {
			if (is == null) {
				throw new IllegalStateException("Resource not found: " + resourceName + " (place it in src/test/resources)");
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
				String line;
				boolean first = true;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty()) continue;
					// Skip header if present when first column name looks like 'url'
					if (first && line.toLowerCase().startsWith("url")) {
						first = false;
						continue;
					}
					first = false;
					String[] parts = line.split(",");
					if (parts.length > 0) {
						String url = parts[0].trim();
						if (!url.isEmpty()) {
							urls.add(url);
						}
					}
				}
			}
		}
		return urls;
	}

	/** Parses a positive int system property; falls back to default on invalid input. */
	private int parsePositiveInt(String s, int defVal) {
		try {
			int v = Integer.parseInt(s);
			return v > 0 ? v : defVal;
		} catch (Exception e) {
			return defVal;
		}
	}

	/** Parses a positive long system property; falls back to default on invalid input. */
	private long parsePositiveLong(String s, long defVal) {
		try {
			long v = Long.parseLong(s);
			return v > 0 ? v : defVal;
		} catch (Exception e) {
			return defVal;
		}
	}
}


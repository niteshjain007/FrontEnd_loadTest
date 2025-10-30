package com.example.selenium;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe CSV writers for load test results.
 *
 * - Writes all attempts to target/load-results.csv (timestamped with write time).
 * - Writes slow attempts (>20s) to target/slow-results.csv (timestamp = navigation start time).
 * - Ensures headers are written once per file using atomic flags and a common file lock.
 */
public class ResultsWriter {
	private static final Path RESULTS_PATH = Paths.get("target", "load-results.csv");
	private static final AtomicBoolean headerWritten = new AtomicBoolean(false);

	private static final Path SLOW_RESULTS_PATH = Paths.get("target", "slow-results.csv");
	private static final AtomicBoolean slowHeaderWritten = new AtomicBoolean(false);
	private static final Object fileLock = new Object();

	/**
	 * Appends a single result row to load-results.csv.
	 * Status is OK when no exception and loadMs >= 0; otherwise ERROR with message.
	 */
	public static void append(String url, long loadMs, Exception error) {
		String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
		String status = (error == null && loadMs >= 0) ? "OK" : "ERROR";
		String errMsg = (error == null) ? "" : escape(error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
		String line = String.join(",",
				escape(timestamp),
				escape(url),
				String.valueOf(loadMs),
				escape(status),
				errMsg
		);

		synchronized (fileLock) {
			try {
				Files.createDirectories(RESULTS_PATH.getParent());
				if (!headerWritten.get() || !Files.exists(RESULTS_PATH)) {
					writeHeader();
				}
				try (BufferedWriter writer = Files.newBufferedWriter(RESULTS_PATH, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
					writer.write(line);
					writer.newLine();
				}
			} catch (IOException e) {
				// Best-effort: print to stderr if writing fails
				System.err.println("Failed to write results: " + e.getMessage());
			}
		}
	}

	/** Appends a slow result using current time as timestamp (not used by test currently). */
	public static void appendSlow(String url, long loadMs) {
		String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
		appendSlow(url, loadMs, timestamp);
	}

	/** Appends a slow result with provided navigation start timestamp. */
	public static void appendSlow(String url, long loadMs, String openedTimestamp) {
		String line = String.join(",",
				escape(openedTimestamp),
				escape(url),
				String.valueOf(loadMs)
		);

		synchronized (fileLock) {
			try {
				Files.createDirectories(SLOW_RESULTS_PATH.getParent());
				if (!slowHeaderWritten.get() || !Files.exists(SLOW_RESULTS_PATH)) {
					writeSlowHeader();
				}
				try (BufferedWriter writer = Files.newBufferedWriter(SLOW_RESULTS_PATH, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
					writer.write(line);
					writer.newLine();
				}
			} catch (IOException e) {
				System.err.println("Failed to write slow results: " + e.getMessage());
			}
		}
	}

	/** Writes header to slow-results.csv once. */
	private static void writeSlowHeader() throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(SLOW_RESULTS_PATH, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			if (!slowHeaderWritten.get()) {
				writer.write("timestamp,url,load_ms");
				writer.newLine();
				slowHeaderWritten.set(true);
			}
		}
	}

	/** Writes header to load-results.csv once. */
	private static void writeHeader() throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(RESULTS_PATH, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			if (!headerWritten.get()) {
				writer.write("timestamp,url,load_ms,status,error");
				writer.newLine();
				headerWritten.set(true);
			}
		}
	}

	/** Escapes a CSV field (commas, quotes, newlines) using RFC4180-style quoting. */
	private static String escape(String s) {
		if (s == null) return "";
		String escaped = s.replace("\"", "\"\"");
		if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
			return "\"" + escaped + "\"";
		}
		return escaped;
	}
}


package com.example.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/** Simple sanity test to validate WebDriver setup using Chrome. */
public class SampleTest {
	/** Opens example.com and asserts that the page title contains 'example'. */
	@Test
	public void openExampleDotCom() {
		WebDriverManager.chromedriver().setup();
		WebDriver driver = new ChromeDriver();
		try {
			driver.get("https://www.example.com");
			assertTrue(driver.getTitle().toLowerCase().contains("example"));
		} finally {
			driver.quit();
		}
	}
}


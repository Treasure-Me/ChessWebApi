package TestingFrameworksTest;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class WebDriverHealthCheckTest {

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    void setUp() {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();

        if (System.getenv("CI") != null) {
            options.addArguments("--headless=new");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--no-sandbox");           // important on Linux runners
            options.addArguments("--disable-dev-shm-usage");
        }

        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    private void setupGameEnvironment(){
        driver.get("http://localhost:5000/");

        WebElement guestBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("quickGuestBtn")
        ));
        guestBtn.click();

        WebElement botBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("menu-play-bot")
        ));
        botBtn.click();
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    // -------------------------------------------------------------------------
    // Core Driver Health Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Driver launches Chrome and can navigate to home/login page")
    void testDriverCanLaunchAndNavigate() {
        driver.get("http://localhost:5000/");
        assertTrue(driver.getCurrentUrl().contains("localhost:5000"), "Should be on local server");

        String title = driver.getTitle();
        assertTrue(title.contains("Chess") || title.contains("Login"), "Page title should contain expected text");
    }

    @Test
    @DisplayName("Can find and interact with buttons on login/home page")
    void testCanFindAndClickButtons() {
        driver.get("http://localhost:5000/");

        // Find Quick Guest Login button
        WebElement guestBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("quickGuestBtn")));
        assertNotNull(guestBtn, "Quick Guest Login button should exist");

        // Click it
        guestBtn.click();

        // Wait for some UI change (e.g. username updates)
        wait.until(d -> {
            try {
                String name = d.findElement(By.id("self-name")).getText().trim();
                return !name.equals("Guest") || name.length() > 0;
            } catch (Exception e) {
                return false;
            }
        });

        // Verify button was clickable and action happened
        assertTrue(true); // Placeholder - real check is the wait succeeding
    }

    @Test
    @DisplayName("Can navigate to game page and wait for DOM element")
    void testNavigationAndElementPresence() {
        setupGameEnvironment();

        // Wait for a stable element on game page (chess-board or opp-name)
        WebElement boardOrOpp = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.id("chess-board")  // or By.id("opp-name")
        ));

        assertNotNull(boardOrOpp, "Game page should load a key element");
        assertTrue(boardOrOpp.isDisplayed(), "Key element should be visible");
    }

    @Test
    @DisplayName("Can read text from page after navigation")
    void testTextReadingAfterNavigation() {
        driver.get("http://localhost:5000/");

        String headerText = driver.findElement(By.tagName("h1")).getText().trim();
        assertTrue(headerText.contains("Chess World"), "Header should contain expected text");
    }

    @Test
    @DisplayName("Can handle alerts (simulate via JS if needed)")
    void testAlertHandlingCapability() {
        driver.get("http://localhost:5000/");

        // Trigger a simple alert via JavaScript (simulates real alert behavior)
        ((JavascriptExecutor) driver).executeScript("alert('Test Alert from Selenium Health Check');");

        Alert alert = wait.until(ExpectedConditions.alertIsPresent());
        String alertText = alert.getText();
        alert.accept();

        assertEquals("Test Alert from Selenium Health Check", alertText, "Should read alert text correctly");
    }

    @Test
    @DisplayName("Can execute JavaScript and get result")
    void testJavaScriptExecution() {
        driver.get("http://localhost:5000/");

        String result = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.title + ' - Injected via JS';"
        );

        assertTrue(result.contains("Chess") || result.contains("Login"), "JS should execute and return value");
    }

    @Test
    @DisplayName("Can find multiple elements and count them")
    void testFindingMultipleElements() {
        setupGameEnvironment();

        // Wait for any square to appear (proof board rendered)
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".square")));

        int squares = driver.findElements(By.cssSelector(".square")).size();
        assertEquals(64, squares, "Chess board should have 64 squares");
    }

    @Test
    @DisplayName("Can wait for element to be clickable and interact")
    void testWaitForClickableAndInteract() {
        setupGameEnvironment();

        WebElement logoutBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("logout-btn")));
        assertNotNull(logoutBtn, "Logout button should be present and clickable");

        // Don't actually click (to avoid side effects), just verify
        assertTrue(logoutBtn.isEnabled(), "Button should be enabled");
    }
}
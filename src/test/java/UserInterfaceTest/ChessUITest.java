package UserInterfaceTest;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ChessUITest {

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    void setUp() {
        WebDriverManager.chromedriver().setup();
        driver = new ChromeDriver();
        // For CI / faster runs → uncomment:
        // ChromeOptions options = new ChromeOptions();
        // options.addArguments("--headless=new", "--disable-gpu");
        // driver = new ChromeDriver(options);

        driver.manage().window().maximize();
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private void openBotMode() {
        driver.get("http://localhost:5000/");

        WebElement guestBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("quickGuestBtn")
        ));
        guestBtn.click();

        wait.until(d -> {
            try {
                String username = d.findElement(By.id("self-name")).getText().trim();
                return !username.equals("Guest") && username.length() > 0;
            } catch (Exception e) {
                return false;
            }
        });

        System.out.println("Guest login completed - username: " +
                driver.findElement(By.id("self-name")).getText().trim());

        WebElement botBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("menu-play-bot")
        ));
        botBtn.click();

        wait.until(ExpectedConditions.urlContains("game.html"));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("opp-name")));

        // 5. Verify
        String opponent = driver.findElement(By.id("opp-name")).getText().trim();
        assertEquals("Stockfish", opponent, "Opponent should be Stockfish in bot mode");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("game-status"), "Playing vs Stockfish"));

        System.out.println("Bot mode loaded successfully");
    }

    private void openAnalysisMode(String positionFEN){
        driver.get("http://localhost:5000/");

        WebElement guestBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("quickGuestBtn")
        ));
        guestBtn.click();

        wait.until(d -> {
            try {
                String username = d.findElement(By.id("self-name")).getText().trim();
                return !username.equals("Guest") && username.length() > 0;
            } catch (Exception e) {
                return false;
            }
        });

        System.out.println("Guest login completed - username: " +
                driver.findElement(By.id("self-name")).getText().trim());

        WebElement analysisBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("menu-analysis")
        ));
        analysisBtn.click();

       if (positionFEN != null){
           WebElement fenInput = wait.until(ExpectedConditions.presenceOfElementLocated(
                   By.id("fen-input")
           ));
           fenInput.clear();
           fenInput.sendKeys(positionFEN);
       }

        WebElement load = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("load-board-btn")
        ));
        load.click();

        wait.until(ExpectedConditions.urlContains("game.html"));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("opp-name")));

        System.out.println("Analysis mode loaded successfully");
    }

    private WebElement square(int row, int col) {
        return driver.findElement(By.cssSelector(
                String.format(".square[data-row='%d'][data-col='%d']", row, col)

        ));
    }

    private int countHighlights() {
        return driver.findElements(By.cssSelector(".legal-move, .legal-capture")).size();
    }

    private String getStatus() {
        return driver.findElement(By.id("game-status")).getText().trim();
    }

    private String getOpponentName() {
        return driver.findElement(By.id("opp-name")).getText().trim();
    }

    private String getTurnText() {
        return driver.findElement(By.id("player-turn")).getText().trim();
    }

    private int countMoveHistoryItems() {
        return driver.findElements(By.cssSelector("#move-list > div")).size();
    }

    private boolean isModalVisible(String modalId) {
        try {
            WebElement modal = driver.findElement(By.id(modalId));
            return modal.isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // 1. Basic Loading & Initial State
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Bot mode loads → shows Stockfish + correct initial UI")
    void testBotModeInitialLoadAndUI() {
        openBotMode();

        assertEquals("Stockfish", getOpponentName(), "Opponent should be Stockfish");

        String status = getStatus();
        assertTrue(
                status.contains("Stockfish") || status.contains("Syncing") ||
                        status.contains("Playing") || status.contains("Resumed") ||
                        status.contains("New Game"),
                "Status should relate to bot game. Got: " + status
        );

        assertTrue(getTurnText().toUpperCase().contains("YOUR TURN"),
                "Fresh bot game should start with white's turn");

        // Check that 32 pieces are rendered
        int pieceCount = driver.findElements(By.xpath("//*[contains(@class,'square') and normalize-space(text()) != '']")).size();
        assertEquals(32, pieceCount, "Starting position should show 32 pieces");
    }

    @Test
    @DisplayName("Board orientation: white pieces at bottom in bot mode")
    void testBoardOrientationWhiteAtBottom() {
        openBotMode();

        // Bottom row (visual row 7) should have white pieces (uppercase)
        WebElement a1 = square(7, 0); // a1
        assertEquals("♜", a1.getText().trim(), "a1 should be white rook");

        WebElement h1 = square(7, 7); // h1
        assertEquals("♜", h1.getText().trim(), "h1 should be white rook");

        // Top row (visual row 0) should have black pieces (lowercase)
        WebElement a8 = square(0, 0); // a8
        assertEquals("♖", a8.getText().trim(), "a8 should be black rook");
    }

    // -------------------------------------------------------------------------
    // 2. Move Interaction (Human → White)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Select e2 pawn → legal moves are highlighted")
    void testSelectPawnHighlightsMoves() {
        openAnalysisMode(null);

        WebElement e2 = square(6, 4); // e2
        e2.click();

        wait.until(d -> countHighlights() > 0);

        assertTrue(countHighlights() >= 2, "Pawn on e2 should highlight at least 2 squares");
    }

    @Test
    @DisplayName("Make move e2e4 → move appears in history & bot responds")
    void testHumanMakesMoveAndBotResponds() {
        openBotMode();

        // Select e2
        square(6, 4).click();
        wait.until(d -> countHighlights() > 0);

        // Move to e4
        square(4, 4).click();

        // Wait for move to be processed (history updates)
        wait.until(d -> countMoveHistoryItems() >= 1);

        // Wait for bot to move (polling + engine)
        wait.until(d -> countMoveHistoryItems() >= 2);

        assertTrue(getTurnText().toUpperCase().contains("WHITE"),
                "After bot move, turn should be back to white");
    }

    // -------------------------------------------------------------------------
    // 3. Promotion
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Pawn promotion → white promotion modal appears")
    void testWhitePawnPromotionShowsModal() throws InterruptedException {
        openAnalysisMode("8/P7/8/1k6/8/8/8/K7 w - - 0 1");

        try {
            square(1, 0).click(); // a7 (adjust row/col)
            wait.until(d -> countHighlights() > 0);
            square(0, 0).click(); // a8

            boolean modalVisible = wait.until(d ->
                    isModalVisible("white-promotion-modal")
            );

            assertTrue(modalVisible, "White promotion modal should appear");
        } catch (TimeoutException e) {
            fail("Promotion scenario not reachable in test – consider using FEN setup");
        }
    }

    // -------------------------------------------------------------------------
    // 4. Resign & Game Over
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Resign button → shows confirmation → game over modal")
    void testResignFlow() {
        openBotMode();

        WebElement resignBtn = driver.findElement(By.id("resign"));
        resignBtn.click();

        // Accept confirmation dialog
        Alert alert = wait.until(ExpectedConditions.alertIsPresent());
        alert.accept();

        // Wait for game over modal
        WebElement gameOverModal = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.id("game-over-modal")
        ));

        assertTrue(gameOverModal.isDisplayed(), "Game over modal should be visible");

        String modalMessage = driver.findElement(By.id("modal-message")).getText();
        assertTrue(
                modalMessage.contains("Black wins") ||
                        modalMessage.contains("resigned") ||
                        modalMessage.contains("Game Over"),
                "Game over message should indicate resignation. Got: " + modalMessage
        );
    }

    // -------------------------------------------------------------------------
    // 5. Polling & Sync
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Polling dot changes color during sync")
    void testPollingIndicatorChangesColor() {
        openBotMode();

        WebElement syncDot = driver.findElement(By.id("sync-status"));

        // Wait for at least one poll cycle (green flash)
        boolean turnedGreen = wait.until(d -> {
            String bg = syncDot.getCssValue("background-color");
            return bg.contains("0, 255, 0") || bg.contains("0f0") || bg.contains("rgb(0, 255, 0)");
        });

        assertTrue(turnedGreen, "Sync dot should turn green during polling");

        // Then back to gray
        wait.until(d -> {
            String bg = syncDot.getCssValue("background-color");
            return bg.contains("gray") || bg.contains("128, 128, 128");
        });
    }

    // -------------------------------------------------------------------------
    // 6. Additional useful tests (add when ready)
    // -------------------------------------------------------------------------

    // @Test
    // void testMoveHistoryUpdatesAfterEachMove() { ... }

    // @Test
    // void testGameOverShownOnCheckmate() { ... }

    // @Test
    // void testAnalysisModeShowsEvalBar() { ... }

    // @Test
    // void testBoardFlipsWhenPlayingAsBlackInOnlineMode() { ... }
}
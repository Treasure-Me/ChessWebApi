package UserInterfaceTest;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.sql.Driver;
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


    private void login(WebDriver driver){
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
    }

    private void openBotMode() {
        login(driver);

        WebElement botBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("menu-play-bot")
        ));
        botBtn.click();

        wait.until(ExpectedConditions.urlContains("game.html"));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("opp-name")));

        String opponent = driver.findElement(By.id("opp-name")).getText().trim();
        assertEquals("Stockfish", opponent, "Opponent should be Stockfish in bot mode");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("game-status"), "Playing vs Stockfish"));

        System.out.println("Bot mode loaded successfully");
    }

    private void openAnalysisMode(String positionFEN){
        login(driver);

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

    private void loginAsGuest(WebDriver d, WebDriverWait w) {
        d.get("http://localhost:5000/");

        WebElement guestBtn = w.until(ExpectedConditions.elementToBeClickable(By.id("quickGuestBtn")));
        guestBtn.click();

        w.until(driver -> {
            try {
                String name = driver.findElement(By.id("self-name")).getText().trim();
                return !name.equals("Guest") && name.length() > 0;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private void startMultiplayerGame(WebDriver d, WebDriverWait w) {
        WebElement newGameBtn = w.until(ExpectedConditions.elementToBeClickable(By.id("menu-new-game")));
        newGameBtn.click();

        w.until(ExpectedConditions.urlContains("game.html"));
        w.until(ExpectedConditions.presenceOfElementLocated(By.id("opp-name")));
    }

    private WebElement square(WebDriver d, int row, int col) {
        return d.findElement(By.cssSelector(
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

    private String getMyColor(WebDriver d) {
        return d.findElement(By.id("game-status")).getText().trim().toUpperCase();
    }

    private List<WebElement> moveHistoryItems() {
        return driver.findElements(By.cssSelector("#move-list > div"));
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
        WebElement a1 = square(driver, 7, 0); // a1
        assertEquals("♜", a1.getText().trim(), "a1 should be white rook");

        WebElement h1 = square(driver, 7, 7); // h1
        assertEquals("♜", h1.getText().trim(), "h1 should be white rook");

        // Top row (visual row 0) should have black pieces (lowercase)
        WebElement a8 = square(driver, 0, 0); // a8
        assertEquals("♖", a8.getText().trim(), "a8 should be black rook");
    }

    // -------------------------------------------------------------------------
    // 2. Move Interaction (Human → White)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Select e2 pawn → legal moves are highlighted")
    void testSelectPawnHighlightsMoves() {
        openAnalysisMode(null);

        WebElement e2 = square(driver, 6, 4); // e2
        e2.click();

        wait.until(d -> countHighlights() > 0);

        assertTrue(countHighlights() >= 2, "Pawn on e2 should highlight at least 2 squares");
    }

    @Test
    @DisplayName("Make move e2e4 → move appears in history & bot responds")
    void testHumanMakesMoveAndBotResponds() {
        openBotMode();

        // Select e2
        square(driver, 6, 4).click();
        wait.until(d -> countHighlights() > 0);

        // Move to e4
        square(driver, 4, 4).click();

        // Wait for move to be processed (history updates)
        wait.until(d -> moveHistoryItems().size() >= 1);

        // Wait for bot to move (polling + engine)
        wait.until(d -> moveHistoryItems().size() >= 2);

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
            square(driver, 1, 0).click(); // a7 (adjust row/col)
            wait.until(d -> countHighlights() > 0);
            square(driver, 0, 0).click(); // a8

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

    @Test
    void testMoveHistoryUpdatesAfterEachMove() {
        openBotMode();

        square(driver, 6, 4).click();
        wait.until(d -> countHighlights() > 0);
        square(driver, 4, 4).click();

        wait.until(d -> moveHistoryItems().size() >= 2);

        square(driver, 7, 6).click();
        wait.until(d -> countHighlights() > 0);
        square(driver, 5, 5).click();

        wait.until(d -> moveHistoryItems().size() >= 4);

        List<WebElement> moves = driver.findElements(By.cssSelector("#move-list > div"));

        assertEquals("e2 -> e4", moves.get(0).getText());
        assertEquals("g1 -> f3", moves.get(2).getText());
    }

    @Test
    void testGameOverShownOnCheckmate() {
        openAnalysisMode("r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/5Q2/PPPP1PPP/RNB1KBNR w KQkq - 4 4");

        square(driver, 5, 5).click();
        wait.until(d -> countHighlights() > 0);
        square(driver, 1, 5).click();

        wait.until(d -> moveHistoryItems().size() >= 1);

        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("game-over-modal")));
    }

    @Test
    void testAnalysisModeShowsEvalBar() {
        openAnalysisMode(null);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("eval-bar")));

        openBotMode();

        WebElement modal = driver.findElement(By.id("game-over-modal"));
        String displayValue = modal.getCssValue("display");

        assertEquals("none", displayValue);
    }

    @Test
    @Disabled
    @DisplayName("Board flips correctly for White and Black players (two separate browsers)")
    void testBoardFlipsAccordingToColor() {
        WebDriver driverWhite = new ChromeDriver();
        WebDriverWait waitWhite = new WebDriverWait(driverWhite, Duration.ofSeconds(20));
        driverWhite.manage().window().maximize();

        // === Browser 2 (will become Black) ===
        WebDriver driverBlack = new ChromeDriver();
        WebDriverWait waitBlack = new WebDriverWait(driverBlack, Duration.ofSeconds(20));
        driverBlack.manage().window().maximize();

        try {
            loginAsGuest(driverWhite, waitWhite);
            loginAsGuest(driverBlack, waitBlack);

            startMultiplayerGame(driverWhite, waitWhite);
            startMultiplayerGame(driverBlack, waitBlack);

            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            boolean whiteIsWhite = getMyColor(driverWhite).contains("WHITE");
            WebDriver whiteDriver = whiteIsWhite ? driverWhite : driverBlack;
            WebDriver blackDriver = whiteIsWhite ? driverBlack : driverWhite;

            // === Verify White sees normal board (white pieces at bottom) ===
            assertEquals("♜", square(whiteDriver, 7, 0).getText().trim(), "White player: a1 should be white rook ♖");
            assertEquals("♖", square(whiteDriver, 0, 0).getText().trim(), "White player: a8 should be black rook ♜");

            // === Verify Black sees flipped board (black pieces at bottom) ===
            assertEquals("♖", square(blackDriver, 7, 0).getText().trim(), "Black player: a1 should be black rook ♜");
            assertEquals("♜", square(blackDriver, 0, 0).getText().trim(), "Black player: a8 should be white rook ♖");

        } finally {
            // Clean up both browsers
            driverWhite.quit();
            driverBlack.quit();
        }
    }
}
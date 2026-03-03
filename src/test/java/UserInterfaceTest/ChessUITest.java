package UserInterfaceTest;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class ChessUITest {

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    void setUp() {
        WebDriverManager.chromedriver().setup();
//        ChromeOptions options = new ChromeOptions();
//        options.addArguments("--headless=new", "--disable-gpu");
        driver = new ChromeDriver();

        driver.manage().window().maximize();
        wait = new WebDriverWait(driver, Duration.ofSeconds(60));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }


    private void login(WebDriver driver, WebDriverWait wait){
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
        login(driver, wait);

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
        login(driver, wait);

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

    private void openMultiplayerMode(WebDriver driver, WebDriverWait wait){
        login(driver, wait);
        WebElement botBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.id("menu-new-game")
        ));
        botBtn.click();

        wait.until(ExpectedConditions.urlContains("game.html"));
        System.out.println("Multiplayer mode loaded successfully");
    }

    private WebElement square(WebDriver driver, int row, int col) {
        return driver.findElement(By.cssSelector(String.format(".square[data-row='%d'][data-col='%d']", row, col)));
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

    private String getMyColor(WebDriver driver){
        return driver.findElement(By.id("game-status")).getText().trim();
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

        assertTrue(getTurnText().toUpperCase().contains("YOUR TURN"),"Fresh bot game should start with white's turn");

        int pieceCount = driver.findElements(By.xpath("//*[contains(@class,'square') and normalize-space(text()) != '']")).size();
        assertEquals(32, pieceCount, "Starting position should show 32 pieces");
    }

    @Test
    @DisplayName("Board orientation: white pieces at bottom in bot mode")
    void testBoardOrientationWhiteAtBottom() {
        openBotMode();

        WebElement a1 = square(driver, 7, 0);
        assertEquals("♜", a1.getText().trim(), "a1 should be white rook");

        WebElement h1 = square(driver, 7, 7);
        assertEquals("♜", h1.getText().trim(), "h1 should be white rook");

        WebElement a8 = square(driver, 0, 0);
        assertEquals("♖", a8.getText().trim(), "a8 should be black rook");
    }

    @Test
    @DisplayName("Select e2 pawn → legal moves are highlighted")
    void testSelectPawnHighlightsMoves() {
        openAnalysisMode(null);

        WebElement e2 = square(driver, 6, 4);
        e2.click();

        wait.until(d -> countHighlights() > 0);

        assertTrue(countHighlights() >= 2, "Pawn on e2 should highlight at least 2 squares");
    }

    @Test
    @DisplayName("Make move e2e4 → move appears in history & bot responds")
    void testHumanMakesMoveAndBotResponds() {
        openBotMode();

        square(driver, 6, 4).click();
        wait.until(d -> countHighlights() > 0);

        square(driver, 4, 4).click();

        wait.until(d -> moveHistoryItems().size() >= 1);

        wait.until(d -> moveHistoryItems().size() >= 2);

        assertTrue(getTurnText().toUpperCase().contains("WHITE"),
                "After bot move, turn should be back to white");
    }


    @Test
    @DisplayName("Pawn promotion → white promotion modal appears")
    void testWhitePawnPromotionShowsModal() {
        openAnalysisMode("8/P7/8/1k6/8/8/8/K7 w - - 0 1");

        try {
            square(driver, 1, 0).click();
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

    @Test
    @DisplayName("Resign button → shows confirmation → game over modal")
    void testResignFlow() {
        openBotMode();

        WebElement resignBtn = driver.findElement(By.id("resign"));
        resignBtn.click();

        Alert alert = wait.until(ExpectedConditions.alertIsPresent());
        alert.accept();

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

    @Test
    @DisplayName("Polling dot changes color during sync")
    void testPollingIndicatorChangesColor() {
        openBotMode();

        WebElement syncDot = driver.findElement(By.id("sync-status"));

        boolean turnedGreen = wait.until(d -> {
            String bg = syncDot.getCssValue("background-color");
            return bg.contains("0, 255, 0") || bg.contains("0f0") || bg.contains("rgb(0, 255, 0)");
        });

        assertTrue(turnedGreen, "Sync dot should turn green during polling");

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
    @DisplayName("Board flips correctly for White and Black (two separate browsers)")
    void testBoardFlipsAccordingToColor() {
        WebDriver driverWhite = new ChromeDriver();
        WebDriver driverBlack = new ChromeDriver();
        WebDriverWait waitWhite = new WebDriverWait(driverWhite, Duration.ofSeconds(25));
        WebDriverWait waitBlack = new WebDriverWait(driverBlack, Duration.ofSeconds(25));

        try {
            driverWhite.manage().window().maximize();
            driverBlack.manage().window().maximize();

            login(driverWhite, waitWhite);
            login(driverBlack, waitBlack);

            openMultiplayerMode(driverWhite, waitWhite);
            openMultiplayerMode(driverBlack, waitBlack);

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            String whiteColor = getMyColor(driverWhite);
            WebDriver whiteBrowser = whiteColor.contains("WHITE") ? driverWhite : driverBlack;
            WebDriver blackBrowser = whiteColor.contains("WHITE") ? driverBlack : driverWhite;

            assertEquals("♜", square(whiteBrowser, 7, 0).getText().trim(), "White sees white rook on a1");
            assertEquals("♖", square(whiteBrowser, 0, 0).getText().trim(), "White sees black rook on a8");

            assertEquals("♜", square(blackBrowser, 7, 0).getText().trim(), "Black sees black rook on a1");
            assertEquals("♖", square(blackBrowser, 0, 0).getText().trim(), "Black sees white rook on a8");

        } finally {
            driverWhite.quit();
            driverBlack.quit();
        }
    }
}
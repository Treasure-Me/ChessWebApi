package PGNToSQL.PGN;

import java.util.*;
import java.util.regex.*;

public class PGNGame {

    private final Map<String, String> headers = new LinkedHashMap<>();
    private final List<String> moves = new ArrayList<>();
    private final String rawMovesText;
    private final int moveCount;

    private static final Pattern HEADER_PATTERN = Pattern.compile("\\[(\\w+)\\s+\"([^\"]*)\"\\]");
    private static final Pattern MOVE_NUMBER_PATTERN = Pattern.compile("\\d+\\.");
    private static final Set<String> RESULTS = Set.of("1-0", "0-1", "1/2-1/2", "*");

    /**
     * Creates a PGNGame from a single game string (the part between two [Event ...] tags)
     */
    public PGNGame(String pgnGameText) {
        String text = pgnGameText.trim();
        StringBuilder movesBuilder = new StringBuilder();

        String[] lines = text.split("\\r?\\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("[")) {
                parseHeader(line);
            } else {
                // Move section
                movesBuilder.append(line).append(" ");
            }
        }

        this.rawMovesText = cleanMoves(movesBuilder.toString());
        this.moves.addAll(splitMoves(this.rawMovesText));
        this.moveCount = this.moves.size();
    }

    private void parseHeader(String line) {
        Matcher m = HEADER_PATTERN.matcher(line);
        if (m.find()) {
            String key = m.group(1).trim();
            String value = m.group(2).trim();
            headers.put(key, value);
        }
    }

    private String cleanMoves(String movesText) {
        String cleaned = MOVE_NUMBER_PATTERN.matcher(movesText).replaceAll("");
        for (String result : RESULTS) {
            cleaned = cleaned.replace(result, "");
        }
        cleaned = cleaned.replaceAll("\\{.*?\\}", "")     // comments
                .replaceAll("\\$\\d+", "")           // NAGs
                .replaceAll("[?!]+", "")             // annotations
                .replaceAll("\\(.*?\\)", "")         // variations
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned;
    }

    private List<String> splitMoves(String movesText) {
        return Arrays.stream(movesText.split("\\s+"))
                .filter(m -> !m.isEmpty() && !RESULTS.contains(m))
                .toList();
    }

    // ====================== Public API ======================

    public String getHeader(String key) {
        return headers.getOrDefault(key, "");
    }

    public Map<String, String> getAllHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public List<String> getMoves() {
        return moves;
    }

    public String getMovesText() {
        return rawMovesText;
    }

    public int getMoveCount() {
        return moveCount;
    }

    public String getWhite() {
        return getHeader("White");
    }

    public String getBlack() {
        return getHeader("Black");
    }

    public String getResult() {
        return getHeader("Result");
    }

    public String getDate() {
        return getHeader("Date");
    }

    public String getEvent() {
        return getHeader("Event");
    }

    public String getECO() {
        return getHeader("ECO");
    }

    /**
     * Returns the full PGN text of this game (headers + moves)
     */
    public String toPGNString() {
        StringBuilder sb = new StringBuilder();
        headers.forEach((k, v) -> sb.append("[").append(k).append(" \"").append(v).append("\"]\n"));
        sb.append("\n").append(rawMovesText).append(" ").append(getResult());
        return sb.toString();
    }
}
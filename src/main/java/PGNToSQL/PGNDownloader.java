package PGNToSQL;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class PGNDownloader {

    public static void downloadSampleGames() {
        String[] urls = {
                "http://www.pgnmentor.com/players/Fischer.pgn",
                "http://www.pgnmentor.com/players/Carlsen.pgn",
                "http://www.pgnmentor.com/players/Kasparov.pgn"
        };

        for (String url : urls) {
            try {
                String fileName = url.substring(url.lastIndexOf("/") + 1);
                System.out.println("Downloading: " + fileName);

                URL website = new URL(url);
                Files.copy(website.openStream(), Paths.get("pgn-files/raw/" + fileName),
                        StandardCopyOption.REPLACE_EXISTING);

                Thread.sleep(1000); // Be nice to the server
            } catch (Exception e) {
                System.err.println("Failed to download: " + url);
            }
        }
    }

    public static void main(String[] args) {
        // Create directories
        new java.io.File("pgn-files/raw").mkdirs();
        new java.io.File("pgn-files/processed").mkdirs();
        new java.io.File("pgn-files/converted").mkdirs();

        downloadSampleGames();
        System.out.println("Download complete!");
    }
}
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Deterministic visual test cards; the downloaded image is not packaged in the APK. */
public final class MakeImageAssets {
    public static void main(String[] args) throws Exception {
        create(Path.of("demo/src/main/assets/image-before.png"), false);
        create(Path.of("test-assets/image-after.png"), true);
    }

    private static void create(Path path, boolean updated) throws Exception {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(900, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(updated ? 0xE7F1FF : 0xFFF0DF));
        g.fillRect(0, 0, 900, 500);
        g.setColor(new Color(0x15253D));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 52));
        g.drawString(updated ? "AFTER IMAGE" : "BEFORE IMAGE", 48, 80);
        g.setColor(new Color(updated ? 0x2255DD : 0xEA602D));
        if (updated) {
            g.fillRoundRect(335, 120, 230, 230, 38, 38);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(19, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(385, 235, 432, 280);
            g.drawLine(432, 280, 520, 185);
        } else g.fillOval(335, 120, 230, 230);
        g.setColor(new Color(0x15253D));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 30));
        g.drawString(updated ? "Downloaded from GitHub" : "Bundled in the base APK", 48, 425);
        g.dispose();
        ImageIO.write(image, "png", path.toFile());
        System.out.println(path);
    }
}

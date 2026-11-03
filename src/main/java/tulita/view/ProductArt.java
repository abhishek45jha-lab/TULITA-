package tulita.view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;

/** Lightweight original vector placeholders, not photographs of actual products. */
public final class ProductArt extends Canvas {
    public ProductArt(String kind, double width, double height) {
        super(width, height);
        GraphicsContext g = getGraphicsContext2D();
        g.scale(width / 260, height / 150);
        g.setFill(Color.web("#e7e9e7"));
        g.fillOval(60, 124, 144, 11);
        Color ink = Color.web("#243951"), gold = Color.web("#b59b62"), white = Color.web("#fafaf7");
        g.setLineWidth(9); g.setStroke(ink); g.setFill(ink);
        switch (kind) {
            case "headphones" -> {
                g.strokeArc(83, 19, 96, 105, 0, 180, ArcType.OPEN);
                g.setStroke(Color.web("#70818c")); g.setLineWidth(3); g.strokeArc(83, 17, 96, 100, 10, 160, ArcType.OPEN);
                g.setFill(ink); g.fillRoundRect(76, 65, 29, 61, 15, 15); g.fillRoundRect(157, 65, 29, 61, 15, 15);
                g.setFill(Color.web("#526578")); g.fillRoundRect(79, 70, 13, 48, 10, 10); g.fillRoundRect(160, 70, 13, 48, 10, 10);
            }
            case "shoe" -> {
                g.setFill(Color.web("#d2d8d6"));
                g.fillPolygon(new double[]{49,62,79,105,133,158,186,212,220,214,72,48}, new double[]{103,74,62,80,66,86,98,103,119,127,128,119},12);
                g.setFill(white); g.fillRoundRect(45, 115, 178, 15, 9, 9);
                g.setFill(ink); g.fillPolygon(new double[]{86,109,161,185,130},new double[]{98,109,85,87,115},5);
                g.setStroke(white); g.setLineWidth(3);
                for (int i=0;i<4;i++) g.strokeLine(109+i*10,87+i*2,119+i*10,79+i*3);
                g.setStroke(Color.web("#879491")); g.setLineWidth(1); g.strokeLine(54,124,211,124);
            }
            case "phone" -> {
                g.setFill(ink); g.fillRoundRect(92, 9, 75, 126, 15, 15);
                g.setFill(Color.web("#607586")); g.fillRoundRect(98, 15, 63, 113, 11, 11);
                g.setFill(Color.web("#acbdbb")); g.fillOval(107, 45, 52, 66);
                g.setFill(ink); g.fillRoundRect(117, 19, 25, 7, 7, 7);
            }
            case "earbuds" -> {
                g.setFill(white); g.fillRoundRect(77, 75, 111, 57, 26, 26);
                g.setStroke(Color.web("#c8cfcc")); g.setLineWidth(2); g.strokeRoundRect(77,75,111,57,26,26); g.strokeLine(79,93,185,93);
                g.setFill(ink); g.fillRoundRect(97,29,14,45,10,10); g.fillRoundRect(152,29,14,45,10,10);
                g.fillOval(88,24,27,22); g.fillOval(149,24,27,22); g.setFill(gold); g.fillOval(129,110,5,5);
            }
            case "mouse" -> {
                g.setFill(Color.web("#e6e3dc")); g.fillRoundRect(92,22,78,112,60,60);
                g.setStroke(Color.web("#bac4c1")); g.setLineWidth(2); g.strokeRoundRect(92,22,78,112,60,60);
                g.strokeLine(131,25,131,66); g.setFill(ink); g.fillRoundRect(127,41,8,20,5,5);
            }
            case "speaker" -> {
                g.setFill(ink); g.fillRoundRect(61,47,142,75,34,34);
                g.setStroke(Color.web("#577284")); g.setLineWidth(1);
                for(int i=0;i<23;i++) g.strokeLine(76+i*5,56,76+i*5,113);
                g.setFill(gold); g.fillRoundRect(116,73,34,23,4,4); g.setFill(white); g.fillText("♫",126,90);
            }
            case "watch" -> {
                g.setFill(gold); g.fillRoundRect(114,6,34,134,7,7);
                g.setFill(ink); g.fillOval(94,39,75,75); g.setFill(white); g.fillOval(100,45,63,63);
                g.setStroke(ink); g.setLineWidth(3); g.strokeLine(131,76,131,55); g.strokeLine(131,76,149,85);
                g.setFill(gold); g.fillOval(128,73,6,6);
            }
            case "bag" -> {
                g.setStroke(ink); g.setLineWidth(5); g.strokeRoundRect(113,17,37,40,12,12);
                g.setFill(Color.web("#71816a")); g.fillRoundRect(84,32,96,102,25,25);
                g.setFill(Color.web("#526448")); g.fillRoundRect(96,82,72,42,12,12);
                g.setFill(gold); g.fillRoundRect(121,53,24,13,2,2);
            }
            case "glasses" -> {
                g.setStroke(gold); g.setLineWidth(6); g.strokeLine(47,65,218,65);
                g.setFill(ink); g.fillRoundRect(54,60,65,47,18,18); g.fillRoundRect(143,60,65,47,18,18);
                g.setStroke(ink); g.strokeArc(115,62,31,28,0,180,ArcType.OPEN);
                g.setStroke(Color.web("#657485")); g.setLineWidth(2); g.strokeLine(65,69,95,69); g.strokeLine(154,69,184,69);
            }
            case "catalog" -> {
                g.setFill(Color.web("#d4cbb7")); g.fillRoundRect(86,33,91,91,10,10);
                g.setStroke(gold); g.setLineWidth(3); g.strokeLine(87,61,176,61); g.strokeLine(132,34,132,61);
                g.setFill(white); g.fillRoundRect(117,79,31,22,3,3); g.setFill(ink); g.fillText("t.",126,95);
            }
            case "jeans" -> {
                g.setFill(Color.web("#3e5872"));
                g.fillPolygon(new double[]{94,165,175,143,132,122,85},new double[]{19,19,128,132,65,132,128},7);
                g.setStroke(gold); g.setLineWidth(1); g.strokeLine(96,30,163,30); g.strokeLine(131,32,132,64);
            }
            default -> {
                g.setFill(kind.equals("hoodie") ? Color.web("#bbae92") : Color.web("#667a68"));
                g.fillPolygon(new double[]{99,116,146,164,196,178,161,161,101,101,82,65},new double[]{28,34,34,28,58,80,66,130,130,66,80,58},12);
                g.setStroke(white); g.setLineWidth(3); g.strokeArc(115,22,33,24,180,180,ArcType.OPEN);
                if(kind.equals("hoodie")) { g.strokeLine(124,45,122,70); g.strokeLine(139,45,141,70); g.strokeRoundRect(113,89,38,22,7,7); }
            }
        }
    }
}

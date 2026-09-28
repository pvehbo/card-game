package org.example.card.ui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.example.card.service.StatsService;

/**
 * 战绩窗口（W1）：统计表 + 成就列表（未解锁置灰）。
 * 只读快照，不写回。
 */
public final class StatsView {

    private StatsView() {
    }

    public static void show(Window owner, StatsService stats) {
        Stage stage = new Stage();
        stage.setTitle("战绩与成就");
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox statBox = new VBox(4);
        statBox.getChildren().add(new Label("总场次 " + stats.games()
                + " · 胜 " + stats.wins() + winRate(stats.wins(), stats.games())));
        statBox.getChildren().add(new Label("简单 " + recordOf(stats, "EASY")
                + " · 普通 " + recordOf(stats, "NORMAL")
                + " · 困难 " + recordOf(stats, "HARD")));
        statBox.getChildren().add(new Label("先手 " + recordOf2(stats.firstWins(), stats.firstGames())
                + " · 后手 " + recordOf2(stats.secondWins(), stats.secondGames())));
        statBox.getChildren().add(new Label("当前连胜 " + stats.streak()
                + " · 最高 " + stats.bestStreak()));

        ListView<String> achievementView = new ListView<>();
        for (StatsService.Achievement achievement : StatsService.Achievement.values()) {
            boolean unlocked = stats.unlockedAchievementIds().contains(achievement.id());
            achievementView.getItems().add((unlocked ? "【已解锁】" : "（未解锁）")
                    + achievement.displayName() + "——" + achievement.desc());
        }

        Button closeButton = new Button("关闭");
        closeButton.getStyleClass().add("btn");
        closeButton.setOnAction(e -> stage.close());
        HBox buttons = new HBox(closeButton);
        buttons.setAlignment(Pos.CENTER);

        VBox root = new VBox(10, new Label("战绩"), statBox,
                new Label("成就（" + stats.unlockedAchievementIds().size() + "/"
                        + StatsService.Achievement.values().length + "）"),
                achievementView, buttons);
        root.setPadding(new Insets(12));
        root.setAlignment(Pos.TOP_CENTER);
        Scene scene = new Scene(root, 420, 480);
        var css = StatsView.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static String recordOf(StatsService stats, String level) {
        return recordOf2(stats.winsOf(level), stats.gamesOf(level));
    }

    private static String recordOf2(int wins, int games) {
        return wins + "胜" + games + "场" + winRate(wins, games);
    }

    private static String winRate(int wins, int games) {
        if (games == 0) {
            return "";
        }
        return "（" + (wins * 100 / games) + "%）";
    }
}

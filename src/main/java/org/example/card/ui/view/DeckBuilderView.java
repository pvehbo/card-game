package org.example.card.ui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.example.card.data.DeckBuilder;
import org.example.card.model.Card;

/**
 * 牌组构筑窗口（B-7）：左卡库（搜索+添加）→ 右牌组（移除+计数+费用曲线）。
 * 非法完成直接红字提示，不关窗；合法才回调 id 表。
 */
public final class DeckBuilderView {

    private DeckBuilderView() {
    }

    public static void show(Window owner, List<String> initialIds, Consumer<List<String>> onDone,
            String stylesheetUrl) {
        DeckBuilder builder = new DeckBuilder();
        if (initialIds != null && !initialIds.isEmpty()) {
            try {
                builder.loadIds(new ArrayList<>(initialIds));
            } catch (IllegalStateException ex) {
                builder.useStandard();
            }
        } else {
            builder.useStandard();
        }

        Stage stage = new Stage();
        stage.setTitle("牌组构筑（30 张）");
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }

        // ---- 左：卡库 ----
        TextField search = new TextField();
        search.setPromptText("搜索名称/描述/id");
        ListView<String> libraryView = new ListView<>();
        List<Card> shown = new ArrayList<>();
        Runnable refreshLibrary = () -> {
            String keyword = search.getText() == null ? "" : search.getText().trim();
            shown.clear();
            libraryView.getItems().clear();
            for (Card card : builder.library()) {
                if (keyword.isEmpty() || card.getName().contains(keyword)
                        || card.getDescription().contains(keyword) || card.getId().contains(keyword)) {
                    shown.add(card);
                    libraryView.getItems().add(rowOf(card));
                }
            }
        };
        search.textProperty().addListener((obs, old, value) -> refreshLibrary.run());
        refreshLibrary.run();
        Button addButton = new Button("添加 →");
        addButton.getStyleClass().add("btn");
        VBox left = new VBox(6, new Label("卡库"), search, libraryView, addButton);
        left.setAlignment(Pos.TOP_CENTER);

        // ---- 右：牌组 ----
        ListView<String> deckView = new ListView<>();
        Label countLabel = new Label();
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-text");
        HBox curveBox = new HBox(4);
        curveBox.setAlignment(Pos.BOTTOM_CENTER);
        Runnable refreshDeck = () -> {
            deckView.getItems().clear();
            for (Card card : builder.picks()) {
                deckView.getItems().add(rowOf(card));
            }
            countLabel.setText("已选 " + builder.picks().size() + "/" + DeckBuilder.DECK_SIZE);
            curveBox.getChildren().clear();
            int[] curve = builder.curve();
            for (int cost = 0; cost < curve.length; cost++) {
                VBox column = new VBox(2);
                column.setAlignment(Pos.BOTTOM_CENTER);
                Region bar = new Region();
                bar.setStyle("-fx-background-color: #6fc3ff; -fx-background-radius: 3;");
                bar.setPrefSize(16, Math.max(2, curve[cost] * 6));
                Label count = new Label(curve[cost] == 0 ? "" : String.valueOf(curve[cost]));
                Label fee = new Label(String.valueOf(cost));
                column.getChildren().addAll(bar, count, fee);
                curveBox.getChildren().add(column);
            }
            builder.validate().ifPresentOrElse(errorLabel::setText, () -> errorLabel.setText(""));
        };
        refreshDeck.run();

        addButton.setOnAction(e -> {
            int index = libraryView.getSelectionModel().getSelectedIndex();
            if (index >= 0 && builder.add(shown.get(index))) {
                refreshDeck.run();
            }
        });
        Button removeButton = new Button("← 移除");
        removeButton.getStyleClass().add("btn");
        removeButton.setOnAction(e -> {
            int index = deckView.getSelectionModel().getSelectedIndex();
            if (index >= 0 && builder.remove(builder.picks().get(index))) {
                refreshDeck.run();
            }
        });
        Button clearButton = new Button("清空");
        clearButton.getStyleClass().add("btn");
        clearButton.setOnAction(e -> {
            builder.clear();
            refreshDeck.run();
        });
        Button defaultButton = new Button("默认");
        defaultButton.getStyleClass().add("btn");
        defaultButton.setOnAction(e -> {
            builder.useStandard();
            refreshDeck.run();
        });
        Button doneButton = new Button("完成");
        doneButton.getStyleClass().addAll("btn", "btn-primary");
        doneButton.setOnAction(e -> {
            var reason = builder.validate();
            if (reason.isPresent()) {
                errorLabel.setText(reason.get());
                return;
            }
            onDone.accept(builder.ids());
            stage.close();
        });
        Button cancelButton = new Button("取消");
        cancelButton.getStyleClass().add("btn");
        cancelButton.setOnAction(e -> stage.close());
        HBox deckButtons = new HBox(6, removeButton, clearButton, defaultButton);
        deckButtons.setAlignment(Pos.CENTER);
        HBox finishButtons = new HBox(6, doneButton, cancelButton);
        finishButtons.setAlignment(Pos.CENTER);
        VBox right = new VBox(6, countLabel, deckView, deckButtons, curveBox, errorLabel, finishButtons);
        right.setAlignment(Pos.TOP_CENTER);

        HBox root = new HBox(12, left, right);
        root.setPadding(new Insets(12));
        root.setAlignment(Pos.TOP_CENTER);
        Scene scene = new Scene(root, 680, 520);
        if (stylesheetUrl != null) {
            scene.getStylesheets().add(stylesheetUrl);
        }
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static String rowOf(Card card) {
        return card.getName() + "（" + card.getCost() + "费）·" + card.getDescription();
    }
}

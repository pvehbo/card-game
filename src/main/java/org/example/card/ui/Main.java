package org.example.card.ui;

import javafx.application.Application;

/**
 * 启动入口（非 Application 子类）。
 * JavaFX 的 Application 主类在 classpath/打包运行时下会报
 * “JavaFX runtime components are missing”，必须由普通主类调用 launch()。
 */
public class Main {

    public static void main(String[] args) {
        Application.launch(CardGameApp.class, args);
    }
}
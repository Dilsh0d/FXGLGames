/*
 * The MIT License (MIT)
 *
 * FXGL - JavaFX Game Library
 *
 * Copyright (c) 2015-2017 AlmasB (almaslvl@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.almasb.fxglgames.spacerunner;

import com.almasb.fxgl.animation.Animation;
import com.almasb.fxgl.animation.Interpolators;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.view.ScrollingBackgroundView;
import com.almasb.fxgl.extra.entity.components.HealthComponent;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.settings.GameSettings;
import com.almasb.fxgl.texture.Texture;
import com.almasb.fxgl.ui.ProgressBar;
import com.almasb.fxglgames.spacerunner.ai.AIPointComponent;
import com.almasb.fxglgames.spacerunner.ai.SquadAI;
import com.almasb.fxglgames.spacerunner.collision.BulletAIPointHandler;
import com.almasb.fxglgames.spacerunner.collision.BulletEnemyHandler;
import com.almasb.fxglgames.spacerunner.collision.PlayerBulletHandler;
import com.almasb.fxglgames.spacerunner.collision.PlayerPowerupHandler;
import com.almasb.fxglgames.spacerunner.components.PlayerComponent;
import com.almasb.fxglgames.spacerunner.level.Level;
import javafx.beans.binding.Bindings;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.Map;

import static com.almasb.fxgl.app.DSLKt.*;

/**
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
public class SpaceRunnerApp extends GameApplication {

    private PlayerComponent playerComponent;
    private Level level;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("Space Runner");
        settings.setVersion("0.1");
        settings.setWidth(1000);
        settings.setHeight(700);
        settings.setConfigClass(GameConfig.class);
    }

    @Override
    protected void preInit() {
        loopBGM("bgm.mp3");
    }

    @Override
    protected void initInput() {
        getInput().addAction(new UserAction("Move Up") {
            @Override
            protected void onAction() {
                playerComponent.up();
            }
        }, KeyCode.W);

        getInput().addAction(new UserAction("Move Down") {
            @Override
            protected void onAction() {
                playerComponent.down();
            }
        }, KeyCode.S);

        getInput().addAction(new UserAction("Change Weapon") {
            @Override
            protected void onAction() {
                playerComponent.changeWeapon();

                weaponTexture.setImage(image("sprite_laser.png"));
                bullets.textProperty().bind(getip("laser").asString("x %d"));
            }
        }, KeyCode.F);

        getInput().addAction(new UserAction("Shoot") {
            @Override
            protected void onAction() {
                playerComponent.shoot();
            }
        }, MouseButton.PRIMARY);
    }

    @Override
    protected void initGameVars(Map<String, Object> vars) {
        vars.put("score", 0);
        vars.put("bullets", 999);
        vars.put("laser", 50);
        vars.put("rockets", 10);
        vars.put("heat", 0);
        vars.put("overheating", false);
        vars.put("shield", 0);
        vars.put("hasShield", false);
    }

    @Override
    protected void initGame() {
        getAudioPlayer().setGlobalMusicVolume(0.3);
        getAudioPlayer().setGlobalSoundVolume(0.4);

        getGameWorld().addEntityFactory(new SpaceRunnerFactory());

        Texture t = getAssetLoader().loadTexture("bg_0.png");

        getGameScene().addGameView(new ScrollingBackgroundView(t.superTexture(t, HorizontalDirection.RIGHT),
                Orientation.HORIZONTAL));

        Entity player = getGameWorld().spawn("Player", 180, getHeight() / 2);

        playerComponent = player.getComponent(PlayerComponent.class);

        getGameScene().getViewport().setBounds(0, 0, Integer.MAX_VALUE, getHeight());
        getGameScene().getViewport().bindToEntity(player, 180, getHeight() / 2);

        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 10; y++) {
                spawn("ai_point", 800 + x * FXGLMath.random(30, 70), y * FXGLMath.random(10, 50));
            }
        }

        SquadAI.INSTANCE.update(getGameWorld().getEntitiesByType(SpaceRunnerType.AI_POINT));

        getGameState().<Integer>addListener("heat", (prev, now) -> {
            if (now >= 100) {
                set("overheating", true);
            }

            if (now == 0) {
                set("overheating", false);
            }
        });

        getbp("hasShield").bind(getip("shield").isEqualTo(100));

        run(this::spawnWaveIfNeeded, Duration.seconds(1));

        run(() -> {
            if (geti("heat") > 0)
                inc("heat", -5);

            if (geti("bullets") < 999)
                inc("bullets", +1);

        }, Duration.seconds(0.25));

        run(() -> {
            if (geti("shield") < 100)
                inc("shield", +4);
        }, Duration.seconds(0.5));

        run(() -> {
            spawnPowerup(FXGLMath.random(PowerupType.values()).get());
        }, Duration.seconds(3));

        nextLevel();
    }

    @Override
    protected void initPhysics() {
        getPhysicsWorld().addCollisionHandler(new BulletEnemyHandler());
        getPhysicsWorld().addCollisionHandler(new PlayerBulletHandler());
        getPhysicsWorld().addCollisionHandler(new PlayerPowerupHandler());
        getPhysicsWorld().addCollisionHandler(new BulletAIPointHandler());
    }

    private Texture weaponTexture;
    private Text bullets;
    private Text uiTextLevel;

    @Override
    protected void initUI() {
        weaponTexture = texture("sprite_bullet.png", 22, 11);

        bullets = getUIFactory().newText("", Color.rgb(20, 20, 20), 16);
        bullets.textProperty().bind(getip("bullets").asString("x %d"));

        HBox ui = new HBox(15,
                weaponTexture,
                bullets
                );

        Text laser = getUIFactory().newText("", Color.rgb(20, 20, 20), 16);
        laser.textProperty().bind(getip("laser").asString("x %d"));

        HBox ui2 = new HBox(15,
                texture("sprite_laser.png"),
                laser
        );

        Text rockets = getUIFactory().newText("", Color.rgb(20, 20, 20), 16);
        rockets.textProperty().bind(getip("rockets").asString("x %d"));

        HBox ui3 = new HBox(15,
                texture("rocket.png", 30, 8),
                rockets
        );

        VBox boxWeapons = new VBox(15, ui, ui2, ui3);
        boxWeapons.setTranslateX(getWidth() - 150);
        boxWeapons.setTranslateY(550);
        boxWeapons.setScaleX(1.4);
        boxWeapons.setScaleY(1.4);

        Texture uiBorder = texture("ui.png");
        uiBorder.setTranslateY(getHeight() - uiBorder.getHeight());

        getGameScene().addUINode(uiBorder);

        ProgressBar barHP = new ProgressBar(false);
        barHP.setHeight(30.0);
        barHP.setLabelVisible(false);


        barHP.setFill(Color.GREEN);
        barHP.setBackgroundFill(Color.DARKGREY);
        barHP.setTraceFill(Color.LIGHTGREEN);
        barHP.currentValueProperty().bind(playerComponent.getEntity().getComponent(HealthComponent.class).valueProperty());

        // heat

        ProgressBar barHeat = new ProgressBar(false);
        barHeat.setHeight(30.0);
        barHeat.setLabelVisible(false);

        barHeat.setFill(Color.RED);
        barHeat.setBackgroundFill(Color.DARKGREY);
        barHeat.setTraceFill(Color.YELLOW);
        barHeat.currentValueProperty().bind(getip("heat"));

        ProgressBar barShield = new ProgressBar(false);
        barShield.setHeight(30.0);
        barShield.setLabelVisible(false);

        barShield.setFill(Color.BLUE);
        barShield.setBackgroundFill(Color.DARKGREY);
        barShield.setTraceFill(Color.LIGHTBLUE);
        barShield.currentValueProperty().bind(getip("shield"));

        Text textShield = getUIFactory().newText("Shield");
        textShield.setFill(Color.BLUE);
        textShield.visibleProperty().bind(getbp("hasShield"));

        Text textDanger = getUIFactory().newText("Danger!");
        textDanger.fillProperty().bind(
                Bindings.when(getbp("overheating")).then(Color.RED).otherwise(Color.DARKGREY)
        );
        textDanger.opacityProperty().bind(
                Bindings.when(getbp("overheating")).then(1.0).otherwise(0.5)
        );

        HBox symbols = new HBox(10, textShield, textDanger);
        symbols.setTranslateX(35);
        symbols.setTranslateY(getHeight() - 35);

        VBox bars = new VBox(-10, barHP, barShield, barHeat);
        bars.setTranslateX(0);
        bars.setTranslateY(520);


        Text textScore = getUIFactory().newText("", Color.WHITE, 22);
        textScore.setTranslateX(300);
        textScore.textProperty().bind(getip("score").asString("Score: %d"));
        textScore.setEffect(new DropShadow(7, Color.BLACK));
        textScore.translateXProperty().bind(
                Bindings.createDoubleBinding(() -> {
                    return 450 - textScore.getLayoutBounds().getWidth();
                }, textScore.textProperty())
        );

        uiTextLevel = getUIFactory().newText("Level 1", Color.WHITE, 22);
        uiTextLevel.setEffect(new DropShadow(7, Color.BLACK));

        uiTextLevel.setVisible(false);

        Rectangle map = new Rectangle(450, 120, Color.color(0, 0, 0, 0.8));
        map.setStrokeWidth(12);
        map.setStroke(Color.LIGHTGRAY);
        map.setTranslateY(20);
        map.setArcWidth(35);
        map.setArcHeight(25);

        Pane centerPane = new Pane(textScore, uiTextLevel, map);
        centerPane.setTranslateX(300);
        centerPane.setTranslateY(540);

        getGameScene().addUINodes(bars, symbols, centerPane, boxWeapons);

        play("player_1.wav");

        runOnce(() -> play("begin.wav"), Duration.seconds(1));
    }

    private void nextLevel() {
        level = new Level();

        level.spawnNewWave();

        // TODO: update level number
        Text textLevel = getUIFactory().newText("Level 1", Color.WHITE, 22);
        textLevel.setEffect(new DropShadow(7, Color.BLACK));
        textLevel.setOpacity(0);

        centerText(textLevel);
        textLevel.setTranslateY(250);

        getGameScene().addUINode(textLevel);

        Animation<?> anim = fadeIn(textLevel, Duration.seconds(1.66), () -> {
            Animation<?> anim2 = translate(textLevel,
                    new Point2D(textLevel.getTranslateX(), textLevel.getTranslateY()),
                    new Point2D(350, 540),
                    Duration.ZERO,
                    Duration.seconds(1.66),
                    () -> {
                        getGameScene().removeUINode(textLevel);
                        uiTextLevel.setVisible(true);
                    });

            anim2.getAnimatedValue().setInterpolator(Interpolators.EXPONENTIAL.EASE_IN());
            anim2.startInPlayState();
        });

        anim.getAnimatedValue().setInterpolator(Interpolators.SMOOTH.EASE_OUT());

        anim.startInPlayState();
    }

    private void spawnWaveIfNeeded() {
        if (!getGameWorld().getEntitiesByType(SpaceRunnerType.ENEMY).isEmpty())
            return;

        if (level.isDone()) {
            nextLevel();
        } else {
            level.spawnNewWave();
        }
    }

    private void spawnPowerup(PowerupType type) {
        spawn("powerup", new SpawnData(playerComponent.getEntity().getX() + 1000, FXGLMath.random(0, 400)).put("type", type));
    }

    public static void main(String[] args) {
        launch(args);
    }
}

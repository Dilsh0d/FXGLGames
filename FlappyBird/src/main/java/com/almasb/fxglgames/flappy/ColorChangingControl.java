package com.almasb.fxglgames.flappy;

import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.app.FXGL;
import com.almasb.fxgl.time.LocalTimer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
public class ColorChangingControl extends Component {

    private Rectangle view;
    private LocalTimer timer;
    private Duration interval = Duration.seconds(5);

    @Override
    public void onAdded() {
        // hacky, assumes that first node is Rectangle
        view = (Rectangle) entity.getView().getNodes().get(0);
        timer = FXGL.newLocalTimer();
    }

    @Override
    public void onUpdate(double tpf) {
        if (timer.elapsed(interval)) {

            Color nextViewColor = ((Color)view.getFill()).invert();
            Color stageColor = nextViewColor.invert();

            view.setFill(nextViewColor);
            FXGL.getApp().getGameState().setValue("stageColor", stageColor);

            timer.capture();
        }
    }
}

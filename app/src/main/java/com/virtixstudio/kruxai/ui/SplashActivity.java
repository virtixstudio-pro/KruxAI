package com.virtixstudio.kruxai.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.virtixstudio.kruxai.R;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION = 4500L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View head = findViewById(R.id.kruxSplashHead);
        View intelligence = findViewById(R.id.tvIntelligence);
        View withoutLimits = findViewById(R.id.tvWithoutLimits);
        View copyright = findViewById(R.id.tvSplashCopyright);

        intelligence.setAlpha(0f);
        withoutLimits.setAlpha(0f);
        copyright.setAlpha(0f);

        head.setTranslationY(-500f);
        head.setRotationY(0f);

        ObjectAnimator drop = ObjectAnimator.ofFloat(
                head,
                View.TRANSLATION_Y,
                -500f,
                0f
        );
        drop.setDuration(1250L);
        drop.setInterpolator(new DecelerateInterpolator(1.8f));

        ObjectAnimator settle = ObjectAnimator.ofFloat(
                head,
                View.TRANSLATION_Y,
                0f,
                8f,
                0f
        );
        settle.setDuration(350L);
        settle.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator lookLeft = ObjectAnimator.ofFloat(
                head,
                View.ROTATION_Y,
                0f,
                -28f
        );
        lookLeft.setDuration(850L);
        lookLeft.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator showIntelligence = ObjectAnimator.ofFloat(
                intelligence,
                View.ALPHA,
                0f,
                1f
        );
        showIntelligence.setDuration(450L);

        ObjectAnimator lookRight = ObjectAnimator.ofFloat(
                head,
                View.ROTATION_Y,
                -28f,
                28f
        );
        lookRight.setDuration(1050L);
        lookRight.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator showWithoutLimits = ObjectAnimator.ofFloat(
                withoutLimits,
                View.ALPHA,
                0f,
                1f
        );
        showWithoutLimits.setDuration(500L);

        ObjectAnimator returnCenter = ObjectAnimator.ofFloat(
                head,
                View.ROTATION_Y,
                28f,
                0f
        );
        returnCenter.setDuration(700L);
        returnCenter.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator showCopyright = ObjectAnimator.ofFloat(
                copyright,
                View.ALPHA,
                0f,
                1f
        );
        showCopyright.setDuration(500L);

        AnimatorSet intro = new AnimatorSet();

        intro.play(drop)
                .before(settle);

        intro.play(settle)
                .before(lookLeft);

        intro.play(showIntelligence)
                .after(lookLeft);

        intro.play(lookRight)
                .after(showIntelligence);

        intro.play(showWithoutLimits)
                .after(lookRight);

        intro.play(returnCenter)
                .after(showWithoutLimits);

        intro.play(showCopyright)
                .after(returnCenter);

        intro.start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            FirebaseAuth auth = FirebaseAuth.getInstance();

            if (auth.getCurrentUser() != null) {
                startActivity(new Intent(
                        SplashActivity.this,
                        MainActivity.class
                ));
            } else {
                startActivity(new Intent(
                        SplashActivity.this,
                        AuthWelcomeActivity.class
                ));
            }

            finish();
        }, SPLASH_DURATION);
    }
}

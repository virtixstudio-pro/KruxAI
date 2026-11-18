package com.virtixstudio.kruxai.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Property;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.virtixstudio.kruxai.R;

/**
 * Écran d'accueil : la tête entre en douceur, un petit regard, les deux mots puis le copyright.
 * Tout tient en environ 2,3 s ; un toucher passe directement à la suite.
 */
public class SplashActivity extends AppCompatActivity {

    /** Durée avant de passer à l'écran suivant (l'animation est finie vers 1,4 s). */
    private static final long HOLD_MS = 2300L;
    /** Sans animations système (économie d'énergie, accessibilité) : on n'attend presque pas. */
    private static final long HOLD_NO_ANIM_MS = 700L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable goNext = this::goNext;
    private AnimatorSet intro;
    private boolean launched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Écran de marque : toujours noir, quel que soit le thème choisi (la tête a des parties blanches et noires).
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF000000));
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);
        androidx.core.view.WindowInsetsControllerCompat bars =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);

        View head = findViewById(R.id.kruxSplashHead);
        View intelligence = findViewById(R.id.tvIntelligence);
        View withoutLimits = findViewById(R.id.tvWithoutLimits);
        View copyright = findViewById(R.id.tvSplashCopyright);

        // Un toucher n'importe où : on passe tout de suite à la suite.
        findViewById(android.R.id.content).setOnClickListener(v -> goNext());

        boolean animated = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
        if (!animated) {
            handler.postDelayed(goNext, HOLD_NO_ANIM_MS);
            return;
        }

        float d = getResources().getDisplayMetrics().density;

        head.setAlpha(0f);
        head.setTranslationY(-48f * d);
        head.setScaleX(0.92f);
        head.setScaleY(0.92f);
        intelligence.setAlpha(0f);
        intelligence.setTranslationY(12f * d);
        withoutLimits.setAlpha(0f);
        withoutLimits.setTranslationY(12f * d);
        copyright.setAlpha(0f);

        TimeInterpolator out = new DecelerateInterpolator(1.6f);
        TimeInterpolator soft = new AccelerateDecelerateInterpolator();

        // La tête arrive en douceur puis regarde un peu à gauche et revient au centre.
        ObjectAnimator headAlpha = anim(head, View.ALPHA, 0, 600, out, 0f, 1f);
        ObjectAnimator headDrop = anim(head, View.TRANSLATION_Y, 0, 700, out, -48f * d, 0f);
        ObjectAnimator headScaleX = anim(head, View.SCALE_X, 0, 700, out, 0.92f, 1f);
        ObjectAnimator headScaleY = anim(head, View.SCALE_Y, 0, 700, out, 0.92f, 1f);
        ObjectAnimator look = anim(head, View.ROTATION_Y, 500, 850, soft, 0f, -16f, 0f);

        // Les deux mots montent doucement, l'un après l'autre.
        ObjectAnimator t1Alpha = anim(intelligence, View.ALPHA, 350, 450, out, 0f, 1f);
        ObjectAnimator t1Up = anim(intelligence, View.TRANSLATION_Y, 350, 450, out, 12f * d, 0f);
        ObjectAnimator t2Alpha = anim(withoutLimits, View.ALPHA, 650, 450, out, 0f, 1f);
        ObjectAnimator t2Up = anim(withoutLimits, View.TRANSLATION_Y, 650, 450, out, 12f * d, 0f);
        ObjectAnimator cAlpha = anim(copyright, View.ALPHA, 1000, 400, out, 0f, 1f);

        intro = new AnimatorSet();
        intro.playTogether(headAlpha, headDrop, headScaleX, headScaleY, look,
                t1Alpha, t1Up, t2Alpha, t2Up, cAlpha);
        intro.start();

        handler.postDelayed(goNext, HOLD_MS);
    }

    private static ObjectAnimator anim(View v, Property<View, Float> prop, long delay, long duration,
                                       TimeInterpolator interpolator, float... values) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, prop, values);
        a.setStartDelay(delay);
        a.setDuration(duration);
        a.setInterpolator(interpolator);
        return a;
    }

    private void goNext() {
        if (launched || isFinishing() || isDestroyed()) return;
        launched = true;
        handler.removeCallbacks(goNext);

        boolean signedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        startActivity(new Intent(this, signedIn ? MainActivity.class : AuthWelcomeActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        // Plus de minuteur ni d'animation après la fermeture : évite un double lancement si l'écran est recréé.
        handler.removeCallbacks(goNext);
        if (intro != null) intro.cancel();
        super.onDestroy();
    }
}

package com.virtixstudio.kruxai.live;

import java.util.Random;

/**
 * Moteur de mouvement du visage de KRUX en mode Live. Java pur : aucune dépendance Android, testable sur un JDK.
 *
 * Principe : chaque partie du visage a une valeur CIBLE (ce que KRUX "veut" faire) et une valeur ACTUELLE qui la
 * rattrape par lissage exponentiel indépendant du nombre d'images par seconde. Résultat : mouvement fluide à
 * 60 i/s comme à 90 ou 120, sans saccade, même si la durée d'une image varie.
 */
public final class LiveMotion {

    public enum State { IDLE, LISTENING, THINKING, SPEAKING }
    public enum Mood { NEUTRAL, HAPPY, SURPRISED }

    // ---- Sorties (lues par la vue à chaque image)
    public float headX, headY;   // décalage de la tête, en unités du dessin (512 x 512)
    public float roll;           // inclinaison, en degrés
    public float scale = 1f;     // respiration + petit "coup" quand il parle
    public float eyeX, eyeY;     // direction du regard (-1..1)
    public float eyeOpen = 1f;   // 0 = fermé (clignement), 1 = ouvert
    public float happy;          // 0..1 : yeux en arc ^ ^
    public float browUp;         // -1..1
    public float mouth;          // 0 = fermée (sourire), 1 = grande ouverte
    public float smile = 1f;     // largeur / courbure du sourire
    public float glow;           // 0..1 : intensité du casque et de la boule (écoute, réflexion)
    public float orb;            // 0..1 : pulsation de la boule d'antenne

    // ---- Entrées
    private State state = State.IDLE;
    private Mood mood = Mood.NEUTRAL;
    private float inputLevel;    // niveau du micro 0..1 (écoute)

    // ---- Cibles
    private float tHeadX, tHeadY, tRoll, tScale = 1f, tEyeX, tEyeY, tBrow, tMouth, tHappy, tSmile = 1f, tGlow;

    private final Random rnd;
    private float time;
    private float nextBlink, blinkT = -1f;
    private float nextGlance;
    private float nextMouth;
    private float nextSpecial;   // petit rire / haussement de sourcils aléatoire au repos
    private float midX, midY, midRoll;
    private float kick;         // impulsion de la tête quand un mot est prononcé

    public LiveMotion(long seed) {
        rnd = new Random(seed);
        nextBlink = 1.5f + rnd.nextFloat() * 2.5f;
        nextGlance = 0.8f + rnd.nextFloat() * 1.5f;
        nextSpecial = 6f + rnd.nextFloat() * 8f;
    }

    public State state() { return state; }

    public void setState(State s) {
        if (s == null || s == state) return;
        state = s;
        if (s != State.SPEAKING) tMouth = 0f;
    }

    public void setMood(Mood m) { if (m != null) mood = m; }

    /** Niveau sonore du micro (0..1) pendant l'écoute. */
    public void setInputLevel(float level) { inputLevel = clamp(level, 0f, 1f); }

    /** À appeler à chaque mot prononcé (TextToSpeech.onRangeStart) : donne un petit mouvement de tête. */
    public void onWord() { kick = 1f; tMouth = 0.55f + rnd.nextFloat() * 0.45f; nextMouth = time + 0.09f; }

    /** Fait une expression ponctuelle (le visage redevient neutre tout seul). */
    public void flash(Mood m, float seconds) { mood = m; moodUntil = time + seconds; }
    private float moodUntil = -1f;

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** Rapproche cur de target ; k = vitesse (plus grand = plus vif). Exact quelle que soit la durée de l'image. */
    static float smooth(float cur, float target, float k, float dt) {
        return cur + (target - cur) * (1f - (float) Math.exp(-k * dt));
    }

    private float sway(float speed, float phase) {
        return (float) (Math.sin(time * speed + phase) * 0.6 + Math.sin(time * speed * 1.73 + phase * 2.1) * 0.4);
    }

    /** Avance l'animation de dt secondes (dt est borné pour éviter les sauts après une pause). */
    public void update(float dt) {
        dt = clamp(dt, 0f, 0.05f);
        time += dt;
        if (moodUntil > 0 && time > moodUntil) { mood = Mood.NEUTRAL; moodUntil = -1f; }

        // ---- Mouvement de base : la tête flotte doucement
        float baseX = sway(0.9f, 0f) * 7f, baseY = sway(1.3f, 1.7f) * 5f, baseRoll = sway(0.7f, 3.1f) * 2.2f;
        float breathe = (float) Math.sin(time * 1.9) * 0.008f;
        tScale = 1f + breathe;
        tHeadX = baseX; tHeadY = baseY; tRoll = baseRoll;
        tBrow = 0f; tHappy = 0f; tSmile = 1f; tGlow = 0.35f;

        // ---- Regard : petits saccades aléatoires
        nextGlance -= dt;
        if (nextGlance <= 0f) {
            tEyeX = (rnd.nextFloat() * 2f - 1f) * 0.7f;
            tEyeY = (rnd.nextFloat() * 2f - 1f) * 0.45f;
            nextGlance = 1.0f + rnd.nextFloat() * 2.4f;
        }

        switch (state) {
            case LISTENING:
                tRoll += 7f;                    // la tête s'incline pour écouter
                tHeadY += -4f;
                tBrow = 0.45f;
                tGlow = 0.6f + inputLevel * 0.4f;
                tEyeX *= 0.25f; tEyeY = -0.05f; // regarde presque l'utilisateur
                tScale += inputLevel * 0.02f;
                break;
            case THINKING:
                tEyeX = -0.65f + sway(0.8f, 0.5f) * 0.25f; // regarde en haut à gauche
                tEyeY = -0.75f;
                tRoll += -5f; tHeadX += -10f;
                tBrow = 0.7f;
                tGlow = 0.55f + (float) (Math.sin(time * 6.0) * 0.25 + 0.25);
                break;
            case SPEAKING:
                tGlow = 0.5f;
                tEyeX *= 0.35f; tEyeY *= 0.35f;
                tBrow = 0.15f + sway(2.2f, 0f) * 0.12f;
                nextMouth -= dt;
                if (nextMouth <= 0f) {          // une "syllabe" toutes les ~110 ms
                    boolean close = rnd.nextFloat() < 0.22f;
                    tMouth = close ? 0.04f : 0.25f + rnd.nextFloat() * 0.75f;
                    nextMouth = 0.07f + rnd.nextFloat() * 0.10f;
                }
                tHeadY += sway(3.0f, 0.4f) * 2f;
                break;
            default:
                nextSpecial -= dt;
                if (nextSpecial <= 0f && mood == Mood.NEUTRAL) {
                    flash(Mood.HAPPY, 1.4f);
                    nextSpecial = 8f + rnd.nextFloat() * 10f;
                }
                break;
        }

        if (state != State.SPEAKING) tMouth = 0f;

        // ---- Expressions
        if (mood == Mood.HAPPY) { tHappy = 1f; tSmile = 1.25f; tBrow = Math.max(tBrow, 0.25f); }
        else if (mood == Mood.SURPRISED) { tBrow = 1f; tMouth = Math.max(tMouth, 0.7f); tScale += 0.03f; }

        // ---- Coup de tête sur chaque mot
        kick = smooth(kick, 0f, 9f, dt);
        tHeadY += kick * 6f;
        tScale += kick * 0.015f;

        // ---- Clignement : fermeture rapide, ouverture un peu plus lente
        nextBlink -= dt;
        if (nextBlink <= 0f && blinkT < 0f) blinkT = 0f;
        if (blinkT >= 0f) {
            blinkT += dt;
            float d = 0.17f;
            float p = blinkT / d;
            eyeOpen = p < 0.4f ? 1f - p / 0.4f : (p < 1f ? (p - 0.4f) / 0.6f : 1f);
            eyeOpen = clamp(eyeOpen, 0f, 1f);
            if (blinkT >= d) {
                blinkT = -1f;
                eyeOpen = 1f;
                nextBlink = 2f + rnd.nextFloat() * 3.5f;
                if (rnd.nextFloat() < 0.18f) nextBlink = 0.25f; // double clignement
            }
        } else {
            eyeOpen = 1f;
        }

        // ---- Lissage vers les cibles
        // Tête : double lissage (démarrage et arrêt progressifs, comme un vrai mouvement avec de l'inertie)
        midX = smooth(midX, tHeadX, 9f, dt);
        midY = smooth(midY, tHeadY, 9f, dt);
        midRoll = smooth(midRoll, tRoll, 7f, dt);
        headX = smooth(headX, midX, 9f, dt);
        headY = smooth(headY, midY, 9f, dt);
        roll = smooth(roll, midRoll, 7f, dt);
        scale = smooth(scale, tScale, 8f, dt);
        eyeX = smooth(eyeX, tEyeX, 14f, dt);
        eyeY = smooth(eyeY, tEyeY, 14f, dt);
        browUp = smooth(browUp, tBrow, 9f, dt);
        happy = smooth(happy, tHappy, 12f, dt);
        smile = smooth(smile, tSmile, 10f, dt);
        glow = smooth(glow, tGlow, 6f, dt);
        mouth = smooth(mouth, tMouth, state == State.SPEAKING ? 26f : 12f, dt);
        orb = (float) (0.5 + 0.5 * Math.sin(time * (state == State.THINKING ? 7.0 : 2.6)));
    }
}

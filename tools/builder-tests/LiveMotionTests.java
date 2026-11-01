import com.virtixstudio.kruxai.live.LiveMotion;
import com.virtixstudio.kruxai.live.LiveMotion.State;

public class LiveMotionTests {
    static int fails, total;
    static void check(String n, boolean ok) { total++; if (!ok) { fails++; System.out.println("ECHEC : " + n); } }

    public static void main(String[] a) {
        // 1. Fluidité : 20 s à 60 i/s, aucune variation brutale d'une image à l'autre
        LiveMotion m = new LiveMotion(42);
        float dt = 1f / 60f, maxHead = 0, maxRoll = 0, maxMouth = 0, maxEye = 0;
        float px = 0, py = 0, pr = 0, pm = 0, pe = 0, minOpen = 1, maxMouthSeen = 0;
        State[] seq = {State.IDLE, State.LISTENING, State.THINKING, State.SPEAKING, State.IDLE};
        boolean bad = false;
        for (int s = 0; s < seq.length; s++) {
            m.setState(seq[s]);
            for (int i = 0; i < 240; i++) {
                if (seq[s] == State.SPEAKING && i % 20 == 0) m.onWord();
                m.update(dt);
                if (i > 0 || s > 0) {
                    maxHead = Math.max(maxHead, Math.max(Math.abs(m.headX - px), Math.abs(m.headY - py)));
                    maxRoll = Math.max(maxRoll, Math.abs(m.roll - pr));
                    maxMouth = Math.max(maxMouth, Math.abs(m.mouth - pm));
                    maxEye = Math.max(maxEye, Math.abs(m.eyeX - pe));
                }
                px = m.headX; py = m.headY; pr = m.roll; pm = m.mouth; pe = m.eyeX;
                minOpen = Math.min(minOpen, m.eyeOpen);
                if (seq[s] == State.SPEAKING) maxMouthSeen = Math.max(maxMouthSeen, m.mouth);
                if (Float.isNaN(m.headX) || Float.isNaN(m.mouth) || Float.isNaN(m.eyeX) || Float.isNaN(m.roll)) bad = true;
                if (m.mouth < -0.001f || m.mouth > 1.001f || m.eyeOpen < 0 || m.eyeOpen > 1) bad = true;
            }
        }
        check("pas de NaN ni valeur hors limites", !bad);
        check("tête fluide (max " + maxHead + " par image)", maxHead < 2.0f);
        check("inclinaison fluide (max " + maxRoll + ")", maxRoll < 0.8f);
        check("regard fluide (max " + maxEye + ")", maxEye < 0.2f);
        check("bouche sans saut (max " + maxMouth + ")", maxMouth < 0.35f);
        check("la bouche s'ouvre en parlant (" + maxMouthSeen + ")", maxMouthSeen > 0.5f);
        check("il cligne des yeux (min " + minOpen + ")", minOpen < 0.2f);

        // 2. La bouche se referme quand il arrête de parler
        for (int i = 0; i < 60; i++) m.update(dt);
        check("bouche fermée au repos", m.mouth < 0.05f);

        // 3. Indépendance du taux d'images : même position après 5 s à 60, 90 et 120 i/s
        float[] ref = new float[3]; int[] fps = {60, 90, 120};
        for (int k = 0; k < 3; k++) {
            LiveMotion t = new LiveMotion(7); t.setState(State.THINKING);
            for (int i = 0; i < 5 * fps[k]; i++) t.update(1f / fps[k]);
            ref[k] = t.roll;
        }
        check("même résultat à 60/90/120 i/s (" + ref[0] + " " + ref[1] + " " + ref[2] + ")",
            Math.abs(ref[0] - ref[1]) < 0.35f && Math.abs(ref[0] - ref[2]) < 0.35f);

        // 4. Grosse pause (téléphone en veille) : pas de saut
        LiveMotion p = new LiveMotion(3); p.update(dt); float before = p.headX; p.update(5f);
        check("pause de 5 s sans saut", Math.abs(p.headX - before) < 8f);

        // 5. Écoute : la lueur suit le niveau du micro
        LiveMotion l = new LiveMotion(5); l.setState(State.LISTENING); l.setInputLevel(1f);
        for (int i = 0; i < 120; i++) l.update(dt);
        check("lueur forte quand le micro capte", l.glow > 0.8f);

        System.out.println((total - fails) + "/" + total + " tests réussis");
        if (fails > 0) System.exit(1);
    }
}

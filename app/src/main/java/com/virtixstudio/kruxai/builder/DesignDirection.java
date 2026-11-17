package com.virtixstudio.kruxai.builder;

import java.util.Random;

/**
 * Direction visuelle tirée au hasard pour chaque application générée : palette, formes, typographie,
 * structure et navigation. Sans ça, l'IA retombe toujours sur le même modèle Material violet.
 * Java pur : testable sans Android.
 */
public final class DesignDirection {

    private static final String[] PALETTES = {
        "« Encre et corail » : fond #FAF7F2, texte #1E1B18, accent #E8553D, secondaire #2F4858",
        "« Nuit électrique » (sombre dominant) : fond #0E1116, surfaces #1A1F29, texte #E6EAF0, accent #5EEAD4, secondaire #A78BFA",
        "« Forêt douce » : fond #F3F6F1, texte #1B2A22, accent #2F6B4F, secondaire #D9A441",
        "« Sable et indigo » : fond #F7F3EA, texte #1F2030, accent #3B3F8C, secondaire #E0A458",
        "« Rose poudré » : fond #FFF5F7, texte #3D2C3E, accent #D6336C, secondaire #3D2C3E",
        "« Océan » : fond #EEF6FA, texte #10293A, accent #0B7285, secondaire #F08C00",
        "« Graphite et citron » (sombre dominant) : fond #16181B, surfaces #22262B, texte #F1F3F5, accent #D7F205 (texte noir dessus), secondaire #8C94A0",
        "« Terre cuite » : fond #FBF1E8, texte #2B1D14, accent #B5532B, secondaire #4A5D4F",
        "« Papier monochrome » : fond #FFFFFF, texte #111111, accent #111111, une seule touche de rouge #E03131",
        "« Menthe et prune » : fond #F1FAF6, texte #2A1A2E, accent #1B998B, secondaire #5B2A6E"
    };

    private static final String[] SHAPES = {
        "coins très arrondis (24dp), cartes sans ombre avec une fine bordure",
        "coins nets (4dp), cartes à plat séparées par des filets fins, aucune ombre",
        "coins moyens (12dp) avec ombres douces (elevation 4dp)",
        "boutons et puces en pilule (coins 999dp), cartes à 20dp",
        "grandes zones de couleur pleine bord à bord, très peu de bordures, boutons à 8dp"
    };

    private static final String[] TYPOGRAPHY = {
        "titres très grands en sans-serif-medium, texte en sans-serif",
        "titres en serif (android:fontFamily=\"serif\"), texte en sans-serif",
        "chiffres et libellés techniques en monospace, le reste en sans-serif",
        "titres en sans-serif-condensed et MAJUSCULES, texte en sans-serif",
        "titres en sans-serif-light, grands, aérés, beaucoup d'espace autour"
    };

    private static final String[] LAYOUTS = {
        "accueil en grille de tuiles sur 2 colonnes",
        "liste de cartes pleine largeur sous un grand en-tête coloré",
        "écran principal centré sur UNE action principale (grand bouton), le reste en second plan",
        "tableau de bord : chiffres clés en haut, puis liste en dessous",
        "chronologie verticale avec repères à gauche"
    };

    private static final String[] NAVIGATION = {
        "BottomNavigationView à 3 ou 4 onglets",
        "TabLayout avec ViewPager2 en haut de l'écran",
        "écran principal avec FloatingActionButton, écrans secondaires ouverts par Intent (flèche de retour)",
        "barre du bas faite maison (rangée d'icônes et de libellés dans un LinearLayout), sans BottomNavigationView"
    };

    public final String palette, shapes, typography, layout, navigation;

    private DesignDirection(String palette, String shapes, String typography, String layout, String navigation) {
        this.palette = palette; this.shapes = shapes; this.typography = typography;
        this.layout = layout; this.navigation = navigation;
    }

    public static DesignDirection pick(Random r) {
        return new DesignDirection(
            PALETTES[r.nextInt(PALETTES.length)], SHAPES[r.nextInt(SHAPES.length)],
            TYPOGRAPHY[r.nextInt(TYPOGRAPHY.length)], LAYOUTS[r.nextInt(LAYOUTS.length)],
            NAVIGATION[r.nextInt(NAVIGATION.length)]);
    }

    /** Texte ajouté au prompt système de création. */
    public String toPrompt() {
        return "DIRECTION VISUELLE IMPOSÉE (chaque application a la sienne : ne retombe pas sur le violet Material par défaut) : "
            + "palette " + palette + " (à définir dans res/values/colors.xml et themes.xml : colorPrimary, colorSecondary, colorSurface, colorOnPrimary, etc., "
            + "avec une variante claire et sombre cohérente) ; formes : " + shapes + " ; typographie : " + typography + " ; "
            + "structure : " + layout + " ; navigation : " + navigation + ". "
            + "La maquette (preview) doit reprendre exactement ces couleurs et ces formes. "
            + "Si le sujet de l'app s'y oppose vraiment, garde la palette mais adapte l'ambiance ; ne mélange pas avec une autre palette. ";
    }
}

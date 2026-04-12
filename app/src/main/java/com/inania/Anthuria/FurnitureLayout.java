package com.inania.Anthuria;

/**
 * Default 2D footprint sizes (pixels) for catalog {@code type} ids (snake_case).
 */
public final class FurnitureLayout {

    private FurnitureLayout() {
    }

    public static float[] defaultSizePx(String type) {
        if (type == null) {
            return new float[]{100f, 60f};
        }
        String t = type.toLowerCase();

        if (t.contains("king_bed") || t.contains("queen_bed")) return new float[]{200f, 200f};
        if (t.contains("double_bed") || t.equals("bed")) return new float[]{200f, 160f};
        if (t.contains("bunk") || t.contains("loft_bed") || t.contains("trundle")) return new float[]{200f, 100f};
        if (t.contains("murphy")) return new float[]{200f, 40f};
        if (t.contains("crib") || t.contains("toddler")) return new float[]{140f, 80f};

        if (t.contains("sectional") || t.contains("sofa")) return new float[]{240f, 100f};
        if (t.contains("loveseat")) return new float[]{180f, 90f};
        if (t.contains("recliner") || t.contains("armchair") || t.contains("gaming_chair"))
            return new float[]{90f, 90f};
        if (t.contains("chair") || t.contains("stool")) return new float[]{55f, 55f};

        if (t.contains("dining_table") || t.contains("extendable")) return new float[]{180f, 100f};
        if (t.contains("coffee_table")) return new float[]{120f, 70f};
        if (t.contains("console") || t.contains("side_table")) return new float[]{100f, 40f};
        if (t.contains("conference")) return new float[]{240f, 120f};

        if (t.contains("island") || t.contains("peninsula")) return new float[]{160f, 90f};
        if (t.contains("tall_cabinet") || t.contains("pantry")) return new float[]{80f, 70f};
        if (t.contains("wall_cabinet")) return new float[]{120f, 40f};
        if (t.contains("base_cabinet") || t.contains("sink_cabinet")) return new float[]{120f, 60f};
        if (t.contains("corner_cabinet")) return new float[]{90f, 90f};
        if (t.contains("wardrobe") || t.contains("closet")) return new float[]{120f, 65f};
        if (t.contains("walk_in_closet")) return new float[]{180f, 120f};

        if (t.contains("bathtub") || t.contains("shower")) return new float[]{180f, 90f};
        if (t.contains("toilet") || t.contains("bidet") || t.contains("urinal")) return new float[]{70f, 55f};
        if (t.contains("vanity") || t.contains("double_vanity")) return new float[]{t.contains("double") ? 160f : 100f, 55f};

        if (t.contains("desk") || t.contains("credenza")) return new float[]{140f, 70f};
        if (t.contains("bookshelf") || t.contains("shelving")) return new float[]{90f, 40f};
        if (t.contains("tv_stand") || t.contains("media_console")) return new float[]{160f, 50f};
        if (t.contains("dresser") || t.contains("chest_of_drawers")) return new float[]{120f, 55f};
        if (t.contains("nightstand")) return new float[]{55f, 45f};

        if (t.contains("bench") || t.contains("nook")) return new float[]{140f, 45f};
        if (t.contains("sideboard") || t.contains("buffet") || t.contains("china_cabinet"))
            return new float[]{180f, 55f};

        if (t.contains("workbench")) return new float[]{200f, 70f};
        if (t.contains("garage") || t.contains("tool_chest")) return new float[]{120f, 60f};

        return new float[]{110f, 65f};
    }
}

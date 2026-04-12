package com.inania.Anthuria;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Furniture type ids per room (snake_case), from the product catalog JSON.
 */
public final class FurnitureCatalog {

    private static final Map<String, List<String>> BY_ROOM = new HashMap<>();

    static {
        put("kitchen", "base_cabinet,wall_cabinet,tall_cabinet,kitchen_island,peninsula,bar_counter,dining_table,extendable_table,chairs,stools,corner_cabinet,pantry,open_shelves,wine_rack,sideboard,buffet,kitchen_cart,breakfast_nook,bench,plate_rack,spice_rack,drawer_unit,pull_out_cabinet,appliance_garage,trash_pullout,cutlery_insert,dish_rack,pot_rack");
        put("bathroom", "vanity,double_vanity,sink_cabinet,mirror_cabinet,bathtub,freestanding_bathtub,shower_cabin,walk_in_shower,toilet,bidet,urinal,storage_cabinet,linen_cabinet,wall_shelves,towel_rack,towel_warmer,laundry_basket,bathroom_stool,corner_shelf,medicine_cabinet,under_sink_storage,over_toilet_shelf,bench,shower_seat,hamper,rolling_cart");
        put("bedroom", "bed,double_bed,queen_bed,king_bed,bunk_bed,loft_bed,trundle_bed,murphy_bed,storage_bed,nightstand,wardrobe,walk_in_closet_system,closet,dresser,chest_of_drawers,vanity_table,desk,chair,armchair,bench,ottoman,bookshelf,wall_shelves,tv_stand,mirror,room_divider,coat_rack,clothes_rack,shoe_rack");
        put("living_room", "sofa,sectional_sofa,loveseat,recliner,armchair,coffee_table,side_table,console_table,tv_stand,media_console,bookshelf,display_cabinet,wall_shelves,ottoman,bench,floor_cushions,cabinet,bar_cart,fireplace_surround,room_divider,gaming_chair");
        put("dining_room", "dining_table,extendable_table,chairs,armchairs,bench,sideboard,buffet,china_cabinet,display_cabinet,bar_cart,wine_cabinet,console_table,serving_table,corner_cabinet");
        put("office", "desk,standing_desk,corner_desk,chair,ergonomic_chair,guest_chair,bookshelf,filing_cabinet,drawer_unit,wall_shelves,cabinet,credenza,conference_table,partition,whiteboard_stand,printer_stand");
        put("kids_room", "crib,toddler_bed,bunk_bed,loft_bed,trundle_bed,changing_table,dresser,toy_storage,toy_box,bookshelf,desk,chair,bean_bag,play_table,wardrobe,clothes_rack");
        put("hallway", "console_table,coat_rack,shoe_rack,bench,storage_bench,wall_shelves,cabinet,umbrella_stand,mirror,key_holder");
        put("laundry_room", "laundry_basket,hamper,storage_cabinet,wall_shelves,folding_table,ironing_board,drying_rack,utility_sink,rolling_cart,linen_cabinet");
        put("garage", "workbench,tool_cabinet,tool_chest,wall_storage,shelving_unit,bike_rack,storage_rack,pegboard,locker,utility_table");
        put("balcony", "outdoor_chairs,folding_chairs,small_table,bench,hammock,swing_chair,planter_stand,storage_box,bar_table,stools");
        put("entryway", "console_table,bench,storage_bench,coat_rack,shoe_rack,cabinet,mirror,umbrella_stand,wall_hooks,key_holder");
    }

    private FurnitureCatalog() {
    }

    private static void put(String room, String csv) {
        String[] parts = csv.split(",");
        List<String> list = new ArrayList<>(parts.length);
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) list.add(t);
        }
        BY_ROOM.put(room, Collections.unmodifiableList(list));
    }

    /**
     * @param roomKey e.g. {@code kitchen}, {@code living_room}
     */
    public static List<String> typesForRoom(String roomKey) {
        if (roomKey == null) return Collections.emptyList();
        List<String> list = BY_ROOM.get(roomKey.toLowerCase(Locale.US));
        return list != null ? list : Collections.emptyList();
    }

    /** Map Russian spinner labels to catalog keys. */
    public static String catalogKeyForSpinnerLabel(String label) {
        if (label == null) return "kitchen";
        switch (label) {
            case "Кухня":
                return "kitchen";
            case "Спальня":
                return "bedroom";
            case "Гостиная":
                return "living_room";
            case "Ванная":
                return "bathroom";
            case "Столовая":
                return "dining_room";
            case "Офис":
                return "office";
            case "Детская":
                return "kids_room";
            case "Прихожая":
                return "hallway";
            case "Прачечная":
                return "laundry_room";
            case "Гараж":
                return "garage";
            case "Балкон":
                return "balcony";
            case "Входная зона":
                return "entryway";
            default:
                return "kitchen";
        }
    }

    public static String humanLabel(String typeId) {
        return typeId.replace('_', ' ');
    }
}

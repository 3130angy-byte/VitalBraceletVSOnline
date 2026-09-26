package org.example.chat;

import java.util.List;
import java.util.Locale;

/**
 * Nombres de Digimon conocidos (alfabeto latino), solo para CORREGIR
 * lecturas casi correctas del modelo de visión ("Dynasnon" -> "Dynasmon").
 * Si hay empate entre dos conocidos ("Glevmon": Greymon y Leomon), no
 * adivina y deja lo leído.
 * No es una lista cerrada: un nombre que no esté aquí (ej. MagnaKidmon, de
 * una DIM personalizada) se acepta tal cual si pasó la validación.
 */
public final class KnownDigimonNames {

    private KnownDigimonNames() {}

    public static final List<String> NAMES = List.of(
            "Agumon", "Greymon", "MetalGreymon", "WarGreymon", "SkullGreymon", "Koromon", "Botamon", "Yukimibotamon",
            "Gabumon", "Garurumon", "WereGarurumon", "MetalGarurumon", "Tsunomon", "Punimon",
            "Patamon", "Angemon", "MagnaAngemon", "HolyAngemon", "Seraphimon", "Tokomon", "Poyomon",
            "Gatomon", "Tailmon", "Angewomon", "Ophanimon", "Magnadramon", "Salamon", "Plotmon",
            "Biyomon", "Birdramon", "Garudamon", "Phoenixmon", "Hououmon", "Yokomon",
            "Tentomon", "Kabuterimon", "MegaKabuterimon", "HerculesKabuterimon", "Motimon", "Pabumon",
            "Palmon", "Togemon", "Lillymon", "Rosemon", "Tanemon", "Yuramon",
            "Gomamon", "Ikkakumon", "Zudomon", "Vikemon", "Bukamon", "Pichimon",
            "Veemon", "ExVeemon", "Paildramon", "Imperialdramon", "Demiveemon", "Chibomon",
            "Wormmon", "Stingmon", "Dinobeemon", "Hawkmon", "Aquilamon", "Silphymon",
            "Armadillomon", "Ankylomon", "Shakkoumon", "Guilmon", "Growlmon", "WarGrowlmon", "Gallantmon",
            "Megidramon", "Gigimon", "Terriermon", "Gargomon", "Rapidmon", "MegaGargomon", "Lopmon",
            "Renamon", "Kyubimon", "Taomon", "Sakuyamon", "Impmon", "Beelzemon", "Monodramon", "Strikedramon",
            "Cyberdramon", "Justimon", "Omnimon", "Omegamon", "Alphamon", "Dukemon", "Craniamon", "Dynasmon",
            "UlforceVeedramon", "Magnamon", "Examon", "LordKnightmon", "Crusadermon", "Gankoomon", "Jesmon",
            "Sleipmon", "Kidmon", "MagnaKidmon", "Gammamon", "BetelGammamon", "Kausgammamon", "GulusGammamon",
            "Canoweissmon", "Siriusmon", "Angoramon", "Jellymon", "Espimon", "Ryudamon", "Ginryumon",
            "Hisyaryumon", "Ouryumon", "Ouroboromon", "Devimon", "Myotismon", "VenomMyotismon", "Piedmon",
            "Puppetmon", "MetalSeadramon", "Machinedramon", "Apocalymon", "Leomon", "SaberLeomon", "LoaderLiomon",
            "BanchoLeomon", "Ogremon", "Andromon", "HiAndromon", "Etemon", "MetalEtemon", "Seadramon",
            "MegaSeadramon", "Shellmon", "Numemon", "Monzaemon", "Sukamon", "Nanimon", "Centarumon", "Unimon",
            "Meramon", "Tyrannomon", "MasterTyrannomon", "Dinorexmon", "Triceramon", "Ukkomon", "Monochromon",
            "Elecmon", "Kuwagamon", "Okuwamon", "GranKuwagamon", "Drimogemon", "Mojyamon", "Frigimon",
            "Gekomon", "ShogunGekomon", "Tuskmon", "Starmon", "Pumpkinmon", "Gotsumon", "Wizardmon",
            "Mamemon", "BigMamemon", "MetalMamemon", "Piximon", "Vademon", "Vegiemon", "Woodmon", "Cherrymon",
            "Anomalocarimon", "Dandevimon", "Kiwimon", "Deltamon", "Garbagemon", "Blossomon", "Zassoumon",
            "ShineGreymon", "MirageGaogamon", "Ravemon", "Rosemon", "Gaogamon", "Lalamon", "Falcomon",
            "Kamemon", "Shoutmon", "Dorulumon", "Ballistamon", "Greymon", "Coronamon", "Lunamon",
            "Apollomon", "Dianamon", "Sunarizamon", "Commandramon", "Sealsdramon", "Tankdramon",
            "BlackAgumon", "BlackGreymon", "BlackWarGreymon", "Gaiomon", "Zubamon", "Zubaeagermon", "Duramon",
            "Durandamon", "Hackmon", "Reppamon", "SaviorHackmon", "Kotemon", "Dinohumon", "Knightmon",
            "Sistermon", "Diablomon", "Armagemon", "Chaosdramon", "Millenniummon", "Moon=Millenniummon",
            "Arcadiamon", "Lucemon", "Belphemon", "Daemon", "Leviamon", "Lilithmon", "Barbamon", "Beelzebumon",
            "Mugendramon", "Kimeramon", "Paildramon", "Dracomon", "Coredramon", "Wingdramon", "Slayerdramon",
            "Breakdramon", "Grademon", "Plesiomon", "Quetzalmon", "Jijimon", "Babamon", "Hagurumon",
            "Guardromon", "Datamon", "Tinmon", "Keramon", "Kurisarimon", "Infermon", "Diaboromon");

    /**
     * Si el nombre leído está a 1-2 letras de uno conocido (y no es exacto),
     * devuelve el conocido; si no, el leído. Nunca "corrige" a un nombre
     * muy distinto: el límite es ~25% del largo.
     */
    public static String correct(String read) {
        String lower = read.toLowerCase(Locale.ROOT);
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean tie = false;
        for (String known : NAMES) {
            int d = levenshtein(lower, known.toLowerCase(Locale.ROOT));
            if (d == 0) return known;
            if (d < bestDistance) { bestDistance = d; best = known; tie = false; }
            else if (d == bestDistance && !known.equals(best)) tie = true;
        }
        // Nombres de 7+ letras toleran 2 errores; cortos, solo 1.
        int maxAllowed = read.length() >= 7 ? 2 : 1;
        return (best != null && !tie && bestDistance <= maxAllowed) ? best : read;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}

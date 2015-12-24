package halsbot.poe.ladder;

import halsbot.poe.ladder.Character.Class;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;

import static halsbot.webutil.WebUtil.readJsonFromUrl;

public class Ladder {

    private final static String ladder_json_url = "http://api.pathofexile.com/ladders/LEAGUE?limit=200";

    private int total;
    private ArrayList<LadderEntry> entries;

    public Ladder(int total, ArrayList<LadderEntry> entries) {
        this.total = total;
        this.entries = new ArrayList<>(entries);
    }

    private static Ladder fromJson(JSONObject obj1, JSONObject obj2) {
        int total = obj1.getInt("total");
        ArrayList<LadderEntry> entries = new ArrayList<>();


        JSONArray ent = obj1.getJSONArray("entries");
        for (int i = 0; i < (total < 200 ? total : 200); i++) {
            LadderEntry entry = LadderEntry.fromJson(ent.getJSONObject(i));
            entries.add(entry);
        }

        total = obj2.getInt("total");
        ent = obj2.getJSONArray("entries");
        for (int i = 0; i < (total < 200 ? total : 200); i++) {
            LadderEntry entry = LadderEntry.fromJson(ent.getJSONObject(i));
            entries.add(entry);
        }

        return new Ladder(obj1.getInt("total"), entries);
    }

    public static Ladder getLadderForLeague(String leagueName) throws IOException {
        JSONObject ladder = readJsonFromUrl(ladder_json_url.replace("LEAGUE", URLEncoder.encode(leagueName, "UTF-8")));
        JSONObject ladder2 = readJsonFromUrl(ladder_json_url.replace("LEAGUE", URLEncoder.encode(leagueName, "UTF-8")) + "&offset=200");

        return fromJson(ladder, ladder2);
    }

    public int getTotal() {
        return total;
    }

    public RankStatus getRankForQuery(String name, boolean character) {
        HashMap<Class, Integer> classes = new HashMap<>();
        classes.put(Class.Duelist, 0);
        classes.put(Class.Marauder, 0);
        classes.put(Class.Ranger, 0);
        classes.put(Class.Scion, 0);
        classes.put(Class.Shadow, 0);
        classes.put(Class.Templar, 0);
        classes.put(Class.Witch, 0);

        Class charClass = null;
        String charName = "";
        int rank = -1;
        int rankInClass = -1;
        int level = -1;

        for (LadderEntry entry : entries) {

            Class entryClass = entry.getCharacter().getCharClass();
            classes.put(entryClass, classes.get(entryClass) + 1);


            if (!character && !entry.getDead() && entry.getAccount().getName().equalsIgnoreCase(name)) {
                charClass = entryClass;
                rank = entry.getRank();
                rankInClass = classes.get(charClass);
                charName = entry.getCharacter().getName();
                level = entry.getCharacter().getLevel();
                break;
            } else if (character && !entry.getDead() && entry.getCharacter().getName().equalsIgnoreCase(name)) {
                charClass = entryClass;
                rank = entry.getRank();
                rankInClass = classes.get(charClass);
                charName = entry.getCharacter().getName();
                level = entry.getCharacter().getLevel();
                break;
            }
        }

        return new RankStatus(charName, level, rank, rankInClass, charClass);
    }
}
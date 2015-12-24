package halsbot.poe;

import halsbot.TwitchBot;
import halsbot.droplist.Drop;
import halsbot.droplist.POEDropList;
import halsbot.poe.equip.Equipment;
import halsbot.poe.ladder.Ladder;
import halsbot.poe.ladder.RankStatus;
import halsbot.poe.race.NoRaceException;
import halsbot.poe.race.Race;
import halsbot.poe.race.RaceModifier;
import halsbot.webutil.WebUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static halsbot.webutil.WebUtil.readJsonFromUrl;

public class PoE {

    private final static String exiletools_ladder_url = "http://api.exiletools.com/ladder?league=allActive";
    private final static String exiletools_leaguelist_url = "http://api.exiletools.com/ladder?listleagues=1";
    private final static String lootfilter_url = "http://pastebin.com/Af00CbhA";
    private final static String profile_url = "https://www.pathofexile.com/account/view-profile/";

    private TwitchBot bot;
    private String account;
    private String channel;
    private POEDropList droplist;

    private String league;
    private String characterName;
    private int level;

    public PoE(TwitchBot bot, String channel, String account) {
        this.account = account;
        this.channel = channel;
        this.bot = bot;
        this.droplist = new POEDropList();
    }

    public void getProfilePage() {
        bot.sendMessage(channel, profile_url + account);
    }

    private void updateLeagueAndCharacter(String account, boolean checkQuery) throws IOException {
        level = 0;
        if (checkQuery) {
            String league = "";
            try {
                JSONObject characters = readJsonFromUrl(exiletools_ladder_url + "&accountName=" + account);
                Iterator<String> keys = characters.keys();

                long lastSeen = 0;

                while (keys.hasNext()) {
                    String key = keys.next();
                    if (characters.get(key) instanceof JSONObject) {
                        JSONObject character = characters.getJSONObject(key);

                        long last = Long.parseLong(character.getString("lastOnline"));

                        if (last > lastSeen) {
                            lastSeen = last;
                            league = character.getString("league");
                            characterName = key.substring(key.indexOf(".") + 1);
                        }
                    }
                }
            } catch (JSONException e) {
                JSONObject character = readJsonFromUrl(exiletools_ladder_url + "&charName=" + account);
                character = character.getJSONObject(character.keys().next());
                league = character.getString("league");
                characterName = account;
            }

            if (league.equals("")) {
                bot.echo("League not found");
                this.league = "";
            } else {
                JSONObject leagues = readJsonFromUrl(exiletools_leaguelist_url);
                this.league = leagues.getJSONObject(league).getString("apiName");
            }
        } else {
            String profilePage = WebUtil.readPage(profile_url + account + "/characters");

            Pattern char_info_pattern = Pattern.compile("new C\\((.*)\\)");
            Matcher m = char_info_pattern.matcher(profilePage);

            m.find();
            String char_info_string = m.group(0).replace("new C(", "").replace(")", "");
            JSONObject char_info = new JSONObject(char_info_string);

            this.characterName = char_info.getString("name");
            this.league = char_info.getString("league");
            this.level = char_info.getInt("level");
        }
    }

    public void lootfilter() {
        bot.sendMessage(channel, "NeverSink's loot filter: " + lootfilter_url);
    }

    public void track(String account) {
        this.account = account;
        bot.sendMessage(channel, "Now tracking " + account);
    }

    public void addDrop(String name) {
        String drop = droplist.addDrop(name);
        bot.sendMessage(channel, "Added: " + drop);
    }

    public void removeDrop() {
        Drop removed = droplist.removeLast();
        bot.sendMessage(channel, "Removed: " + removed);
    }

    public void getSkillTree() {
        try {
            updateLeagueAndCharacter(account, false);
            String treeUrl = BuildTree.loadTree(characterName, account);
            bot.sendMessage(channel, treeUrl);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void getGems() {
        try {
            updateLeagueAndCharacter(account, false);
            Equipment equip = Equipment.loadFromJSon(characterName, account);
            bot.sendMessage(channel, equip.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void getDrops() {
        bot.sendMessage(channel, droplist.getDrops());
    }

    public void rank() {
        if (account == null) {
            bot.sendMessage(channel, "No character being tracked at this time");
        } else {
            rank(account, false);
        }
    }

    public void rank(String query, boolean checkQuery) {
        try {
            updateLeagueAndCharacter(query, checkQuery);

            if (league.equals("")) {
                bot.sendMessage(channel, "No alive character found");
            } else {
                getRank();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void getRank() throws IOException {
        Ladder ladder = Ladder.getLadderForLeague(league);
        RankStatus rank = ladder.getRankForQuery(characterName, true);
        if (rank.notFound) {
            getCharacterRank();
        } else {
            bot.sendMessage(channel, rank.name + " (Level " + rank.level + ") in " + league + " is Rank " + rank.rank + " Overall and Rank " + rank.classRank + " " + rank.charClass);
        }

    }

    private void getCharacterRank() throws IOException {
        try {
            JSONObject character = readJsonFromUrl(exiletools_ladder_url + "&charName=" + characterName);
            character = character.getJSONObject(character.keys().next());

            int rank = Integer.parseInt(character.getString("rank"));
            int level = Integer.parseInt(character.getString("level"));

            bot.sendMessage(channel, characterName + " (Level " + level + ") in " + league + " is Rank " + rank + " Overall");
        } catch (JSONException e) {
            if (level > 0) {
                bot.sendMessage(channel, characterName + " (Level " + level + " isn't ranked in " + league + " yet");
            } else {
                bot.sendMessage(channel, characterName + " isn't ranked in " + league + " yet");
            }
        }
    }

    public void getLadder() {
        if (account == null) {
            bot.sendMessage(channel, "No character being tracked at this time");
            return;
        }
        try {
            updateLeagueAndCharacter(account, false);

            if (league.equals("")) {
                bot.sendMessage(channel, "No league found");
            } else {
                JSONObject ladder = readJsonFromUrl("http://api.pathofexile.com/leagues/" + URLEncoder.encode(league, "UTF-8"));
                String url = ladder.getString("url");
                SimpleDateFormat format = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.GERMAN);
                format.setTimeZone(TimeZone.getTimeZone("GMT"));
                Date date = format.parse(ladder.getString("endAt"));
                bot.sendMessage(channel, "Ladder and forum thread for " + league + ": " + url + "   League ends: " + date);
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public void getRaceRank(String name) {
        try {
            Race race = Race.getCurrentRace();
            if (race.isRunning()) {
                Ladder ladder = Ladder.getLadderForLeague(race.getName());
                RankStatus rank = ladder.getRankForQuery(name, false);

                if (rank.notFound) {
                    ladder = Ladder.getLadderForLeague(race.getName());
                    rank = ladder.getRankForQuery(name, true);

                    if (rank.notFound) {
                        if (ladder.getTotal() < 200) {
                            bot.sendMessage(channel, name + " hasn't finished the race in " + race.getName() + " yet");
                        } else {
                            bot.sendMessage(channel, name + " is not top 200 in Race " + race.getName());
                        }
                    } else {
                        bot.sendMessage(channel, rank.name + " (Level " + rank.level + ") in " + race.getName() + " is Rank " + rank.rank + " Overall and Rank " + rank.classRank + " " + rank.charClass);
                    }
                } else {
                    bot.sendMessage(channel, rank.name + " (Level " + rank.level + ") in " + race.getName() + " is Rank " + rank.rank + " Overall and Rank " + rank.classRank + " " + rank.charClass);
                }
            } else {
                bot.sendMessage(channel, "No race currently active");
            }
        } catch (NoRaceException nr) {
            bot.sendMessage(channel, "There are no races scheduled at the moment");
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public void getRaceRank() {
        getRaceRank(account);
    }

    public void getRaceLadder() {
        try {
            Race race = Race.getCurrentRace();
            if (!race.hasStarted()) {
                bot.sendMessage(channel, "Ladder and forum thread for " + race.getName() + ": " + race.getUrl() + "   Race starts: " + race.getStart());
            } else if (race.isRunning()) {
                bot.sendMessage(channel, "Ladder and forum thread for " + race.getName() + ": " + race.getUrl() + "   Race ends: " + race.getEnd());
            } else {
                bot.sendMessage(channel, "No race found");
            }
        } catch (NoRaceException nr) {
            bot.sendMessage(channel, "There are no races scheduled at the moment");
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public void getRaceMods() {
        try {
            Race race = Race.getCurrentRace();

            bot.sendMessage(channel, "Mods for " + race.getName() + ":");

            for (RaceModifier mod : race.getMods()) {
                bot.sendMessage(channel, mod.getDescription());
            }
        } catch (NoRaceException nr) {
            bot.sendMessage(channel, "There are no races scheduled at the moment");
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public void raceTimeLeft() {
        try {
            Race race = Race.getCurrentRace();

            if (!race.hasStarted()) {
                long diff = race.timeToStart();

                long diffSeconds = diff / 1000 % 60;
                long diffMinutes = diff / (60 * 1000) % 60;
                long diffHours = diff / (60 * 60 * 1000) % 60;

                if (diffHours > 0) {
                    bot.sendMessage(channel, diffHours + " hours " + diffMinutes + " minutes to the start of " + race.getName());
                } else if (diffMinutes == 0) {
                    bot.sendMessage(channel, diffSeconds + " seconds to the start of " + race.getName());
                } else {
                    bot.sendMessage(channel, diffMinutes + " minutes to the start of " + race.getName());
                }
            } else if (race.isRunning()) {
                long diff = race.timeLeft();

                long diffSeconds = diff / 1000 % 60;
                long diffMinutes = diff / (60 * 1000) % 60;
                long diffHours = diff / (60 * 60 * 1000) % 60;

                if (diffHours > 0) {
                    bot.sendMessage(channel, diffHours + " hours " + diffMinutes + " minutes to the end of " + race.getName());
                } else if (diffMinutes == 0) {
                    bot.sendMessage(channel, diffSeconds + " seconds to the end of " + race.getName());
                } else {
                    bot.sendMessage(channel, diffMinutes + " minutes to the end of " + race.getName());
                }
            } else {
                bot.sendMessage(channel, "No race found");
            }
        } catch (NoRaceException nr) {
            bot.sendMessage(channel, "There are no races scheduled at the moment");
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }
}

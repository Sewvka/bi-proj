package ru.nsu.ccfit.evolution.user.framework;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Array;
import ru.nsu.ccfit.evolution.user.actors.*;

import java.security.InvalidParameterException;
import java.util.ArrayList;

import static com.badlogic.gdx.scenes.scene2d.actions.Actions.moveTo;

public class SessionStage extends Stage {
    public final ArrayList<PlayerView> players;
    public final FoodTray food;
    private final int playerCount;
    private final SessionScreen sessionScreen;
    public SessionStage(EvolutionGame game, int playerCount, SessionScreen sessionScreen) {
        super(sessionScreen.getViewport());
        if (playerCount > 4) throw new InvalidParameterException("Game does not support more than 4 players!");
        //if (playerCount < 2) throw new InvalidParameterException("Game requires at least two players!");
        if (playerCount < 1) throw new InvalidParameterException("Game requires at least one player!");
        this.playerCount = playerCount;
        this.sessionScreen = sessionScreen;
        players = new ArrayList<>(playerCount);
        food = new FoodTray(game);
        food.setPosition(GameScreen.WORLD_SIZE_X / 16, GameScreen.WORLD_SIZE_Y/8);

        //в конечном итоге номера ID игроков должен будет предоставлять сервер
        players.add(new PlayerView(game, true, 0));
        addActor(players.get(0));
        for (int i = 1; i < playerCount; i++) {
            players.add(new PlayerView(game, false, i));
            addActor(players.get(i));
        }
        alignPlayers();
    }

    public void feedToken() {
        food.removeFood();
        getSelectedCreature().addFood();
    }

    public void feedCreature(int creatureIndex, int playerID, boolean fromTray) {
        if (fromTray) {
            food.removeFood();
        }
        players.get(playerID).getTable().get(creatureIndex).addFood();
    }

    public SessionScreen getSessionScreen() {
        return sessionScreen;
    }

    public FoodTray getFood() {
        return food;
    }

    public void initDevelopment() {
        //всем игрокам даются карты
        for (PlayerView p : players) {
            p.getHand().addAction(moveTo(p.getHand().getX(), 0, 0.3f));
            Array<Integer> drawn = sessionScreen.getServerEmulator().requestDrawnCards(p.getPlayerID());
            if (drawn != null) {
                p.getHand().addAll(drawn);
            }
        }
        setHandTouchable(Touchable.enabled);
    }

    public void initFeeding(int foodTotal) {
        setHandTouchable(Touchable.disabled);
        for (PlayerView p : players) {
            HandView h = p.getHand();
            h.addAction(moveTo(h.getX(), -GameScreen.WORLD_SIZE_Y/9, 0.3f));
            h.setTouchable(Touchable.disabled);
        }
        food.init(foodTotal);
        addActor(food);
    }

    public void initExtinction() {
        food.remove();
        for (PlayerView p : players) {
            Array<Integer> extinctIDs = sessionScreen.getServerEmulator().requestExtinctCreatures(p.getPlayerID());
            Array<CreatureView> extinct = new Array<>();
            for (int i : new Array.ArrayIterator<>(extinctIDs)) {
                extinct.add(p.getTable().get(i));
            }
            for (CreatureView c : new Array.ArrayIterator<>(extinct)) {
                p.getTable().removeCreature(c);
            }

            p.getTable().clearAllFood();
        }
    }

    public void setHandTouchable(Touchable touchable) {
        for (PlayerView p : players) {
            p.getHand().setTouchable(touchable);
        }
    }

    public TableView getSelectedTable() {
        for (PlayerView p : players) {
            if (p.getTable().isSelected()) return p.getTable();
        }
        return null;
    }

    public boolean isTableSelected() {
        return getSelectedTable() != null;
    }

    private void alignPlayers() {
        if (playerCount < 1) return;
        players.get(0).setAlignment("bottom");
        switch (playerCount) {
            case 2:
                players.get(1).setAlignment("top");
                break;
            case 3:
                players.get(1).setAlignment("topL");
                players.get(2).setAlignment("topR");
                break;
            case 4:
                players.get(1).setAlignment("left");
                players.get(2).setAlignment("top");
                players.get(3).setAlignment("right");
                break;
        }
    }

    public boolean isCreatureSelected() {
        for (PlayerView p : players) {
            if (p.getTable().isCreatureSelected()) return true;
        }
        return false;
    }

    public CreatureView getSelectedCreature() {
        for (PlayerView p : players) {
            if (p.getTable().isCreatureSelected()) return p.getTable().getSelectedCreature();
        }
        return null;
    }
}

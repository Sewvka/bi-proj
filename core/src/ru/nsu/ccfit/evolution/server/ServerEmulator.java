package ru.nsu.ccfit.evolution.server;

import com.badlogic.gdx.utils.Array;
import ru.nsu.ccfit.evolution.common.Abilities;
import ru.nsu.ccfit.evolution.user.framework.SessionScreen;

import java.util.Random;

//на данный момент этот класс симулирует работу удалённого сервера.
public class ServerEmulator {
    private final static int GAME_START = 0;
    private final static int DEVELOPMENT = 1;
    private final static int FEEDING = 2;
    private final static int EXTINCTION = 3;
    private final PlayerModel[] players;
    private int gameStage;
    private int foodTotal;
    private int foodLeft;
    private int activePlayerIndex;
    private final int playerCount;
    private final Deck deck;
    private final SessionScreen gameScreen;

    public ServerEmulator(SessionScreen gameScreen) {
        playerCount = 1;
        this.gameScreen = gameScreen;
        deck = new Deck();
        players = new PlayerModel[playerCount];
        for (int i = 0; i < playerCount; i++) {
            players[i] = new PlayerModel(i);
        }
        gameStage = GAME_START;
    }

    public void init() {
        advanceStage();
    }

    private void initDevelopment() {
        gameStage = DEVELOPMENT;
        //игроки получают необходимое кол-во карт
        for (PlayerModel p : players) {
            if (p.getHand().getCardCount() == 0 && p.getTable().getCreatureCount() == 0) {
                for (int i = 0; i < 6; i++) p.getHand().drawCard(deck.draw());
            } else {
                for (int i = 0; i < p.getTable().getCreatureCount() + 1; i++) {
                    p.getHand().drawCard(deck.draw());
                }
            }
        }
        activePlayerIndex = 0;
        gameScreen.initDevelopment();
    }

    private void initFeeding() {
        gameStage = FEEDING;
        for (PlayerModel p : players) {
            p.getTable().resetActivations();
            p.passedTurn = false;
        }
        foodTotal = new Random().nextInt(6) + 3;
        foodLeft = foodTotal;
        gameScreen.initFeeding();
    }

    private void initExtinction() {
        gameStage = EXTINCTION;
        for (PlayerModel p : players) {
            for (int i = 0; i < p.getTable().getCreatureCount(); i++) {
                CreatureModel c = p.getTable().getCreature(i);
                if (!c.isFed()) {
                    p.addExtinctCreature(c);
                    p.getTable().removeCreature(i);
                }
            }
            p.getTable().clearAllFood();
        }

        gameScreen.initExtinction();
        advanceStage();
    }

    public Array<Integer> requestExtinctCreatures(int playerID) {
        if (falseID(playerID)) return null;
        return (getPlayer(playerID).getExtinctCreatures());
    }

    public void nextTurn() {
        if (allPlayersPassed()) {
            advanceStage();
        }
        else {
            activePlayerIndex++;
            if (activePlayerIndex >= playerCount) activePlayerIndex = 0;
            if (players[activePlayerIndex].passedTurn) nextTurn();
        }
    }

    private void advanceStage() {
        resetPasses();
        activePlayerIndex = 0;

        if (gameStage == DEVELOPMENT) {
            initFeeding();
        } else if (gameStage == FEEDING) {
            initExtinction();
        } else if (gameStage == EXTINCTION || gameStage == GAME_START) {
            initDevelopment();
        }
    }

    public Array<Integer> requestDrawnCards(int playerID) {
        if (gameStage != DEVELOPMENT) return null;
        PlayerModel player = getPlayer(playerID);
        if (player == null) return null;
        Array<Integer> drawn = new Array<>();
        drawn.addAll(player.getHand().getDrawnCards());
        player.getHand().commitDrawnCards();
        return drawn;
    }

    private PlayerModel getPlayer(int playerID) {
        for (PlayerModel p : players) {
            if (p.getID() == playerID) return p;
        }
        return null;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public int getGameStage() {
        return gameStage;
    }

    public boolean requestCreaturePlacement(int selectedCard, int playerID) {
        if (gameStage != DEVELOPMENT) return false;
        if (falseID(playerID) || isInactive(playerID)) return false;
        if (falseCardIndex(selectedCard, playerID)) return false;
        PlayerModel player = getPlayer(playerID);
        if (player.getHand().getCardCount() < 1) return false;

        player.getHand().removeCard(selectedCard);
        player.getTable().addCreature();
        nextTurn();
        return true;
    }

    public boolean requestAbilityPlacement(String ability, int selectedCard, int selectedCreature, int playerID, int targetID) {
        if (gameStage != DEVELOPMENT) return false;
        if (Abilities.isCooperative(ability)) return false;
        if (falseID(playerID) || falseID(targetID) || isInactive(playerID)) return false;
        if (falseCardIndex(selectedCard, playerID)) return false;
        if (falseCreatureIndex(selectedCreature, targetID)) return false;
        PlayerModel player = getPlayer(playerID);
        PlayerModel target = getPlayer(targetID);
        if (!ability.equals("parasite") && playerID != targetID) return false;
        if (!player.getHand().containsAbility(ability, selectedCard)) return false;
        if (target.getTable().getCreatureCount() < 1) return false;
        if (target.getTable().getCreature(selectedCreature).hasAbility(ability) && !ability.equals("fat")) return false;

        player.getHand().removeCard(selectedCard);
        target.getTable().addAbility(selectedCreature, ability);
        nextTurn();
        return true;
    }

    public boolean requestCoopAbilityPlacement(String ability, int selectedCard, int selectedCreatureIndex1, int selectedCreatureIndex2, int playerID) {
        if (gameStage != DEVELOPMENT) return false;
        if (!Abilities.isCooperative(ability)) return false;
        if (falseID(playerID) || isInactive(playerID)) return false;
        if (falseCardIndex(selectedCard, playerID) || falseCreatureIndex(selectedCreatureIndex1, playerID) || falseCreatureIndex(selectedCreatureIndex2, playerID))
            return false;
        PlayerModel player = getPlayer(playerID);
        if (!player.getHand().containsAbility(ability, selectedCard) || player.getTable().getCreatureCount() < 2)
            return false;
        if (player.getTable().getCreature(selectedCreatureIndex1).hasCoopAbility(ability, selectedCreatureIndex2) || player.getTable().getCreature(selectedCreatureIndex2).hasCoopAbility(ability, selectedCreatureIndex1))
            return false;

        player.getHand().removeCard(selectedCard);
        player.getTable().addCoopAbility(selectedCreatureIndex1, selectedCreatureIndex2, ability);
        nextTurn();
        return true;
    }

    private boolean falseID(int playerID) {
        return playerID >= playerCount || playerID < 0;
    }

    private boolean isInactive(int playerID) {
        return players[activePlayerIndex].getID() != playerID;
    }

    private boolean falseCreatureIndex(int creatureIndex, int playerID) {
        return (creatureIndex >= getPlayer(playerID).getTable().getCreatureCount() || creatureIndex < 0);
    }

    private boolean falseCardIndex(int cardIndex, int playerID) {
        return (cardIndex >= getPlayer(playerID).getHand().getCardCount() || cardIndex < 0);
    }

    public int requestFatActivation(int playerID, int creatureIndex) {
        if (falseID(playerID) || falseCreatureIndex(creatureIndex, playerID) || isInactive(playerID)) return 0;
        CreatureModel c = getPlayer(playerID).getTable().getCreature(creatureIndex);
        int foodRemaining = c.foodRequired() - c.getFood();
        int fatConsumed = Math.min(foodRemaining, c.getFatStored());
        c.removeFat(fatConsumed);
        c.addFood(fatConsumed);
        return fatConsumed;
    }

    public boolean requestPredation(int predatorIndex, int preyIndex, int playerID, int targetID) {
        if (falseCreatureIndex(predatorIndex, playerID) || falseCreatureIndex(preyIndex, targetID)) return false;
        if (falseID(playerID) || falseID(targetID) || isInactive(playerID)) return false;
        if (gameStage != FEEDING) return false;
        PlayerModel player = getPlayer(playerID);
        PlayerModel target = getPlayer(targetID);
        CreatureModel predator = player.getTable().getCreature(predatorIndex);
        CreatureModel prey = target.getTable().getCreature(preyIndex);

        if (checkPredationConditions(predator, prey)) {
            target.getTable().removeCreature(preyIndex);
            predator.addFood(2);
            nextTurn();
            return true;
        }
        return false;
    }

    private boolean checkPredationConditions(CreatureModel predator, CreatureModel prey) {
        if (predator.equals(prey)) return false;
        if (predator.preyedThisRound || !predator.canEatMore()) return false;
        if (prey.hasAbility("burrowing") && prey.isFed()) return false;
        if (prey.hasAbility("camouflage") && !predator.hasAbility("sharp_vision")) return false;
        if (prey.hasAbility("high_body_weight") && !predator.hasAbility("high_body_weight")) return false;
        return prey.hasAbility("swimmer") == predator.hasAbility("swimmer");
    }

    public int getFoodTotal() {
        return foodTotal;
    }

    public boolean requestFeed(int selectedCreature, int playerID) {
        if (gameStage != FEEDING) return false;
        if (falseCreatureIndex(selectedCreature, playerID)) return false;
        if (falseID(playerID) || isInactive(playerID)) return false;
        CreatureModel c = getPlayer(playerID).getTable().getCreature(selectedCreature);

        if (foodLeft > 0) {
            if (c.addFood()) {
                foodLeft--;
                nextTurn();
                return true;
            }
        }
        return false;
    }

    public void requestPassTurn(int playerID) {
        if (falseID(playerID) || isInactive(playerID)) return;
        PlayerModel player = getPlayer(playerID);
        player.passedTurn = true;
        nextTurn();
    }

    public int getStage() {
        return gameStage;
    }

    private boolean allPlayersPassed() {
        for (PlayerModel player : players) {
            if (!player.passedTurn) return false;
        }
        return true;
    }

    private void resetPasses() {
        for (PlayerModel player : players) {
            player.passedTurn = false;
        }
    }
}

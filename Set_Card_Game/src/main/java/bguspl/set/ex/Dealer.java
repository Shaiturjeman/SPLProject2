package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.Collections;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    

    //dealer Thread 
    private Thread dealerThread ;
    
    //cards need to be removed
    private Integer[] cardsShouldBeRemoved ;






    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        Collections.shuffle(deck);
        cardsShouldBeRemoved = new Integer[env.config.featureSize];
        for(int i = 0; i < env.config.featureSize; i++){
            cardsShouldBeRemoved[i] = null;
        }

        
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        for (Player player : players) {
            Thread playerThread = new Thread(player, "playerID: " + player.id);
            playerThread.start();
        }
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            Collections.shuffle(deck);
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            updateTimerDisplay(false);
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();

            // if(table.countCards()!= 0 && !deck.isEmpty()){
            //     List<Integer> cardsOnTable = table.getCardOnTabele();
            //     if(env.util.findSets(cardsOnTable, 1).size() == 0){
            //         removeAllCardsFromTable();
            //         placeCardsOnTable();
            //         updateTimerDisplay(true);
            //     }
            // }
            
             if(deck.isEmpty() && table.countCards() != 0){
                List<Integer> cardsOnTable = table.getCardOnTabele();
                if(env.util.findSets(cardsOnTable, 1).size() == 0){
                    announceWinners();
                    terminate();
                }
            }
        }

    }

    /**
     * Called when the game should be terminated.
     */

    public void terminate() {
        terminate = true;
        for(Player player : players){
            if(!player.getTerminate()){
                player.terminate();
            }
        }
        env.ui.dispose();

    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        for(int i = 0; i < cardsShouldBeRemoved.length; i++){
            if(cardsShouldBeRemoved[i] == null){
                for(int j = 0; j < env.config.tableSize; j++){
                    if(table.slotToCard[j] == cardsShouldBeRemoved[i]){
                        table.removeCard(j);
                        cardsShouldBeRemoved[i] = null;
                    }
                }
            }

        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        IntStream stream = IntStream.range(0, env.config.tableSize);    
        Stream<Integer> boxedStream = stream.boxed();
        List<Integer> slotList = boxedStream.collect(Collectors.toList());
        Collections.shuffle(slotList);
        for (int s: slotList){
            if(deck.size()!=0  && table.slotToCard[s] == null){
                table.placeCard(deck.remove(0), s);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            this.dealerThread.sleep(2000);
        } catch (InterruptedException ignored) {
        }

    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(reset && !shouldFinish()){
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(),false);
        }
        else if(!shouldFinish()){
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        }
        else{
            env.ui.setCountdown(env.config.turnTimeoutWarningMillis, true);
        }

    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        synchronized(table){
            if(table.countCards() != 0){
                for(int i = 0; i < env.config.tableSize; i++){
                    if(table.slotToCard[i] != null){
                        deck.add(table.slotToCard[i]);
                        table.removeCard(i);
                    }
                }
                for(Player player : players){
                    player.resetTokens();;
                }
        
            }
        }

        
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int[] scores = new int[players.length];
        for (int i = 0; i < players.length; i++) {
            scores[i] = players[i].score();
        }
        int maxScore = 0;
        for (int score : scores) {
            if (score > maxScore) {
                maxScore = score;
            }
        }
        for (int i = 0; i < players.length; i++) {
            if (scores[i] == maxScore) {
                env.ui.placeToken(i, i);
            }
        }
        


    }

    public boolean SetCheck(Integer[] cards){
        int[] cardsCopy = new int[cards.length];
        for(int i = 0; i < cards.length; i++){
            cardsCopy[i] = cards[i];
        }
        if(env.util.testSet(cardsCopy)){
            cardsShouldBeRemoved = cards;
            return true;
        }
        return false;
        
    }
}

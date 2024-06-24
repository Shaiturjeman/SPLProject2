package bguspl.set.ex;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.Random;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * Queue that handles the upcoming moves of the player.
     */
    public  LinkedBlockingQueue<Integer> moves ;
    private Integer[] playerTokens ;
    private int TokensCounter;
    public Dealer dealer;






    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.moves = new LinkedBlockingQueue<>();
        this.playerTokens = new Integer[env.config.featureSize];
        for(int i=0; i < env.config.featureSize; i++)
        {
            playerTokens[i] = -1;
        }
        this.TokensCounter = 0;
        this.dealer = dealer;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            if(!moves.isEmpty()){
                int slot = moves.poll();
                System.out.println("Player " + id + " slot: " + slot);
                if(table.slotToCard[slot]!=null){
                    System.out.println("Card in slot: " + table.slotToCard[slot]);
                    if(TokenPlaced(slot)){
                        System.out.println("Token already placed");
                        System.out.println("Dealer is goin to delete the token from the table  ");
                        removeTheToken(slot);

                    }
                    else if(this.TokensCounter >= env.config.featureSize){

                        System.out.println("Feature size reached");
                        
                        penalty();
                    }
                    else{
                        tokenPlaceAndCheck(slot);
                    }
                }
            }
        } 
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");

    }



    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                
                int nextMove = GenerateMove();
                if(!moves.isEmpty()){}
                if (table.slotToCard[nextMove] != null) {
                    keyPressed(nextMove);
                    if (TokenPlaced(nextMove)) {
                        removeTheToken(nextMove);
                    }  
                    else if (this.TokensCounter >= env.config.featureSize) {
                        penalty();
                    }
                    else {
                        tokenPlaceAndCheck(nextMove);
                    }
                }
                
            }
                try {
                    synchronized (this) { 
                        wait();
                     }
                } catch (InterruptedException ignored) {}
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    private int GenerateMove() {
        Random rMove = new Random();
        int nextMove = rMove.nextInt(env.config.tableSize);
        return nextMove;
    }

    // /**
    //  * A method that finds a potential set of cards on the table.
    //  */
    // private List<int[]> findPotentialSet() {
    //     List<Integer> cardsOnDeck = new LinkedList<>();
    //     for (int i = 0 ; i < table.slotToCard.length; i++)
    //     {
    //         if (table.slotToCard[i] != null && table.tokens[i][0] == null)
    //         {
    //             cardsOnDeck.add(i);
    //         }
    //     }
    //     List<int[]> potentialSets = env.util.findSets(cardsOnDeck, 1);
    //     return potentialSets;

    // } 
    

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true; 
        if (aiThread != null)
        {
            aiThread.interrupt();
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (!terminate)
        {
            try 
            {
                synchronized(this.moves){
                    moves.put(slot);
                    moves.notifyAll();
                }
                
            } 
            catch (InterruptedException ignored) {}
        }
        

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {   
 
        //increase the player's score by 1.
        score++;

        //update the player's score in the ui.
        env.ui.setScore(id, score);

        //freeze the player for marking a legal set.
        long freezeTime = env.config.pointFreezeMillis;
        try
        {
            while(freezeTime > 0)
            {
                env.ui.setFreeze(id, freezeTime);
                Thread.sleep(freezeTime);               
                freezeTime -= 1000;
            }
            env.ui.setFreeze(id, 0);
        
        this.resetTokens();
        this.TokensCounter = 0;
        this.moves.clear();
        }
        catch (InterruptedException ignored){}


    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {

        //freeze the player for marking an illegal set.
        long freezeTime = env.config.penaltyFreezeMillis;
        try
        {
            while(freezeTime > 0)
            {
                env.ui.setFreeze(id, freezeTime);
                Thread.sleep(freezeTime);               
                freezeTime -= 1000;
            }
            env.ui.setFreeze(id, 0);
        
        this.resetTokens();
        this.TokensCounter = 0;
        this.moves.clear();
        }
        catch (InterruptedException ignored){}

    }

    public int score() {
        return score;
    }
    // Check if the game should be terminated
    public boolean getTerminate() {
        return terminate;
    }
    // Check if the token is  already placed on the table
    public boolean TokenPlaced(int slot) {
        // synchronized(table){
            for(int i =0; i < playerTokens.length; i++)
            {
                if(playerTokens[i] != -1)
                {
                    int playerCardToSlot = table.cardToSlot[playerTokens[i]];
                    if(playerCardToSlot == slot)
                    {
                        return true;
                    } 
                }

            }

        return false;

    }


    // Remove the token from the table
    public void removeTheToken(int slot) {
        for(int i=0 ; i<playerTokens.length; i++)
            if(playerTokens[i] == table.slotToCard[slot]){
                playerTokens[i] = -1;
            }
        boolean done = this.table.removeToken(this.id, slot);
        if(done)    
            TokensCounter--;
    }


    // Place a token on the table and check if a set is formed
    public void tokenPlaceAndCheck(int slot) {
        if(TokensCounter < env.config.featureSize)
        {
            this.table.placeToken(this.id, slot);
            TokensCounter++;
            boolean placed = false;
            for(int i=0; i<playerTokens.length && placed == false; i++){
                if(playerTokens[i] != null && table.slotToCard[slot] != null){
                    if(playerTokens[i] == -1){
                        playerTokens[i] = table.slotToCard[slot];
                        placed = true;
                        
                    }

                }

            
            }
            if(placed == false){
                penalty();
            }

            if(this.TokensCounter == env.config.featureSize){
                boolean legalSet = this.dealer.SetCheck(playerTokens);
                if(legalSet){

                    this.resetTokens();
                    this.TokensCounter = 0;
                    this.moves.clear();
                    point();
                    
                }
                else{

                    penalty();
                }

            }
        }

    else if(this.TokensCounter >= env.config.featureSize){

        penalty();
    }
        
    }
 // Reset the tokens of the player and the counter
    public void resetTokens(){
        synchronized(this){
        for(int i=0; i<this.playerTokens.length; i++)
        {
            if(this.playerTokens[i] != -1 && playerTokens[i] != null)
            {
                int pToken = table.cardToSlot[this.playerTokens[i]];
                if(pToken != -1)
                {
                    table.removeToken(this.id, pToken);
                }
            }
            playerTokens[i] = -1;
        }
        TokensCounter = 0;
        }
    }
}

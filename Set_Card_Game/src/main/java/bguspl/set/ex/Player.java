package bguspl.set.ex;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;

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
    public final BlockingQueue<Integer> moves = new LinkedBlockingQueue<>();




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
            try
            {
                int slot = moves.take();
                proccessAction(slot);
            }
            catch (InterruptedException ignored) 
            {
                Thread.currentThread().interrupt();
            }
            
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * place or remove tokens on the table according to the player's queue of moves.
     */
    private void proccessAction(int slot) {
        synchronized(this) { 
            if(terminate)
            {
                return;
            }

            //if the slot is empty, place a token.
            if(table.slotToCard[slot] == null)
            {
                table.placeToken(id, slot);
                env.logger.info("player " + id + " placed a token in slot " + slot);
                env.ui.placeToken(id, slot);
            }

            //remove the token from the slot.
            else if(table.slotToCard[slot] == id)
            {
                table.removeToken(id, slot);
                env.logger.info("player " + id + " removed a token from slot " + slot);
                env.ui.removeToken(id, slot);
            }
        }
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
                // TODO implement player key press simulator
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true; 
        playerThread.interrupt();
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
                moves.put(slot);
            } 
            catch (InterruptedException ignored) 
            {
                Thread.currentThread().interrupt();
            }
        }
        

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {   
        synchronized (this) { notify(); }

        //increase the player's score by 1.
        score++;

        //update the player's score in the ui.
        env.ui.setScore(id, score);

        //freeze the player for marking a legal set.
        env.logger.info("player " + id + " has been frozen");
        env.ui.setFreeze(id, env.config.pointFreezeMillis);


        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {

        //freeze the player for marking an illegal set.
        env.logger.info("player " + id + " has been penalized");
        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
        
        try
        {
            Thread.sleep(env.config.penaltyFreezeMillis);
        }
        catch (InterruptedException ignored)
        {
            Thread.currentThread().interrupt();
        }

        //unfreeze the player when the penalty is over.
        env.ui.setFreeze(id, 0);
        env.logger.info("player " + id + " has been unfrozen");

    }

    public int score() {
        return score;
    }

    public boolean getTerminate() {
        return terminate;
    }
}

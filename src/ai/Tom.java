package ai;


import mcts.ElapsedTimer;
import game.Color;
import game.Game;
import game.SemiPrimitiveGame;
import gtp.Vertex;
import mcts.TreeNode;

/**
 * A player that uses MCTS.
 * @author Thomas Ager (with thanks to mcts.ai)
 */

public class Tom extends Player {
	
	public Tom(boolean binaryScoring, boolean RAVE, int raveParameter, boolean RAVESkip) {
		super("Tom");

    	/* set the values for different features */
    	this.binaryScoring = binaryScoring;
    	this.RAVE = RAVE;
    	this.raveParameter = raveParameter;
    	this.RAVESkip = RAVESkip;
	}

	public void setGame(Game game) {
		this.game = game;
	}

	public int playMove() {

		/* initialize the node that represents the players current position */
		TreeNode tn = new TreeNode(game, null, side, binaryScoring, RAVE, raveParameter, RAVESkip);
			
		/* create a node that has the players current position recorded */
		TreeNode withPlayerNode = new TreeNode(game, tn, side, binaryScoring, RAVE, raveParameter, RAVESkip);
		
		ElapsedTimer t = new ElapsedTimer();
		while(t.elapsed() < 1000) {
	        /* develop the tree in the node with the players current position recorded */
	        withPlayerNode.developTree();
	    }
	        
	    /* select the move from within the node with the developed tree, making use of the recorded position */
	    int move = withPlayerNode.getMove();

	    /* if the opposing players move was a pass and the players current move is useless */
	    if(game.getMove(0) == -1 && getMoveValue(move) <= 0) {
	    	
	    	/* make the move a pass */
	    	move = -1;
	    	
	    }
	    
	    /* play the move on the board for this player */
	    game.play(move);
	    
	    /* return the move */
	    return move;
		
	}
	
}

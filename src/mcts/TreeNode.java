package mcts;

import ai.Configuration;
import ai.Player;
import ai.NoEyeRandomPlayer;

import java.util.LinkedList;

import game.*;
import game.Board.PositionList;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ai.RandomPlayer;

public class TreeNode {
	
	/* initialize the random generation */
    static Random r = new Random();
    
    /* initialize epsilon for use in uct */
    static double epsilon = 1e-6;
    
    /* each node has a game with the current move taken */
    Game currentGame;
    
    /* each node has a parent */
    TreeNode parent;
    
    /* each node has children */
    List<TreeNode> children = new ArrayList<TreeNode>();
    
    /* and a dynamic tree is maintained of only the children who have been visited more than once */
    List<TreeNode> dynamicTreeChildren = new ArrayList<TreeNode>();
    
    /* initialize the values used in uct, one for uct standard and one for rave */
    double[] nVisits = new double[2], totValue = new double[2];
    
    /* the actual uct value, used for quick navigation when searching the tree */
    //double uctValue;
    
    /* the weight, used to balance rave and uct and recorded for quick navigation when 
     * using weighted heuristics
     */
    //double weight;
    
    /* the counter for skipping rave */
    int raveSkipCounter;
    
    /* the playerColor for use in enforcing positive values for the player in the amaf map */
    Color playerColor;
    
    /* setup the testing variable to allow prints or disallow them */
    boolean testing = false;
    
    /* a map of colours used to record if a move was in the players color for amaf */
    Color[] amafMap;
    
    /* each node has the move taken to get to that move */
    public int move;
    
    /* each node has a ruleset defined by the user */
    Configuration nodeRuleSet;
    
    /* set all the values that change for every node */
    public TreeNode(TreeNode parent, Game currentGame, int move, int raveSkipCounter, //double uctValue, double weight, // NOTE // Might not need to m
    		Color playerColor, Configuration nodeRuleSet) {
    	this.parent = parent;
    	this.currentGame = currentGame;
    	this.move = move;
    	this.nodeRuleSet = nodeRuleSet;
    	this.playerColor = playerColor;
    	this.raveSkipCounter = raveSkipCounter;
    }
    
    /* set the values that are persistent across nodes, but given by the user/player */
    public void setRuleSet(Configuration ruleSet) {
    	this.nodeRuleSet = ruleSet;
    }
    public void setPlayerColor(Color playerColor) {
    	this.playerColor = playerColor;
    }

    /* return the size of the board for the node's game */
    public int boardSize() {
    	return this.currentGame.board.getSideSize();
    }

    /* print all the moves of the children of the current node */
    public void printChildren() {
    	for(TreeNode c : children) {
    		print(c.currentGame.getMove(-1));
    	}
    }
    
    /* get the child node that matches the game state given (last move played, from MCTSPlayer) */
    public TreeNode getChild(int lastMove) {
    	
    	/* for every child, check if the move matches the last move */
    	for(TreeNode c : children) {
    		if(c.move == lastMove) {
    			return c;
    		}
    	}
    	
    	/* if we couldn't find a matching expanded child, then create one with the move played out */
    	Game normalGame = currentGame.duplicate();
    	normalGame.play(lastMove);
    	TreeNode newChild = new TreeNode(this, normalGame, lastMove, raveSkipCounter, playerColor, nodeRuleSet);
    	
    	/* and return that one instead */
    	return newChild;
    }
    
    /* develop the tree */
    public void developTree() {
    	
    	/* create a list of all visited nodes to backpropogate later */
        List<TreeNode> visited = new LinkedList<TreeNode>();
        
        /* set the current node to this node, and add it to the visited list */
        TreeNode cur = this;
        visited.add(this);
        
        /* until the bottom of the tree is reached
         * in either dynamic or non-dynamic tree modes */
        boolean atLeafNode = false;
        if(nodeRuleSet.dynamicTree)
        	atLeafNode = cur.isDynamicTreeLeaf();
        else 
        	atLeafNode = cur.isLeaf();
        while (!atLeafNode) {
        	/* follow the highest uct value node, and add it to the visited list */
        	print("navigating to leaf node");
        	if(nodeRuleSet.dynamicTree)
        		cur = cur.dynamicTreeSelect();
        	else
        		cur = cur.select();
            print("Adding: " + cur);
            visited.add(cur);
            if(nodeRuleSet.dynamicTree)
            	atLeafNode = cur.isDynamicTreeLeaf();
            else 
            	atLeafNode = cur.isLeaf();
        }
        
        
        /* at the bottom of the tree expand the children for the node, including a pass move
         * but only if it hasn't been expanded before */
        print("found leaf node, expanding from: " + cur);
        if(cur.children.size() == 0)
        	cur.expand();

        /* get the best child from the expanded nodes, even if it's just passing */
	    print("selecting from: " + cur);
	    TreeNode newNode = cur.select();
	        
	    /* simulate from the node, and get the value from it */
	    print("simulating" + newNode);
	    double value = simulate(newNode);
	    print("got result" + value);

	    /* backpropogate the values of all visited nodes */
	    for (TreeNode node : visited) {
	    	
	    	/* type 0 for just uct updating */
	        node.updateStats(0, value);
		    
	    }
	    
	    /* and if using rave update the subtree of the parent of the simulated node */
	    if(nodeRuleSet.rave || nodeRuleSet.weightedRave) {

	    	/* based on the amaf map of the node that was just simulated */
	    	updateStatsRave(newNode.amafMap, cur, value);
	    }
	    
	    /* if we are using the dynamic tree, update that */
	    if(nodeRuleSet.dynamicTree) 
	    	cur.dynamicTreeExpand();
    }
    
    /* get the highest uct value child node of the node given */
    public int getHighestValueMove() {
    	TreeNode highestValueNode = select();
    	print("NODE SELECTED:" +highestValueNode);
    	return highestValueNode.move;
    }
    
    /* add the node to the dynamic tree if it has been visited more than once */
    public void dynamicTreeExpand() {
    	for (TreeNode c : children) {
    		if(c.nVisits[0] > 1) {
    			System.out.println("found child");
    			dynamicTreeChildren.add(c);
    			
    			/* quitting the search once we've found it */
    			break;
    		}
    	}
    }
    
    /* expand the children of the node */
    public void expand() {
    	
    	/* get all of the empty points on the board */
    	PositionList emptyPoints = currentGame.board.getEmptyPoints();
    	int sizeOfPoints = emptyPoints.size();
    	print("There are currently this many empty points:" +sizeOfPoints + " ");
    	
    	/* for every empty point on the board */
        for (int i=0; i<emptyPoints.size(); i++) {
        	
        	
        	/* if disallowing playing in eyes, create a semiprimitive board with that rule and test playing on it */
        	boolean canPlay = true;
        	if(nodeRuleSet.dontExpandEyes) {
	        	SemiPrimitiveGame duplicateBoard = currentGame.copy();    
	        	canPlay = duplicateBoard.play(emptyPoints.get(i));
        	}
        	
        	/* otherwise just try and play on it normally, ensuring the rules of the game are followed */
        	Game normalGame = currentGame.duplicate();
        	boolean canPlayNormal = normalGame.play(emptyPoints.get(i));
        	
        	/* checking if it is possible to play that point, checking if we're playing into an eye */
        	if(canPlay && canPlayNormal) {
        		
        		/* create a new child for that point */
        		TreeNode newChild = new TreeNode(this, normalGame, emptyPoints.get(i), raveSkipCounter, playerColor, nodeRuleSet);
        		
        		/* and add it to the current nodes children */
        		children.add(newChild);
        		print("added child " + newChild.move);
        	} else {
        		print("cant play");
        	}
        	
        }
        
        /* add a pass move as well as playing on every allowable empty point */
        Game passGame = currentGame.duplicate();
        passGame.play(-1);
		TreeNode passChild = new TreeNode(this, passGame, -1, raveSkipCounter, playerColor, nodeRuleSet);
		
		/* and add it to the current nodes children */
		children.add(passChild);
        
    }
    
    /* prune nodes playing on the first line of the board without surrounding nodes */
    public void prune() {
    	//
    }
    
    /* get the highest value node according to the rules selected */
    private TreeNode select() {
        /* get the best value child node from the tree */
	    TreeNode selected = findBestValueNode(children);
        /* and then it is returned, with a value always selected thanks to randomisation */
        return selected;
        
    }
    
    public TreeNode dynamicTreeSelect() {
    	/* get the best value child node from the dynamic tree children */
	    TreeNode selected = findBestValueNode(dynamicTreeChildren);
        /* and then it is returned, with a value always selected thanks to randomisation */
        return selected;
    }
    
    public TreeNode findBestValueNode(List<TreeNode> children) {
    	/* initialize the values, with the bestvalue put at the smallest possible value */
    	TreeNode selected = null;
        double bestValue = -Double.MAX_VALUE;
        print(""+children);
        for (TreeNode c : children) {
        	double uctValue = 0;
        	/* if the rave skip counter has reached the amount of times to wait until skipping rave,
        	 * or if it is not enabled */
    		if(raveSkipCounter < nodeRuleSet.raveSkip || nodeRuleSet.raveSkip == -1) {
    		    /* get the uct value using standard rules */
    		    uctValue = getUctValue(c); // NOTE // Investigate avoiding recalculation if the node stats have not been updated // NOTE //
    		    
    		    /* and increment the counter */
    		    raveSkipCounter++;
    		    
    		} else if(raveSkipCounter == nodeRuleSet.raveSkip) {

    			/* otherwise disable rave */
    			if(nodeRuleSet.rave = true) {
    				nodeRuleSet.rave = false;
    				/* get the uct value using new rules */
        			uctValue = getUctValue(c);
        			nodeRuleSet.rave = true;
    			}
    			if(nodeRuleSet.weightedRave = true) {
    				nodeRuleSet.weightedRave = false;
    				/* get the uct value using new rules */
        			uctValue = getUctValue(c);
        			nodeRuleSet.weightedRave = true;
    			}
    			if(nodeRuleSet.heuristicRave = true) {
    				nodeRuleSet.heuristicRave = false;
    				/* get the uct value using new rules */
        			uctValue = getUctValue(c);
        			nodeRuleSet.heuristicRave = true;
    			}
    			
    		    /* and set the counter to 0 */
    		    raveSkipCounter = 0;
    		}
        	print("UCT value = " + uctValue);
    		
            /* if the uctvalue is larger than the best value */
        	print("current best value is: "+bestValue);
            if (uctValue > bestValue) {
            	
            	/*the selected node is that child */
                selected = c;
                
                /* and the best value is the current value */
                bestValue = uctValue;
                print("found new bestValue: " + uctValue);
                
            }
        }
        return selected;
    }

    /* check if the current node is a leaf */ 
    public boolean isLeaf() {
    	
    	/* if the size of the children of the node is 0, its a leaf */
    	if(children.size() == 0)
    		return true;
    	return false;
    }
    public boolean isDynamicTreeLeaf() {
    	if(dynamicTreeChildren.size() == 0)
    		return true;
    	return false;
    }
    
    /* perform the calculation required for uct */
    public double calculateUctValue(int type, TreeNode tn) {
    	
    	/* ((total value) / (visits + e)) + (log(visits) / (visits + e) + random number) */
    	return tn.totValue[type] / (tn.nVisits[type] + epsilon) +
                Math.sqrt(Math.log(tn.nVisits[type]+1) / (tn.nVisits[type] + epsilon)) +
                r.nextDouble() * epsilon;
    }
    
    /* perform the calculation needed to weight the node, in order to balance rave and uct */
    public double calculateWeight(TreeNode tn) {
    	
    	/* when only a few simulations have been seen, the weight is closer to 1, weighting the RAVE value more highly
    	 * when many simulations have been seen, the weight is closer to 0, weighting the MC value more highly
    	 */
    	double weight = 1 - (nodeRuleSet.initialWeight * (tn.nVisits[0] / nodeRuleSet.finalWeight));
    	if(tn.nVisits[0] == 0) {
    		return 1;
    	}
    	if(weight < 0) {
    		return 0;
    	}
    	return weight;
    }
    
    /* get the uct value for a node with a small random number to break ties randomly in unexpanded nodes  */
    public double getUctValue(TreeNode tn) {

    	/* if we are using UCT no rave */
        if (nodeRuleSet.uct) {
        	
        	/* get the uct value only */
        	return calculateUctValue(0, tn);
    		
        } else if (nodeRuleSet.rave) {
        	
        	/* get the rave value only */
        	return calculateUctValue(1, tn);
    		
        } else if (nodeRuleSet.weightedRave) {
        	
        	/* calculate it using the weight */
        	double weight = calculateWeight(tn);
        	//System.out.println(weight);
    		return ((1 - weight) * calculateUctValue(0, tn)) + (weight * calculateUctValue(1, tn));
	        
    	}
        return 0;
    }

    /* update the subtrees using RAVE */
    public void updateStatsRave(Color[] amafMap, TreeNode tn, double simulationResult) {
    	
    	/* for every child of this node */
    	for (TreeNode c : tn.children) {

    		/* for every move on the amafmap */
    		for(int i =0; i<amafMap.length;i++) {
    			
				/* if that part is filled in on the amafMap and matches the childs most recently taken move 
				 * and is the right colour */
    			if(amafMap[i] != null && i == c.currentGame.getMove(0) 
    					&& c.currentGame.getNextToPlay() != amafMap[i]) {

    				/* update the total value of that node with the simulation result */
					c.updateStats(1, simulationResult);
					
					/* and quit out of the loop, no need to update multiple times for multiple matches */
					break;
    			}
			}
		
    		/* if there is more subtree to explore */
    		if(c.children.size() > 0) {
    			
	    		/* recursively iterate through the whole subtree */
	    		updateStatsRave(amafMap, c, simulationResult);
    		}
    	}
    }

    /* simulate a random game from a treenode */
    public double simulate(TreeNode tn) {

    	/* initialize the map of who played where for this simulation
    	 * on the node that is to be simulated from */
		tn.amafMap = new Color[tn.currentGame.getSideSize() * tn.currentGame.getSideSize()];
		
    	/* create a random player that cannot play in eyes */
		RandomPlayer randomPlayer = new RandomPlayer();
		
    	/* create a duplicate of the game */
		SemiPrimitiveGame duplicateGame = currentGame.copy();
		
    	/* initialize the game using the duplicate */
    	randomPlayer.startGame(duplicateGame, null);
    	
    	/* until the simulation has finished, play moves */
    	while(!duplicateGame.isOver()) {
    		
    		/* get the move, and play on the board */
    		int move = randomPlayer.playMove();

    		/* if we are using any variation of rave */
    		if (nodeRuleSet.rave) {
    			
	    		/* if the move isn't a pass */
	    		if(move != -1) {
	    			
		    		/* set the current moves colour on the amaf map */
		    		if(randomPlayer.game.getNextToPlay() == playerColor) {
		    			tn.amafMap[move] = playerColor;
		    		} else {
		    			tn.amafMap[move] = playerColor.inverse();
		    		}
	    		}
    		}
    	}
    	
    	/* get the score for the players color, positive or negative depending on colour */
    	float score = duplicateGame.score(playerColor);
    	
    	/* if using binary scoring */
    	if(nodeRuleSet.binaryScoring) {
    		
    		/* return 0 for loss, 1 for win */
	    	if(score > 0)
	    		return 1;
	    	return 0;
	    
	    /* if scoring using our own system return the score value */
    	} else {
    		return score;
    	}
    	
    }
    
    /* methods to print things when explicitly allowed to */
    public void print(String line) {
    	if(testing)
    		System.out.println(line);
    }
    public void print(int line) {
    	if(testing)
    		System.out.println(line);
    }

    /* update the stats for this node */
    public void updateStats(int type, double value) {
    	
    	/* for the uct value or rave value, dependent on the type input */
    	nVisits[type]++;
        totValue[type] += value;
        print("updated stats, visits: " + nVisits[type] + " total value: " + totValue[type]);
    }
    
}
package task4.engine;

import java.util.*;

import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.tools.MarioAIOptions;
import task4.sprites.Mario;
import task4.engine.*;
import task4.level.Level;

public class Simulator {
	
	private class SearchNode {
		// 前状態
		private SearchNode parent = null;
		// 現状態の各状況のスナップショット
		public LevelScene snapshot = null;
		
		// 現状態での行動
		boolean[] action;
		// 繰り返し回数
		int repetition;
		
		private int timeElapsed = 0;
		public float remainingTimeEstimated = 0;
		private float remainingTime = 0;
		
		public boolean hasBeenHurt = false;
		public boolean isInVisitedList = false;
		
		public SearchNode(SearchNode parent, boolean[] action, int repetition) {
			this.parent = parent;
			this.action = action;
			this.repetition = repetition;
			
			if(this.parent != null) {
				this.remainingTimeEstimated = parent.estimateRemainingTimeChild(action, repetition);
				timeElapsed = parent.timeElapsed + repetition;
			} else {
				this.remainingTimeEstimated = calcRemainingTime(levelScene.mario.x, 0);
				timeElapsed = 0;
			}
			this.action = action;
			this.repetition = repetition;
		}
		
		// 現状態から遷移できる次のノードのリストを返す
		public ArrayList<SearchNode> createNextNode() {
			ArrayList<SearchNode> list = new ArrayList<SearchNode>();
			ArrayList<boolean[]> actions = enumerateNextActions(this);
			for(boolean[] action : actions) {
				list.add(new SearchNode(this, action, repetition));	
			}
			return list;
		}
		
		public float calcRemainingTime(float x, float y) {
			float maxSpeed = 10.9090909f;
			return (100000 - (maxForwardMovement(x, 1000) + x)) / maxSpeed - 1000;
		}
		
		public float getRemainingTime() {
			if(remainingTime > 0) {
				return remainingTime;
			} else {
				return remainingTimeEstimated;
			}
		}
		
		public float estimateRemainingTimeChild(boolean[] action, int repetition) {
			float[] t = estimateMaximumForwardMovement(levelScene.mario.xa, action, repetition);
			return calcRemainingTime(levelScene.mario.x + t[0], t[1]);
		}
		
		// 現状態のノードを指定されたステップ数シミュレートする
		public float simulate() {
			levelScene = parent.snapshot;
			parent.snapshot = getSnapshot();
			
			final int initialDamage = calcMarioDamage();
			
			for(int i = 0; i < repetition; ++i) {
				//int x = (int)levelScene.mario.x;
				//int y = (int)levelScene.mario.y;
				//System.out.println("[Debug]advanceStep: " + x + ", " + y);
				advanceStep(action);
				//x = (int)levelScene.mario.x;
				//y = (int)levelScene.mario.y;
				//System.out.println("[Debug]advanceStep: " + x + ", " + y);
			}
			
			// todo
			remainingTime = calcRemainingTime(levelScene.mario.x, levelScene.mario.xa)
							+ (calcMarioDamage() - initialDamage) * (1000000 - 100 * timeElapsed);
			if(isInVisitedList) {
				remainingTime += visitedListPenalty;
			}
			hasBeenHurt = (calcMarioDamage() != initialDamage);
			snapshot = getSnapshot();
			
			return remainingTime;
		}
	}

	
	public LevelScene levelScene = null;
	private LevelScene workScene = null;
	private ArrayList<SearchNode> nodePool = null;
	
	private SearchNode bestNode;
	private SearchNode furthestNode;
	
	private float currentSearchStartingMarioXPos;
	
	private ArrayList<boolean[]> actionPlan = null;
	private ArrayList<int[]> visitedStates = new ArrayList<int[]>();
	public int timeBudget = 20;
	private static final int visitedListPenalty = 1500;
	private int ticksBeforeReplanning = 0;
	
	private final int timeLimit = 40;
	
	public Simulator() {
		levelScene = new LevelScene();
		levelScene.level = new Level(300, 15);
	}
	
	public void resetSimMario(MarioAIOptions options) {
		levelScene.mario.resetStatic(options);
	}
	
	public void setLevelPart(byte[][] levelPart, float[] enemies) {
		levelScene.setLevelScene(levelPart);
		levelScene.setEnemies(enemies);
	}
	
	public boolean canJump(SearchNode node, boolean checkParent) {
		if(checkParent && node.parent != null && canJump(node.parent, false)) {
			return true;
		}
		Mario mario = node.snapshot.mario;
		return mario.mayJump() || mario.getJumpTime() > 0;
	}
	
	private boolean[] createAction(boolean left, boolean right, boolean jump, boolean speed, boolean down) {
		boolean[] action = new boolean[Environment.numberOfKeys];
		action[Mario.KEY_LEFT] = left;
		action[Mario.KEY_RIGHT] = right;
		action[Mario.KEY_JUMP] = jump;
		action[Mario.KEY_SPEED] = speed;
		action[Mario.KEY_DOWN] = down;
		return action;
	}
	
	private ArrayList<boolean[]> extractPlan() {
		ArrayList<boolean[]> actions = new ArrayList<boolean[]>();
		if(bestNode == null) {
			// スタート直後
			for(int i = 0; i < 10; ++i) {
				actions.add(createAction(false, true, false, true, false));
			}
			return actions;
		}
		
		SearchNode currentNode = bestNode;
		while(currentNode.parent != null) {
			for(int i = 0; i < currentNode.repetition; ++i) {
				actions.add(0, currentNode.action);
			}
			currentNode = currentNode.parent;
		}
		return actions;
	}
	
	private ArrayList<boolean[]> enumerateNextActions(SearchNode node) {
		ArrayList<boolean[]> actions = new ArrayList<boolean[]>();
		// ジャンプ．優先度はこっちを高くする．
		if(canJump(node, true)) {
			actions.add(createAction(false, true, true, true, false));
			actions.add(createAction(false, true, true, false, false));
			actions.add(createAction(true, false, true, true, false));
		}
		// 左右への移動（高速と低速）．これはいつでもできる．
		actions.add(createAction(false, true, false, true, false));
		actions.add(createAction(false, true, false, false, false));
		actions.add(createAction(true, false, false, true, false));
		actions.add(createAction(true, false, false, false, false));
		return actions;
	}
	
	public float[] estimateMaximumForwardMovement(float acc, boolean[] action, int ticks) {
		float dist = 0;
		float runningSpeed = action[Mario.KEY_SPEED] ? 1.2f : 0.6f;
		int dir = 0;
		if(action[Mario.KEY_LEFT]) {
			dir = -1;
		}
		if(action[Mario.KEY_RIGHT]){
			dir = 1;
		}
		for(int i = 0; i < ticks; ++i) {
			acc += runningSpeed * dir;
			dist += acc;
			acc *= 0.89f;
		}
		float[] res = new float[2];
		res[0] = dist;
		res[1] = acc;
		return res;
	}
	
	private float maxForwardMovement(float initSpeed, int ticks) {
		float y = ticks;
		float s0 = initSpeed;
		return (float)(99.17355373 * Math.pow(0.89, y + 1)
					 - 9.090909091 * s0 * Math.pow(0.89, y + 1)
					 + 10.90909091 * y - 88.26446282 + 9.090909091 * s0);
	}
	
	private int calcMarioDamage() {
		Mario mario = levelScene.mario;
		if(levelScene.level.isGap[(int)(mario.x / 16)]
		   && mario.y > levelScene.level.gapHeight[(int)(mario.x / 16)] * 16) {
			mario.addDamage(5);
		}
		return mario.getDamage();
	}
	
	private void startSearch(int repetition) {
		SearchNode root = new SearchNode(null, null, repetition);
		root.snapshot = getSnapshot();
		nodePool = new ArrayList<SearchNode>();
		visitedStates.clear();
		nodePool.addAll(root.createNextNode());
		currentSearchStartingMarioXPos = levelScene.mario.x;
		bestNode = root;
		furthestNode = root;
	}
	
	private void search(long startTime) {
		SearchNode currentNode = bestNode;
		boolean currentGood = false;
		int ticks = 0;
		int maxRight = 176;
		
		while(nodePool.size() != 0
			  && (bestNode.snapshot.mario.x - currentSearchStartingMarioXPos < maxRight || !currentGood)
			  && (System.currentTimeMillis() - startTime < timeLimit))
		{
			ticks++;
			
			currentNode = pickBestNode(nodePool);
			currentGood = false;
			float realRemainingTime = currentNode.simulate();
			if(realRemainingTime < 0) {
				continue;
			} else {
				int marioX = (int)currentNode.snapshot.mario.x;
				int marioY = (int)currentNode.snapshot.mario.y;
				if(!currentNode.isInVisitedList
				   && isInVisited(marioX, marioY, currentNode.timeElapsed)) {
					realRemainingTime += visitedListPenalty;
					currentNode.isInVisitedList = true;
					currentNode.remainingTime = realRemainingTime;
					currentNode.remainingTimeEstimated = realRemainingTime;
					nodePool.add(currentNode);
				} else if(realRemainingTime - currentNode.remainingTimeEstimated > 0.1) {
					currentNode.remainingTimeEstimated = realRemainingTime;
					nodePool.add(currentNode);
				} else {
					currentGood = true;
					visited(marioX, marioY, currentNode.timeElapsed);
					nodePool.addAll(currentNode.createNextNode());
				}
			}
			
			if(currentGood) {
				bestNode = currentNode;
				furthestNode = currentNode;
			}
		}
		
		levelScene = currentNode.snapshot;
	}
	
	public LevelScene getSnapshot() {
		LevelScene snapshot = null;
		try {
			snapshot = (LevelScene)levelScene.clone();
		} catch(CloneNotSupportedException ex) {
			ex.printStackTrace();
		}
		return snapshot;
	}
	
	public void restoreState(LevelScene l) {
		levelScene = l;
	}
	
	public void advanceStep(boolean[] action) {
		levelScene.mario.setKeys(action);
		levelScene.tick();
	}
	
	public boolean[] optimise() {
		long startTime = System.currentTimeMillis();
		LevelScene currentState = getSnapshot();
		if(workScene == null) {
			workScene = levelScene;
		}
		int planAhead = 2;
		int stepsPerSearch = 2;
		ticksBeforeReplanning--;
		if(ticksBeforeReplanning <= 0 || actionPlan.size() == 0) {
			actionPlan = extractPlan();
			if(actionPlan.size() < planAhead) {
				planAhead = actionPlan.size();
			}
			for(int i = 0; i < planAhead; ++i) {
				advanceStep(actionPlan.get(i));
			}
			workScene = getSnapshot();
			startSearch(stepsPerSearch);
			ticksBeforeReplanning = planAhead;
		}
		restoreState(workScene);
		search(startTime);
		workScene = getSnapshot();
		
		boolean[] action = new boolean[Environment.numberOfKeys];
		if(actionPlan.size() > 0) {
			action = actionPlan.remove(0);
		}
		restoreState(currentState);
		return action;
	}
	
	private void visited(int x, int y, int t) {
		visitedStates.add(new int[] {x, y, t});
	}
	
	private boolean isInVisited(int x, int y, int t) {
		int timeDiff = 5;
		int xDiff = 2;
		int yDiff = 2;
		for(int[] v : visitedStates) {
			if(Math.abs(v[0] - x) < xDiff
			   && Math.abs(v[1] - y) < yDiff
			   && Math.abs(v[2] - t) < timeDiff
			   && t >= v[2]) {
				return true;
			}
		}
		return false;
	}
	
	private SearchNode pickBestNode(ArrayList<SearchNode> nodePool) {
		SearchNode best = null;
		float bestCost = 10000000;
		for(SearchNode n : nodePool) {
			float cost = n.getRemainingTime() + n.timeElapsed * 0.9f;
			if(cost < bestCost) {
				best = n;
				bestCost = cost;
			}
		}
		nodePool.remove(best);
		return best;
	}
}

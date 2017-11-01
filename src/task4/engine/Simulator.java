package task4.engine;

import java.util.*;

import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.tools.MarioAIOptions;
import task4.sprites.Mario;
import task4.engine.*;
import task4.level.Level;

public class Simulator {
	
	/*
	 * 探索ノード
	 */
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
		
		public boolean isInVisitedList = false;
		
		// todo: スナップショットはここで取るべきな気がする
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
				SearchNode newNode = new SearchNode(this, action, repetition);
				newNode.simulate();
				list.add(newNode);
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
			// シミュレート中の状態を現状態にセットし，コピーをとる．
			levelScene = parent.snapshot;
			parent.snapshot = getSnapshot(levelScene);
			
			int initialDamage = calcMarioDamage();
			float initX = levelScene.mario.x;
			float initY = levelScene.mario.y;
			
			for(int i = 0; i < repetition; ++i) {
				advanceStep(action);
			}
			
			int lastDamage = calcMarioDamage();
			float endX = levelScene.mario.x;
			float endY = levelScene.mario.y;
			
			remainingTime = calcRemainingTime(levelScene.mario.x, levelScene.mario.xa)
							+ (lastDamage - initialDamage) * (1000000 - 100 * timeElapsed);
			if(isInVisitedList) {
				remainingTime += visitedListPenalty;
			}
			if(lastDamage > initialDamage) {
				//System.out.println("[SearchNode.simulate]: getDamage, time: " + remainingTime);
			} else {
				//System.out.println("[SearchNode.simulate]: safe");
				//System.out.println("jump action");
			}
			// シミュレート後の状態を現状態にスナップショットを取っておく
			snapshot = getSnapshot(levelScene);

			// debug
			//System.out.println("[SearchNode Debug]");
			//snapshot.printSpritePos();
			//System.out.println("[SearchNode]: Mario Pos -- " + snapshot.mario.x + " " + snapshot.mario.y + " " + snapshot.mario.xa + " " + snapshot.mario.ya + "\n");
			
			return remainingTime;
		}
	}
	
	/*
	 * 探索ノードの比較用．コストが小さい方を優先的に取り出す．
	 */
	public class SearchNodeComparator implements Comparator<SearchNode> {
		@Override
		public int compare(SearchNode n1, SearchNode n2) {
			float cost1 = n1.getRemainingTime() + n1.timeElapsed * 0.9f;
			float cost2 = n2.getRemainingTime() + n2.timeElapsed * 0.9f;
			if(cost1 < cost2) {
				return -1;
			} else if (cost1 > cost2) {
				return 1;
			} else {
				return 0;
			}
		}
	}

	
	/*
	 * Simulator のメンバ変数
	 */
	
	// シミュレーターでの大本の Scene
	public LevelScene levelScene = null;
	private LevelScene workScene = null;
	
	private SearchNode bestNode;
	private SearchNode furthestNode;
	
	private ArrayList<boolean[]> actionPlan = null;
	private ArrayList<int[]> visitedStates = new ArrayList<int[]>();
	public int timeBudget = 20;
	private static final int visitedListPenalty = 100;
	
	private final int timeLimit = 40;
	
	
	public Simulator() {
		levelScene = new LevelScene();
		levelScene.level = new Level(400, 15);
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
	
	private ArrayList<boolean[]> extractPlan(SearchNode state) {
		ArrayList<boolean[]> actions = new ArrayList<boolean[]>();
		if(state == null) {
			// スタート直後
			for(int i = 0; i < 5; ++i) {
				actions.add(createAction(false, true, false, true, false));
			}
			return actions;
		}
		
		SearchNode currentNode = state;
		// O(n^2) だけど n が小さいと仮定して add(0, action) でもいいのかなあ．
		while(currentNode.parent != null) {
			for(int i = 0; i < currentNode.repetition; ++i) {
				actions.add(currentNode.action);
			}
			currentNode = currentNode.parent;
		}
		Collections.reverse(actions);
		return actions;
	}
	
	private ArrayList<boolean[]> enumerateNextActions(SearchNode node) {
		ArrayList<boolean[]> actions = new ArrayList<boolean[]>();
		boolean jump_ok = canJump(node, true);
		if(jump_ok) {
			// 左右なしジャンプ．
			actions.add(createAction(false, false, true, true, false));
			actions.add(createAction(false, false, true, false, false));
		}
		// 左右への移動（高速と低速）．これはいつでもできる．
		actions.add(createAction(false, true, false, false, false));
		actions.add(createAction(false, true, false, true, false));
		if(jump_ok) {
			actions.add(createAction(false, true, true, true, false));
		}
		actions.add(createAction(true, false, false, false, false));
		actions.add(createAction(true, false, false, true, false));
		if(jump_ok) {
			actions.add(createAction(true, false, true, true, false));
		}
		
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
		if(mario.x < 0) {
			return 10;
		}
		if(levelScene.level.isGap[(int)(mario.x / 16)]
		   && mario.y > levelScene.level.gapHeight[(int)(mario.x / 16)] * 16) {
			mario.addDamage(5);
		}
		return mario.getDamage();
	}
	
	// 探索開始ノードを現状態にセットし，探索の準備を整える．
	private void startSearch(int repetition) {
		SearchNode root = new SearchNode(null, null, repetition);
		root.snapshot = getSnapshot(levelScene);
		visitedStates.clear();
		bestNode = root;
		furthestNode = root;
	}
	
	// あるシーンの状態のコピーを取得する．
	public LevelScene getSnapshot(LevelScene l) {
		LevelScene snapshot = null;
		try {
			snapshot = (LevelScene)l.clone();
		} catch(CloneNotSupportedException ex) {
			ex.printStackTrace();
		}
		return snapshot;
	}
	
	public void restoreState(LevelScene l) {
		levelScene = l;
	}
	
	// 現状態を1ステップ進める．
	public void advanceStep(boolean[] action) {
		levelScene.mario.setKeys(action);
		levelScene.tick();
	}
	
	public boolean[] optimise() {
		LevelScene currentScene = getSnapshot(levelScene);
		if(workScene == null) {
			workScene = levelScene;
		}
		
		long startTime = System.currentTimeMillis();
		
		startSearch(1);
		
		int beamWidth = 20;
		int maxDepth = 100;
		Comparator<SearchNode> comparator = new SearchNodeComparator();
		PriorityQueue<SearchNode> currentStates = new PriorityQueue<SearchNode>(beamWidth, comparator);
		currentStates.add(bestNode);
		for(int i = 0; i < maxDepth && currentStates.size() > 0; ++i) {
			if(System.currentTimeMillis() - startTime > 10) {
				beamWidth = 10;
			}
			if(System.currentTimeMillis() - startTime > 20) {
				break;
			}
			PriorityQueue<SearchNode> nextStates = new PriorityQueue<SearchNode>(beamWidth, comparator);
			for(int j = 0; j < beamWidth; ++j) {
				if(currentStates.isEmpty()) {
					break;
				}
				SearchNode now = currentStates.poll();
				//if(isInVisited((int)now.snapshot.mario.x, (int)now.snapshot.mario.y, now.timeElapsed)) {
				//	now.isInVisitedList = true;
				//	continue;
				//}
				nextStates.addAll(now.createNextNode());
				//visited((int)now.snapshot.mario.x, (int)now.snapshot.mario.y, now.timeElapsed);
			}
			currentStates = nextStates;
			//System.out.println("optimise, step " + i + "  -> state size: " + currentStates.size());
		}
		if(currentStates.isEmpty()) {
			System.out.println("[Simulator.optimise] Warning!! Empty queue!!");
		}
		bestNode = currentStates.poll();
		ArrayList<boolean[]> actions = extractPlan(bestNode);
		restoreState(currentScene);
		//System.out.println("depth: " + i + "  final pos: " + levelScene.mario.x + " " + levelScene.mario.y);
		return actions.get(0);
	}
	
	private void visited(int x, int y, int t) {
		visitedStates.add(new int[] {x, y, t});
	}
	
	private boolean isInVisited(int x, int y, int t) {
		final int timeDiff = 2;
		final int xDiff = 2;
		final int yDiff = 2;
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
	
	// for debug
	public void printScene() {
		System.out.println("[Simulator Debug]");
		levelScene.printSpritePos();
		System.out.println("Mario.pos = (" + levelScene.mario.x + ", " + levelScene.mario.y + ")");
	}

}

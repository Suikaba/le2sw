package task4.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;

import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.tools.MarioAIOptions;
import task4.level.Level;
import task4.sprites.Mario;

public class Simulator {

	private static int BUDGET_SIZE = 16;
	// 同じ場所に居続けるのは不利
	private static int STAY_LIMIT = 200;

	/**
	 * 探索過程の各状態を表すノード
	 */
	private class SearchNode {
		// 前状態
		private SearchNode parent = null;
		// 現状態の各状況のスナップショット
		private LevelScene snapshot = null;

		// 現状態での行動
		private boolean[] action;
		// 繰り返し回数
		private int repetition;

		// 探索開始から何ステップ目のノードか
		private int timeElapsed = 0;

		// 現状態のコスト．値が小さいほうが良い．
		private float cost;

		// 探索済みリストにいるか
		private boolean isInVisitedList = false;

		// todo: スナップショットはここで取るべきな気がする
		public SearchNode(SearchNode parent, boolean[] action, int repetition) {
			this.parent = parent;
			this.action = action;
			this.repetition = repetition;

			if(this.parent != null) {
				timeElapsed = parent.timeElapsed + repetition;
			} else {
				timeElapsed = 0;
			}
			this.action = action;
			this.repetition = repetition;
			this.cost = 0;
		}

		/**
		 *  現状態から遷移できる次のノードのリストを返す
		 * @return 遷移先ノード
		 */
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

		/**
		 * @return 現在のノードのコスト
		 */
		public float getCost() {
			return cost;
		}

		/**
		 *  現状態のノードを1ステップシミュレートする
		 * @return シミュレートした結果のコスト．getCost と同じ値．
		 */
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
			int xInMap = (int)(endX / BUDGET_SIZE);
			int yInMap = (int)(endY / BUDGET_SIZE);

			boolean onGap = true;
			// とりあえず敵を無視して穴かどうかだけ判定
			for(int h = 19; h >= 0; --h) {
				if(levelScene.level.getBlock(xInMap, h) != 0 && levelScene.level.getBlock(xInMap, h) != 2) {
					onGap = false;
				}
			}

			// コスト計算部分
			cost = 0;
			if(yInMap >= 15 || onGap && yInMap >= 10 && levelScene.mario.ya > 0) {
				System.out.println("onGap: x == " + xInMap + " y == " + yInMap);
				cost = 1e8f;
			} else {
				if(yInMap < 0) {
					yInMap = 0;
				}
				if(mapScore[xInMap][yInMap] >= STAY_LIMIT) {
					//System.out.println("Too stay");
					cost += 1e4;
				}
				if(tooStayX != -1 && tooStayCount - timeElapsed > 0) {
					if(tooStayY <= 7) {
						cost += 1e4 * (tooStayY - yInMap);
					} else {
						cost += 1e4 * (yInMap - tooStayY);
					}
				}
			}
			if(tooStayCount == 0) {
				cost += (initX - endX) * 10;
			} else {
				cost += (initX - endX) + (endX - tooStayX * 16) * 3;
			}
			cost += endY + (lastDamage - initialDamage) * 1e6;
			cost += getMapScore(xInMap, yInMap);
			if(lastDamage - initialDamage > 0) {
				System.out.println("Damaged");
			}

			if(isInVisitedList) {
				cost += visitedListPenalty;
			}

			// シミュレート後の状態を現状態にスナップショットを取っておく
			snapshot = getSnapshot(levelScene);

			// debug
			//System.out.println("[SearchNode Debug]");
			//snapshot.printSpritePos();
			//System.out.println("[SearchNode]: Mario Pos -- " + snapshot.mario.x + " " + snapshot.mario.y + " " + snapshot.mario.xa + " " + snapshot.mario.ya + "\n");

			return cost;
		}
	}

	/**
	 * 探索ノードの比較用．コストが小さい方を優先的に取り出す．
	 */
	public class SearchNodeComparator implements Comparator<SearchNode> {
		@Override
		public int compare(SearchNode n1, SearchNode n2) {
			final float cost1 = n1.getCost();
			final float cost2 = n2.getCost();
			if(cost1 < cost2) {
				return -1;
			} else if (cost1 > cost2) {
				return 1;
			} else {
				return 0;
			}
		}
	}


		// シミュレーターでの大本の Scene
	public LevelScene levelScene = null;
	private LevelScene workScene = null;

	private SearchNode bestNode;

	public int timeBudget = 20;
	private static final int visitedListPenalty = 100;

	// マップの各位置の重み．探索の過程で使う．
	private float[][] mapScore;

	// 前回計算したときの行動プラン
	private ArrayList<boolean[]> prevPlan = new ArrayList<boolean[]>();

	// ある地点を探索し終えたか
	private boolean[][] visitedStates;

	// 同じ場所にとどまり続けていないか
	private int tooStayCount = 0;
	private int tooStayX = -1, tooStayY = -1;


	public Simulator() {
		levelScene = new LevelScene();
		levelScene.level = new Level(400, 15);
		mapScore = new float[400][40];
		visitedStates = new boolean[400][40];
	}

	/**
	 * シミュレータのマリオの初期状態を，与えられたタスクのオプションを元に設定する
	 * @param options タスクの設定
	 */
	public void resetSimMario(MarioAIOptions options) {
		levelScene.mario.resetStatic(options);
	}

	/**
	 * シミュレータの状態を，与えられた引数を元に再設定する
	 * @param levelPart マップのオブジェクト（ブロックなど）の状態
	 * @param enemies 敵の配置
	 */
	public void setLevelPart(byte[][] levelPart, float[] enemies) {
		levelScene.setLevelScene(levelPart);
		levelScene.setEnemies(enemies);
	}

	/**
	 * 与えられた探索ノードにおいて，ジャンプ（あるいはハイジャンプ）が可能であるかを判定する
	 * @param node 探索ノード
	 * @param checkParent 親の状態を加味するか
	 * @return ジャンプ（ハイジャンプ）が可能か
	 */
	public boolean canJump(SearchNode node, boolean checkParent) {
		if(checkParent && node.parent != null && canJump(node.parent, false)) {
			return true;
		}
		Mario mario = node.snapshot.mario;
		return mario.mayJump() || mario.getJumpTime() > 0;
	}

	/**
	 * 行動状態を作るヘルパー関数
	 *
	 * @param left 左ボタンをおすか
	 * @param right 右ボタンをおすか
	 * @param jump ジャンプボタンをおすか
	 * @param speed Bボタンをおすか
	 * @param down 下ボタンをおすか
	 * @return 行動状態
	 */
	private boolean[] createAction(boolean left, boolean right, boolean jump, boolean speed, boolean down) {
		boolean[] action = new boolean[Environment.numberOfKeys];
		action[Mario.KEY_LEFT] = left;
		action[Mario.KEY_RIGHT] = right;
		action[Mario.KEY_JUMP] = jump;
		action[Mario.KEY_SPEED] = speed;
		action[Mario.KEY_DOWN] = down;
		return action;
	}

	/**
	 * 与えられたノードから，現状態までの行動リストを復元する
	 *
	 * @param state 探索ノード
	 * @return 復元された各ステップでの行動のリスト
	 */
	private ArrayList<boolean[]> extractPlan(SearchNode state) {
		ArrayList<boolean[]> actions = new ArrayList<boolean[]>();
		if(state == null) {
			// (主に)スタート直後
			actions.add(createAction(false, true, false, false, false));
			return actions;
		}

		SearchNode currentNode = state;
		actions.add(currentNode.action);
		while(currentNode.parent != null) {
			for(int i = 0; i < currentNode.repetition; ++i) {
				actions.add(currentNode.action);
			}
			currentNode = currentNode.parent;
		}
		Collections.reverse(actions);
		return actions;
	}

	/**
	 * 与えられた探索ノードにおいて，選択できるすべてのアクションを列挙する
	 * @param node 探索ノード
	 * @return 可能なアクションのリスト
	 */
	private ArrayList<boolean[]> enumerateNextActions(SearchNode node) {
		ArrayList<boolean[]> actions = new ArrayList<boolean[]>();
		boolean jump_ok = canJump(node, true);
		// 左右キー無しは右左を細かくやれば実現できるので書かない

		int xInMap = (int)(node.snapshot.mario.x / BUDGET_SIZE);
		if(tooStayX == -1 || tooStayCount - node.timeElapsed <= 0 && xInMap < tooStayX - 4) {
			if(jump_ok) {
				actions.add(createAction(false, true, true, true, false));
			}
			actions.add(createAction(false, true, false, false, false));
			actions.add(createAction(false, true, false, true, false));
		}
		if(jump_ok) {
			actions.add(createAction(true, false, true, true, false));
		}
		actions.add(createAction(true, false, false, false, false));
		actions.add(createAction(true, false, false, true, false));


		return actions;
	}

	/**
	 * @return 現状態におけるマリオのダメージ量
	 */
	private int calcMarioDamage() {
		Mario mario = levelScene.mario;
		return mario.getDamage();
	}

	/**
	 * @return ある地点における，到達回数による重み
	 */
	private float getMapScore(int x, int y) {
		if(y < 0) {
			y = 0;
		}
		if(y >= 20) {
			y = 19;
		}
		return mapScore[x][y];
	}

	/**
	 *  探索開始ノードを現状態にセットし，探索の準備を整える．
	 */
	private void startSearch() {
		SearchNode root = new SearchNode(null, null, 1);
		root.snapshot = getSnapshot(levelScene);
		bestNode = root;
	}

	/**
	 *  あるシーンの状態のコピーを取得する．
	 *
	 * @param l コピーしたいシーン
	 * @return 引数に与えたシーンのコピー
	 */
	public LevelScene getSnapshot(LevelScene l) {
		LevelScene snapshot = null;
		try {
			snapshot = (LevelScene)l.clone();
		} catch(CloneNotSupportedException ex) {
			ex.printStackTrace();
		}
		return snapshot;
	}

	/**
	 * 大本のシーンを引数のシーンに戻す
	 * @param l 戻す先のシーン
	 */
	public void restoreState(LevelScene l) {
		levelScene = l;
	}

	/**
	 *  現状態を1ステップ進める．
	 *
	 * @param action 現状態でどのような行動をとるか
	 */
	public void advanceStep(boolean[] action) {
		levelScene.mario.setKeys(action);
		levelScene.tick();
	}

	/**
	 * 現状態における最良の行動を，未来の状態をシミュレートすることで推定する
	 *
	 * @param restTime 探索に使える残り時間 (ms)
	 * @return 最良の行動
	 */
	public boolean[] optimise(long restTime) {
		LevelScene currentScene = getSnapshot(levelScene);
		if(workScene == null) {
			workScene = levelScene;
		}

		long startTime = System.currentTimeMillis();

		startSearch();

		// todo: chokudai search を試してみる
		int beamWidth = 15;
		final int maxDepth = 15;
		Comparator<SearchNode> comparator = new SearchNodeComparator();
		PriorityQueue<SearchNode> currentStates = new PriorityQueue<SearchNode>(1, comparator);
		ArrayList<PriorityQueue<SearchNode>> prevStates = new ArrayList<PriorityQueue<SearchNode>>();
		currentStates.add(bestNode);
		for(int i = 0; i < maxDepth; ++i) {
			if(restTime - 15 < System.currentTimeMillis() - startTime) {
				break;
			}
			PriorityQueue<SearchNode> nextStates = new PriorityQueue<SearchNode>(8 * beamWidth, comparator);
			for(int j = 0; j < beamWidth; ++j) {
				if(currentStates.isEmpty()) {
					break;
				}
				SearchNode now = currentStates.poll();
				if(now.snapshot.mario.isDead()) {
					continue;
				}
				nextStates.addAll(now.createNextNode());
			}
			if(!nextStates.isEmpty()) {
				if(!currentStates.isEmpty()) {
					prevStates.add(currentStates);
				}
				currentStates = nextStates;
			} else {
				if(currentStates.isEmpty()) {
					if(!prevStates.isEmpty()) {
						currentStates = prevStates.remove(prevStates.size() - 1);
					}
				}
			}
		}

		int currentX = (int)(levelScene.mario.x / BUDGET_SIZE);
		int currentY = (int)(levelScene.mario.y / BUDGET_SIZE);
		for(int dx = 0; dx <= 0; ++dx) {
			for(int dy = 0; dy <= 0; ++dy) {
				int x = currentX + dx, y = currentY + dy;
				if(x < 0 || x >= 400 || y < 0 || y >= 20) {
					continue;
				}
				mapScore[x][y] += Math.max(0, 3 - Math.abs(currentX - x) + Math.abs(currentY - y));
			}
		}
		if(currentStates.isEmpty()) {
			System.out.println("[Simulator.optimise] Warning!! Empty queue!!");
		}

		bestNode = currentStates.poll();
		if(bestNode == null && prevPlan.size() > 0) {
			restoreState(currentScene);
			return prevPlan.remove(0);
		}
		restoreState(currentScene);

		float marioXf = levelScene.mario.x;
		float marioYf = levelScene.mario.y;
		int marioX = (int)(levelScene.mario.x / 16);
		int marioY = (int)(levelScene.mario.y / 16);
		System.out.println();
		System.out.println("[Simulator.optimise] final pos: (" + marioXf + ", " + marioYf + ") -> (" + marioX + ", " + marioY + ")");
		System.out.println("[Simulator.optimise] bestNode: " + bestNode.snapshot.mario.x + " " + bestNode.snapshot.mario.y + " " + bestNode.getCost() + "\n");
		prevPlan = extractPlan(bestNode);

		if(tooStayX == -1 && marioY >= 0 && mapScore[marioX][marioY] >= STAY_LIMIT) {
			tooStayCount = 50;
			tooStayX = marioX;
			tooStayY = marioY;
			System.out.println("too Stay pos: " + tooStayX + " " + tooStayY);
		} else {
			tooStayCount -= 1;
			if(tooStayCount < 0) {
				tooStayCount = 0;
			}
		}
		// スタック状態から抜け出せた
		if(tooStayX != -1 && tooStayX + 5 < marioX) {
			tooStayX = tooStayY = -1;
			System.out.println("Get out stuck");
		}
		return prevPlan.remove(0);
	}

	// for debug
	public void setDebugLevel(int debugLevel) {
	//	levelScene.DEBUG_LEVEL = debugLevel;
	}
	public void printScene() {
		System.out.println("[Simulator Debug]");
		levelScene.printSpritePos();
		System.out.println("Mario.pos = (" + levelScene.mario.x + ", " + levelScene.mario.y + ")");
	}

}

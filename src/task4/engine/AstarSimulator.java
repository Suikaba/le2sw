package task4.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;

import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.tools.MarioAIOptions;
import task4.level.Level;
import task4.sprites.Mario;

public class AstarSimulator {

	private static int BUDGET_SIZE = 16;
	// 同じ場所に居続けるのは不利
	private static int STAY_LIMIT = 100;
	private static int MAP_LENGTH = 400;
	private static int MAP_HEIGHT = 20;

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

		// 現状態のスコア．値が大きい方が良い．
		private float score;

		// todo: スナップショットはここで取るべきな気がする
		public SearchNode(SearchNode parent, boolean[] action, int repetition) {
			this.parent = parent;
			this.action = action;
			this.repetition = repetition;

			if(this.parent != null) {
				this.timeElapsed = parent.timeElapsed + repetition;
			} else {
				this.timeElapsed = 0;
			}
			this.action = action;
			this.repetition = repetition;
			this.score = 0;
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
		 * @return 現在のノードのスコア
		 * @note 高いほうが良い
		 */
		public float getScore() {
			return score;
		}

		/**
		 *  現状態のノードを1ステップシミュレートする
		 * @return シミュレートした結果のコスト．getCost と同じ値．
		 */
		public float simulate() {
			// シミュレート中の状態を現状態にセットし，コピーをとる．
			levelScene = parent.snapshot;
			parent.snapshot = getSnapshot(levelScene);

			for(int i = 0; i < repetition; ++i) {
				advanceStep(action);
			}

			int lastDamage = calcMarioDamage();
			float endX = levelScene.mario.x;
			float endY = levelScene.mario.y;
			int xInMap = (int)(endX / BUDGET_SIZE);
			int yInMap = (int)(endY / BUDGET_SIZE);

			int gapHeight = -1;
			int startMarioXInMap = (int)(startMarioX / 16);
			// とりあえず敵を無視して穴かどうかだけ判定
			// 離れすぎていると，マップデータがまだ無いので穴扱いになってしまう．
			if(Math.abs(startMarioXInMap - xInMap) < 10) {
				for(int h = 0; h <= 19; ++h) {
					if(levelScene.level.getBlock(xInMap, h) != 0 && levelScene.level.getBlock(xInMap, h) != 2) {
						gapHeight = h;
					}
				}
			}

			// コスト計算部分
			score = 0;
			if(yInMap >= 15 || gapHeight == -1 && levelScene.mario.ya > 0 && yInMap >= 10) {
				//System.out.println("onGap: (" + endX + " ," + endY + "),  start x: " + startMarioX);
				score = -1e10f;
			} else {
				if(yInMap < 0) {
					yInMap = 0;
				}
				if(yInMap >= 0 && yInMap < MAP_HEIGHT && mapScore[xInMap][yInMap] >= STAY_LIMIT) {
					//System.out.println("Too stay");
					score -= 1e4;
				}
			}
			// ここがあまりにも雑すぎる
			if(tooStayCount == 0) {
				score += (endX - startMarioX) * 10;
			} else {
				score += (tooStayX * 16 - endX) * 1e2;
			}
			score -= endY + (lastDamage - startDamage) * 1e8;
			/*if(lastDamage - startDamage > 0) {
				System.out.println("Damaged");
			}*/
			//score -= getMapScore(xInMap, yInMap);
			if(isVisited(xInMap, yInMap, timeElapsed)) {
				score -= visitedListPenalty;
			}

			// シミュレート後の状態を現状態にスナップショットを取っておく
			snapshot = getSnapshot(levelScene);

			// debug
			//System.out.println("[SearchNode Debug]");
			//snapshot.printSpritePos();
			//System.out.println("[SearchNode]: Mario Pos -- " + snapshot.mario.x + " " + snapshot.mario.y + " " + snapshot.mario.xa + " " + snapshot.mario.ya + "\n");

			return score;
		}
	}

	/**
	 * 探索ノードの比較用．コストが大きい方を優先的に取り出す．
	 */
	public class SearchNodeComparator implements Comparator<SearchNode> {
		@Override
		public int compare(SearchNode n1, SearchNode n2) {
			final float score1 = n1.getScore();
			final float score2 = n2.getScore();
			if(score1 < score2) {
				return 1;
			} else if (score1 > score2) {
				return -1;
			} else {
				return 0;
			}
		}
	}


		// シミュレーターでの大本の Scene
	public LevelScene levelScene = null;
	private LevelScene workScene = null;

	public int timeBudget = 20;
	private static final int visitedListPenalty = 1500;

	// マップの各位置の重み．探索の過程で使う．
	private float[][] mapScore;

	// 前回計算したときの行動プラン
	private ArrayList<boolean[]> prevPlan = new ArrayList<boolean[]>();

	// 探索したところはメモする
	private HashSet<Integer> visitedStates = new HashSet<Integer>();

	// 同じ場所にとどまり続けていないか
	private int tooStayCount = 0;
	private int tooStayX = -1, tooStayY = -1;

	// 探索開始時のマリオのX座標
	float startMarioX = 0;
	// 探索開始時のマリオのダメージ量
	int startDamage = 0;


	public AstarSimulator() {
		levelScene = new LevelScene();
		levelScene.level = new Level(400, 15);
		mapScore = new float[MAP_LENGTH][MAP_HEIGHT];
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
		if(state == null) { // これいらないか
			actions.add(createAction(false, true, false, false, false));
			return actions;
		}

		SearchNode currentNode = state;
		while(currentNode.parent != null) {
			for(int i = 0; i < currentNode.repetition; ++i) {
				actions.add(currentNode.action);
			}
			currentNode = currentNode.parent;
		}
		Collections.reverse(actions);
		if(actions.size() == 0) {
			actions.add(createAction(false, false, false, false, false));
		}
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

		if(jump_ok) {
			actions.add(createAction(false, true, true, true, false));
		}
		actions.add(createAction(false, true, false, false, false));
		actions.add(createAction(false, true, false, true, false));
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
	private SearchNode startSearch() {
		SearchNode root = new SearchNode(null, null, 1);
		root.snapshot = getSnapshot(levelScene);
		startMarioX = levelScene.mario.x;
		startDamage = levelScene.mario.getDamage();
		visitedStates.clear();
		return root;
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

		SearchNode rootNode = startSearch();

		Comparator<SearchNode> comparator = new SearchNodeComparator();
		PriorityQueue<SearchNode> openNode = new PriorityQueue<SearchNode>(1, comparator);
		openNode.add(rootNode);
		final int maxDepth = 300;
		int count = 0;
		System.out.println("RestTime: " + restTime);
		while(!openNode.isEmpty() && count < maxDepth) {
			if(System.currentTimeMillis() - startTime > restTime - 20) {
				break;
			}
			count++;
			SearchNode currentBest = openNode.poll();
			openNode.addAll(currentBest.createNextNode());
			int curX = (int)(currentBest.snapshot.mario.x / 16);
			int curY = (int)(currentBest.snapshot.mario.y / 16);
			int t = currentBest.timeElapsed;
			visited(curX, curY, t);
		}

		restoreState(currentScene);

		// とどまりすぎていないかチェック
		float marioXf = levelScene.mario.x;
		float marioYf = levelScene.mario.y;
		int marioX = (int)(marioXf / 16);
		int marioY = (int)(marioYf / 16);
		for(int dx = -1; dx <= 1; ++dx) {
			for(int dy = -1; dy <= 1; ++dy) {
				int x = marioX + dx, y = marioY + dy;
				if(x < 0 || x >= MAP_LENGTH || y < 0 || y >= MAP_HEIGHT) {
					continue;
				}
				mapScore[x][y] += Math.max(0, 3 - Math.abs(marioX - x) - Math.abs(marioY - y));
			}
		}
		if(marioY >= 0 && mapScore[marioX][marioY] >= STAY_LIMIT) {
			tooStayCount = 30;
			tooStayX = marioX;
			tooStayY = marioY;
			//System.out.println("too Stay pos: " + tooStayX + " " + tooStayY);
		} else {
			tooStayCount--;
			if(tooStayCount < 0) {
				tooStayCount = 0;
			}
		}
		if(tooStayX != -1 && tooStayX + 5 < marioX) {
			tooStayX = tooStayY = -1;
		}

		SearchNode best = openNode.poll();
		ArrayList<boolean[]> actions = extractPlan(best);
		int damage = best.snapshot.mario.getDamage() - levelScene.mario.getDamage();
		System.out.println("[Simulator.optimise]: cur -> " + marioXf + " " + marioYf);
		System.out.println("[Simulator.optimise]: best -> " + best.snapshot.mario.x + " " + best.snapshot.mario.y + " " + damage);
		boolean[] action = actions.remove(0);
		return action;
		//return actions.get(0);
	}

	/**
	 * 探索済みフラグを立てる
	 * @param x x座標（ブロック
	 * @param y y座標
	 * @param t 時刻（ステップ数）
	 */
	private void visited(int x, int y, int t) {
		visitedStates.add(calcHash(x, y, t));
	}

	/**
	 * 探索したか
	 */
	private boolean isVisited(int x, int y, int t) {
		return visitedStates.contains((int)calcHash(x, y, t));
	}
	private int calcHash(int x, int y, int t) {
		final long base = 29;
		final long M = 1000000007;
		long hs = (x * base * base + y * base + t) % M;
		return (int)hs;
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

package task4.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;

import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.tools.MarioAIOptions;
import task4.level.Level;
import task4.sprites.EnemyInfo;
import task4.sprites.Mario;
import task4.sprites.SpriteInfo;

public class AstarSimulator {

	private static final int MAP_LENGTH = 400;
	private static final int MAP_HEIGHT = 20;

	private static final int TOO_STAY_COUNT = 50;
	private static final float TOO_VISITED_PENALTY = 50.0f;

	private static final int VISITED_BLOCK_SIZE = 8;

	/**
	 * 探索過程の各状態を表すノード
	 */
	private class SearchNode {
		// 前状態
		private SearchNode parent = null;
		// 現状態のスナップショット
		private LevelScene snapshot = null;

		// 現状態での行動
		private boolean[] action;

		// 探索開始から何ステップ目のノードか
		private int timeElapsed = 0;

		// 現状態のスコア．値が大きい方が良い．
		private float cost = 0;

		private boolean hasPenalty = false;

		// todo: スナップショットはここで取るべきな気がする
		public SearchNode(SearchNode parent, boolean[] action) {
			this.parent = parent;
			this.action = action;

			if(this.parent != null) {
				this.timeElapsed = parent.timeElapsed + 1;
			} else {
				this.timeElapsed = 0;
			}
			this.action = action;
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
				SearchNode newNode = new SearchNode(this, action);
				newNode.simulate();
				list.add(newNode);
			}
			return list;
		}

		/**
		 *  現状態のノードを1ステップシミュレートする
		 * @return シミュレートした結果のコスト．getCost と同じ値．
		 */
		public void simulate() {
			// シミュレート中の状態を前状態にセットし，コピーをとる．
			LevelScene nowScene = getSnapshot(parent.snapshot);
			this.snapshot = nowScene;

			for(int i = 0; i < 2; ++i) { // 1フレームごとに探索はやりすぎなので，2フレームまとめる．
				advanceStep(nowScene, this.action);
			}

			this.cost = calcCost(this);
		}
	}

	/**
	 * 探索ノードの比較用．コストが小さい方を優先的に取り出す．
	 */
	public class SearchNodeComparator implements Comparator<SearchNode> {
		@Override
		public int compare(SearchNode n1, SearchNode n2) {
			if(n1.cost < n2.cost) {
				return -1;
			} else if (n1.cost > n2.cost) {
				return 1;
			} else {
				return 0;
			}
		}
	}

	/**
	 * AstarSimulator メンバ変数
	 */
	// シミュレーターでの大本の Scene
	public LevelScene rootScene = null;

	// 探索したところはメモする
	private HashMap<Integer, Integer> visitedCount = new HashMap<Integer, Integer>();
	private HashSet<Integer> visitedList = new HashSet<Integer>();

	// 現状態における穴の状況
	private int[] gapHeight;

	private int lastUpdateOfMaxRight = 0;
	private float maxRight = 0;

	public AstarSimulator() {
		rootScene = new LevelScene();
		rootScene.level = new Level(MAP_LENGTH, MAP_HEIGHT);
		gapHeight = new int[MAP_LENGTH];
		//mapCount = new int[MAP_LENGTH][MAP_HEIGHT];
		//dist = new int[MAP_LENGTH][MAP_HEIGHT];
		for(int i = 0; i < MAP_LENGTH; ++i) {
			gapHeight[i] = -2; // 未探索フラグ
		}
	}

	/**
	 * シミュレータの各種定数を設定する
	 * @param options タスクの設定
	 */
	public void reset(MarioAIOptions options) {
		rootScene.reset(options);
	}

	/**
	 * シミュレータの状態を，与えられた引数を元にゲーム本体と同期をとる
	 * @param levelPart マップのオブジェクト（ブロックなど）の状態
	 * @param enemyInfo 敵の情報
	 * @param spriteInfo 敵以外のスプライトの情報
	 */
	public void syncWithGame(byte[][] levelPart, ArrayList<EnemyInfo> enemyInfo, ArrayList<SpriteInfo> spriteInfo) {
		rootScene.setMapData(levelPart);
		rootScene.updateSpriteInfo(enemyInfo, spriteInfo);
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
			actions.add(currentNode.action);
			currentNode = currentNode.parent;
		}
		Collections.reverse(actions);
		if(actions.size() == 0) { // これもあやしいなあ
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
		boolean jumpOK = canJump(node, true);
		// 左右キー無しは右左を細かくやれば実現できるので書かない
		// todo: じつは書いたほうが良い？

		if(jumpOK) {
			actions.add(createAction(false, true, true, true, false));
		}
		actions.add(createAction(false, true, false, false, false));
		actions.add(createAction(false, true, false, true, false));
		// down 入れないほうが安定する気がしてきた
		actions.add(createAction(false, true, false, false, true));
		actions.add(createAction(true, false, false, false, true));
		//actions.add(createAction(false, true, false, true, true));
		if(jumpOK) {
			actions.add(createAction(true, false, true, true, false));
		}
		actions.add(createAction(true, false, false, false, false));
		actions.add(createAction(true, false, false, true, false));

		return actions;
	}

	/**
	 * 以下探索ノードの評価値群
	 * それぞれの返り値が大きい方が良い
	 * damageAmount を除くすべてで 0 ~ 1 で値を返すものとする．
	 */

	/**
	 * ある探索ノードから暫定ゴール地点までの距離
	 */
	private float progressRate(SearchNode node) {
		LevelScene scene = node.snapshot;
		if(scene == null) {
			return 1;
		}
		float curXf = scene.mario.x;
		final float screenHalfWidth = 9 * 16;
		// 絶対値を取るか，右に行きすぎないようにしないと穴があっても右に無理やりいってしまうことがある．
		// 最大値を1にすることで，多少左に戻る操作が容易いようにしている
		float goalXf = rootScene.mario.x + screenHalfWidth;
		/*if(tooStay()) {
			if(curXf <= maxRight) {
				return 1;
			} else {
				goalXf = (maxRight - 32 + screenHalfWidth);
				return 0;
			}
		}*/
		return Math.min(1.0f, Math.max(Math.abs(goalXf - curXf), 0) / screenHalfWidth);
	}
	/**
	 * マリオの状態
	 * @return Fire: 0, Large: 1/3, small: 2/3, Dead: 1
	 */
	private float marioMode(SearchNode node) {
		LevelScene scene = node.snapshot;
		if(scene == null || rootScene == null) {
			return 1;
		}
		Mario mario = node.snapshot.mario;
		if(mario.getStatus() == Mario.STATUS_DEAD) {
			return 1;
		}
		return (2 - mario.getMode()) / 3.0f;
	}
	/**
	 * 穴に落ちなさそうか
	 */
	private float fallingGap(SearchNode node) {
		LevelScene scene = node.snapshot;
		if(scene == null) {
			return 1;
		}
		int curXInMap = (int)(scene.mario.x / 16);
		int curYInMap = (int)(scene.mario.y / 16);
		int gapWidth = 0;
		for(int x = curXInMap; x <= curXInMap + 9; ++x) {
			if(gapHeight[x] != -1) {
				break;
			}
			gapWidth++;
		}
		if(gapWidth >= 7) {
			if(curYInMap >= 8) { // 穴が大きくて十分高くなかったらダメ(雑なのでだめになったら再検討．
				return 1;
			}
		}
		if(curYInMap >= 15 || gapHeight[curXInMap] == -1 && curYInMap >= 10 && scene.mario.ya > 0
			|| gapHeight[curXInMap] > 7 && gapHeight[curXInMap] < curYInMap && scene.mario.ya > 0) {
			return 1;
		} else {
			return 0;
		}
	}
	/**
	 * 探索ノードを探索した回数
	 */
	private float visitedCount(SearchNode node) {
		LevelScene scene = node.snapshot;
		if(scene == null) {
			return 1;
		}
		int curX = (int)(scene.mario.x / VISITED_BLOCK_SIZE);
		int curY = (int)(scene.mario.y / VISITED_BLOCK_SIZE);
		int count = getVisitedCount(curX, curY, 0);
		if(count > 20) {
			return 10.f;
		} else {
			return count / 20.f;
		}
	}
	private float height(SearchNode node) {
		LevelScene scene = node.snapshot;
		if(scene == null) {
			return 1;
		}
		return scene.mario.y / 256.0f;
	}

	private float calcCost(SearchNode node) {
		float cost = progressRate(node) * (tooStay() ? 3.f : 50.0f)  // 進行度合いは当然大事
					+ marioMode(node) * 50000.f       // ダメージは重い
					+ fallingGap(node) * 100000.f  // 穴に落ちていれば何よりもまずい
					+ visitedCount(node) * (tooStay() ? 500.f : 0f)
					+ node.timeElapsed * 0.01f
					+ height(node) * (tooStay() ? 4.f : 55.f);
		return cost;
	}

	/**
	 * 現状態で見える範囲の穴の状況を求める．
	 */
	private void searchGap() {
		if(rootScene == null) {
			return;
		}
		int marioX = rootScene.mario.mapX;
		for(int x = marioX - 9; x <= marioX + 9; ++x) {
			if(x < 0 || MAP_LENGTH <= x) {
				continue;
			}
			for(int y = 0; y < MAP_HEIGHT; ++y) {
				if(rootScene.level.getBlock(x, y) != 0 && rootScene.level.getBlock(x, y) != 2) {
					gapHeight[x] = y;
				}
			}
			if(gapHeight[x] == -2) {
				gapHeight[x] = -1; // 穴
			}
		}
	}

	/**
	 * 長時間進行しない，スタック状態にあるか．
	 */
	private boolean tooStay() {
		return lastUpdateOfMaxRight >= TOO_STAY_COUNT;
	}

	/**
	 *  探索開始ノードを現状態にセットし，探索の準備を整える．
	 */
	private SearchNode prepareSearch() {
		SearchNode root = new SearchNode(null, createAction(false, false, false, false, false));
		root.snapshot = getSnapshot(rootScene);
		searchGap();
		//visitedCount.clear();
		visitedList.clear();
		return root;
	}

	/**
	 *  あるシーンの状態のコピーを取得する．
	 *
	 * @param scene コピーしたいシーン
	 * @return 引数に与えたシーンのコピー
	 */
	private LevelScene getSnapshot(LevelScene scene) {
		if(scene == null) { // todo: 例外とか投げるべき？
			return null;
		}
		LevelScene snapshot = null;
		try {
			snapshot = (LevelScene)scene.clone();
		} catch(CloneNotSupportedException ex) {
			ex.printStackTrace();
		}
		return snapshot;
	}

	/**
	 *  指定されたシーンを，与えられた入力により1ステップ進める．
	 *
     * @param scene 進めるシーン
	 * @param action 現状態でどのような行動をとるか
	 */
	public void advanceStep(LevelScene scene, boolean[] action) {
		scene.mario.setKeys(action);
		scene.tick();
	}

	/**
	 * 現状態における最良の行動を，未来の状態をシミュレートすることで推定する
	 *
	 * @param restTime 探索に使える残り時間 (ms)
	 * @return 最良の行動
	 */
	public boolean[] optimise(long restTime) {
		long startTime = System.currentTimeMillis();

		SearchNode rootNode = prepareSearch();
		PriorityQueue<SearchNode> openNode = new PriorityQueue<SearchNode>(1, new SearchNodeComparator());
		openNode.add(rootNode);
		final int maxDepth = 10000;
		for(int i = 0; i < maxDepth && !openNode.isEmpty(); ++i) {
			if(System.currentTimeMillis() - startTime > restTime - 5) { // -5 は余裕を持たせている．
				break;
			}
			SearchNode openBest = openNode.poll();
			if(maxRight + 9.5f * 16 - rootScene.mario.x <= 0) { // 右端に到達したら打ち切る
				openNode.add(openBest);
				break;
			}
			int x = (int)(openBest.snapshot.mario.x / VISITED_BLOCK_SIZE);
			int y = (int)(openBest.snapshot.mario.y / VISITED_BLOCK_SIZE);
			int t = openBest.timeElapsed;
			if(isVisited(x, y, t) && !openBest.hasPenalty) { // ペナルティつけられてもなお良いなら，選択する
				openBest.cost += TOO_VISITED_PENALTY;
				openBest.hasPenalty = true;
				openNode.add(openBest);
			} else {
				openNode.addAll(openBest.createNextNode());
			}
			addVisitedList(x, y, t);
		}

		PriorityQueue<SearchNode> closedNodes = openNode; // これちょっと変なんだけど，こうしたほうが結果的に良かった
		SearchNode best = closedNodes.poll();
		ArrayList<boolean[]> actions = extractPlan(best);
		boolean[] action = actions.get(0);

		float marioXf = rootScene.mario.x;
		float marioYf = rootScene.mario.y;
		if(maxRight < marioXf) {
			lastUpdateOfMaxRight = 0;
			maxRight = marioXf;
			visitedCount.clear();
		} else {
			lastUpdateOfMaxRight++;
		}

		// スタック状態なら現在位置を visitedCount に重み付ける
		if(tooStay()) {
			// t はあえて入れていない（入れないほうが良かったため
			visited((int)marioXf / VISITED_BLOCK_SIZE, (int)marioYf / VISITED_BLOCK_SIZE, 0);
		}

		return action;
	}

	/**
	 * 探索済みフラグを立てる
	 * @param x x座標（ブロック
	 * @param y y座標
	 * @param t 時刻（ステップ数）
	 */
	private void visited(int x, int y, int t) {
		final int hs = calcHash(x, y, t);
		int cnt = 0;
		if(visitedCount.containsKey(hs)) {
			cnt = visitedCount.get(hs);
		}
		visitedCount.put(hs, cnt + 1);
	}
	private void addVisitedList(int x, int y, int t) {
		final int hs = calcHash(x, y, t);
		visitedList.add(hs);
	}

	private int getVisitedCount(int x, int y, int t) {
		final int hs = calcHash(x, y, t);
		if(visitedCount.containsKey(hs)) {
			return visitedCount.get(hs);
		} else {
			return 0;
		}
	}
	private boolean isVisited(int x, int y, int t) {
		boolean result = false;
		final int dt = (tooStay() ? 1 : 0);
		for(int i = t - dt; i <= t + dt; ++i) {
			result |= visitedList.contains(calcHash(x, y, i));
		}
		return result;
	}
	private int calcHash(int x, int y, int t) {
		final long base = 9973;
		final long mod = 1000000007;
		final long hs = (x * base * base + y * base + t) % mod;
		return (int)hs;
	}


	// for debug
	public void setDebugLevel(int debugLevel) {
		rootScene.setDebugLevel(debugLevel);
	}
	public void printScene() {
		System.out.println("[Simulator Debug]");
		rootScene.printSpritePos();
		System.out.println("Mario.pos = (" + rootScene.mario.x + ", " + rootScene.mario.y + ")");
	}
	private void printCost(SearchNode node) {
		float pR = progressRate(node);
		float mode = marioMode(node);
		float gap = fallingGap(node);
		float vis = visitedCount(node);
		float h = height(node);
		System.out.println("[Node]: (pRate, mode, gap, visited, h) = "
							 + pR + ", " + mode + ", " + gap + ", " + vis + ", " + h + ")");
	}
	private void printNodeInfo(SearchNode node) {
		if(node == null) {
			return;
		}
		Mario mario = node.snapshot.mario;
		float x = mario.x;
		float y = mario.y;
		int t = node.timeElapsed;
		String mode = Mario.MODES[mario.getMode()];

		System.out.println("[Node]: (x, y, t, mode) = (" + x + ", " + y + ", " + t + ", " + mode + ")");
		printCost(node);
	}

}

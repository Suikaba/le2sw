
package task4.agents;

import java.util.ArrayList;
import java.util.List;

import ch.idsia.agents.controllers.BasicMarioAIAgent;
import ch.idsia.benchmark.mario.engine.sprites.Sprite;
import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.benchmark.mario.environments.MarioEnvironment;
import ch.idsia.tools.EvaluationInfo;
import ch.idsia.tools.MarioAIOptions;
import task4.engine.AstarSimulator;
import task4.sprites.Enemy;
import task4.sprites.EnemyInfo;
import task4.sprites.Mario;
import task4.sprites.SpriteInfo;


public class AstarAgent extends BasicMarioAIAgent
{
	private boolean action[] = new boolean[Environment.numberOfKeys];
	private AstarSimulator simulator;
	private ArrayList<SpriteInfo> spriteInfo = new ArrayList<SpriteInfo>();
	private ArrayList<EnemyInfo> enemyInfo = new ArrayList<EnemyInfo>();
	private int firstStepCount = 0;

	public AstarAgent()
	{
	    super("AstarAgent");
	    reset();
	}

	public void reset()
	{
		action = new boolean[Environment.numberOfKeys];
		simulator = new AstarSimulator();
	}

	public void resetSimulator(MarioAIOptions options) {
		simulator.reset(options);
	}

	/**
	 * 環境から情報を得る．BasicMarioAIAgent より詳細にとるようにする．
	 */
	public void integrateObservation(Environment environment)
	{
		final int zLevelScene = 0;
		final int zLevelEnemies = 0;
	    levelScene = environment.getLevelSceneObservationZ(zLevelScene);
	    enemies = environment.getEnemiesObservationZ(zLevelEnemies);
	    mergedObservation = environment.getMergedObservationZZ(1, 0);

	    this.marioFloatPos = environment.getMarioFloatPos();
	    this.enemiesFloatPos = environment.getEnemiesFloatPos();
	    this.marioState = environment.getMarioState();

	    // clone sprites
	    // enemiesFloatPos などでやるのが筋だが，青クリボー取れないのでしかたなくやっている．
	    // 大本のデータに影響が出ないようにしてある
	    List<ch.idsia.benchmark.mario.engine.sprites.Sprite> engineSprites = ((MarioEnvironment)environment).getSprites();
	    spriteInfo.clear();
	    enemyInfo.clear();
	    for(ch.idsia.benchmark.mario.engine.sprites.Sprite sprite : engineSprites) {
	    	final int kind = sprite.kind;
	    	if(sprite.isDead() || kind == Sprite.KIND_SPARCLE || kind == Sprite.KIND_PARTICLE || kind == Sprite.KIND_PRINCESS) {
	    		continue;
	    	}
	    	if(Enemy.isEnemy(sprite.kind)) {
	    		ch.idsia.benchmark.mario.engine.sprites.Enemy enemy = (ch.idsia.benchmark.mario.engine.sprites.Enemy)sprite;
	    		EnemyInfo info = new EnemyInfo(enemy.x, enemy.y, enemy.xa, enemy.ya, enemy.kind, enemy.facing);
	    		enemyInfo.add(info);
	    	} else {
	    		spriteInfo.add(new SpriteInfo(sprite.x, sprite.y, sprite.xa, sprite.ya, sprite.kind));
	    	}
	    }

	    receptiveFieldWidth = environment.getReceptiveFieldWidth();
	    receptiveFieldHeight = environment.getReceptiveFieldHeight();

	    marioStatus = marioState[0];
	    marioMode = marioState[1];
	    isMarioOnGround = marioState[2] == 1;
	    isMarioAbleToJump = marioState[3] == 1;
	    isMarioAbleToShoot = marioState[4] == 1;
	    isMarioCarrying = marioState[5] == 1;
	    getKillsTotal = marioState[6];
	    getKillsByFire = marioState[7];
	    getKillsByStomp = marioState[8];
	    getKillsByShell = marioState[9];

	    EvaluationInfo evaluationInfo = environment.getEvaluationInfo();
	    int[] evaluationInfoArr = evaluationInfo.toIntArray();
	    distancePassedCells = evaluationInfoArr[0];
	    distancePassedPhys = evaluationInfoArr[1];
	    flowersDevoured = evaluationInfoArr[2];
	    mushroomsDevoured = evaluationInfoArr[9];
	    coinsGained = evaluationInfoArr[10];
	    timeLeft = evaluationInfoArr[11];
	    timeSpent = evaluationInfoArr[12];
	    hiddenBlocksFound = evaluationInfoArr[13];
	}

	public boolean[] getAction()
	{
		/*{
			System.out.println("==================================================================================");
		}*/

		// for debug
		//simulator.setDebugLevel(LevelScene.DEBUG_ENEMY | LevelScene.DEBUG_MAP | LevelScene.DEBUG_BLOCK);
		simulator.setDebugLevel(0);

		final long startTime = System.currentTimeMillis();

		byte[][] scene = levelScene; // 19 x 19

		// シミュレータを動かして，現在のフレームまで持ってくる．
		simulator.advanceStep(simulator.rootScene, action);

		/*{
			final float simX = simulator.rootScene.mario.x;
			final float simY = simulator.rootScene.mario.y;
			final float realX = realMarioPos[0];
			final float realY = realMarioPos[1];
			System.out.println("[AstarAgent getAction]: (simX, simY) = (" + simX + ", " + simY + ")");
			System.out.println("[AstarAgent getAction]: (realX, realY) = (" + realX + ", " + realY + ")");
			System.out.println("[AstarAgent getAction]: (diffX, diffY) = (" + (realX - simX) + ", " + (realY - simY) + ")");
		}*/

		// シミュレータと真の状況の同期をとる
		simulator.syncWithGame(scene, enemyInfo, spriteInfo);
		Mario mario = simulator.rootScene.mario;
		if(this.marioMode == 2) { // FIRE
			mario.large = true;
			mario.fire = true;
		} else if(this.marioMode == 1) { // LARGE
			mario.large = true;
			mario.fire = false;
		} else {
			mario.large = false;
			mario.fire = false;
		}
		/*simulator.rootScene.mario.x = realMarioPos[0];
		// マジックナンバーは Mario GROUND_INERTIA と AIR_INERTIA から
		simulator.rootScene.mario.xa = (realMarioPos[0] - lastX) * 0.89f;
		if(Math.abs(simulator.rootScene.mario.y - realMarioPos[1]) > 0.01f) {
			simulator.rootScene.mario.ya = (realMarioPos[1] - lastY) * 0.85f + 3.f;
		}
		simulator.rootScene.mario.y = realMarioPos[1];
*/
		long t2 = System.currentTimeMillis();

		/*lastX = realMarioPos[0];
		lastY = realMarioPos[1];*/

		if(firstStepCount > 10) {
			action = simulator.optimise(40 - (t2 - startTime) - 15); // -20 は適当．本家のエンジン遅いのか？40ms も使えないとき多い．
		} else {
			firstStepCount++;
		}

		/*{
			final long endTime = System.currentTimeMillis();
			System.out.println("[Agent getAction]: total calc time -> " + (endTime - startTime) + " ms.");
			System.out.println("==================================================================================\n");
		}*/

		return action;
	}
}
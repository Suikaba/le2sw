
package task4.agents;

import ch.idsia.agents.controllers.BasicMarioAIAgent;
import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.tools.MarioAIOptions;
import task4.engine.BeamSearchSimulator;

/**
 * Created by IntelliJ IDEA.
 * User: Sergey Karakovskiy, sergey.karakovskiy@gmail.com
 * Date: Apr 8, 2009
 * Time: 4:03:46 AM
 */

public class BeamSearchAgent extends BasicMarioAIAgent
{
	private boolean action[] = new boolean[Environment.numberOfKeys];
	private BeamSearchSimulator simulator;
	private float lastX = 0;
	private float lastY = 0;

	public BeamSearchAgent()
	{
	    super("BeamSearchAgent");
	    reset();
	}

	public void reset()
	{
		action = new boolean[Environment.numberOfKeys];
		simulator = new BeamSearchSimulator();
	}

	public void resetSimMario(MarioAIOptions options) {
		simulator.resetSimMario(options);
	}

	public boolean[] getAction()
	{
		// for debug
		boolean calcTime = true;

		final long startTime = System.currentTimeMillis();

		byte[][] scene = (byte[][])levelScene.clone(); // 19 x 19
		float[] enemies = (float[])enemiesFloatPos.clone();
		float[] realMarioPos = (float[])marioFloatPos.clone();

		long t1 = System.currentTimeMillis();
		simulator.advanceStep(action);
		long t2 = System.currentTimeMillis();
		if(calcTime) {
			System.out.println("[Simulator.advanceStep]: " + (t2 - t1) + " ms.");
		}
		if(simulator.levelScene.mario.x != realMarioPos[0] || simulator.levelScene.mario.y != realMarioPos[1]) {
			simulator.levelScene.mario.x = realMarioPos[0];
			simulator.levelScene.mario.xa = (realMarioPos[0] - lastX) * 0.89f;
			if(Math.abs(simulator.levelScene.mario.y - realMarioPos[1]) > 0.1f) {
				simulator.levelScene.mario.ya = (realMarioPos[1] - lastY) * 0.85f;
			}

			simulator.levelScene.mario.y = realMarioPos[1];
		}

		simulator.setLevelPart(scene, enemies);
		if(this.marioMode == 2) { // FIRE
			simulator.levelScene.mario.large = true;
			simulator.levelScene.mario.fire = true;
		} else if(this.marioMode == 1) { // LARGE
			simulator.levelScene.mario.large = true;
			simulator.levelScene.mario.fire = false;
		} else {
			simulator.levelScene.mario.large = false;
			simulator.levelScene.mario.fire = false;
		}
		t2 = System.currentTimeMillis();
		if(calcTime) {
			System.out.println("[Simulator.advanceStep]: " + (t2 - t1) + " ms.");
		}
		lastX = realMarioPos[0];
		lastY = realMarioPos[1];

		action = simulator.optimise(40 - (t2 - t1));

		final long endTime = System.currentTimeMillis();
		if(calcTime) {
			System.out.println("[Agent getAction]: total calc time -> " + (endTime - startTime) + " ms.");
		}

		//simulator.printScene();
		//System.out.println("RealMarioPos: " + realMarioPos[0] + " " + realMarioPos[1]);

		return action;
	}
}
/*
 * Copyright (c) 2009-2010, Sergey Karakovskiy and Julian Togelius
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Mario AI nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package task4.agents;

import ch.idsia.agents.controllers.BasicMarioAIAgent;
import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.tools.MarioAIOptions;
import task4.engine.Simulator;

/**
 * Created by IntelliJ IDEA.
 * User: Sergey Karakovskiy, sergey.karakovskiy@gmail.com
 * Date: Apr 8, 2009
 * Time: 4:03:46 AM
 */

public class BeamSearchAgent extends BasicMarioAIAgent
{
	private boolean action[] = new boolean[Environment.numberOfKeys];
	private Simulator simulator;
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
		simulator = new Simulator();
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
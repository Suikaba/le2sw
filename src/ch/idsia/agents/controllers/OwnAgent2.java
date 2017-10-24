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

package ch.idsia.agents.controllers;

import ch.idsia.agents.Agent;
import ch.idsia.benchmark.mario.engine.GeneralizerLevelScene;
import ch.idsia.benchmark.mario.engine.sprites.Mario;
import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.benchmark.mario.engine.sprites.Sprite;
import java.util.Random;

/**
 * Created by IntelliJ IDEA.
 * User: Sergey Karakovskiy, sergey.karakovskiy@gmail.com
 * Date: Apr 8, 2009
 * Time: 4:03:46 AM
 */

public class OwnAgent2 extends BasicMarioAIAgent implements Agent
{
	final int MIN_R = 0;
	final int MAX_R = 18;
	final int MIN_C = 0;
	final int MAX_C = 18;

	final int NO_OBSTACLE = 0;

	boolean good_jump = false;
	int leftCounter = 0;
	int rightCounter = 0;

	public OwnAgent2()
	{
	    super("OwnAgent2");
	    reset();
	}

	public boolean isObstacle(int r, int c) {
		final int val = getReceptiveFieldCellValue(r, c);
		return val == GeneralizerLevelScene.BRICK
			   || val == GeneralizerLevelScene.BORDER_CANNOT_PASS_THROUGH
			   || val == GeneralizerLevelScene.FLOWER_POT_OR_CANNON
			   || val == GeneralizerLevelScene.LADDER;
	}
	
	public boolean isCreature(int r, int c) {
		final int val = getEnemiesCellValue(r, c);
		return val != Sprite.KIND_NONE;
	}

	public boolean isGaps(int c) {
		for(int i = MIN_R; i <= MAX_R; ++i) {
			if(getReceptiveFieldCellValue(i, c) != NO_OBSTACLE) {
				return false;
			}
		}
		return true;
	}

	public void reset()
	{
		action = new boolean[Environment.numberOfKeys];
	}
	
	public boolean canJump()
	{
		if(!isMarioAbleToJump) {
			return false;
		}
		if(leftCounter >= 5) {
			return true;
		}
		boolean ok = true;
		for(int c = marioEgoCol; c <= marioEgoCol + 2; ++c) {
			for(int r = marioEgoRow - 1; r >= 0; --r) {
				ok &= !isCreature(r, c);
			}
		}
		return ok;
	}
	
	public int findFrontEnemy() {
		int result = -1;
		for(int c = marioEgoCol + 1; c <= MAX_C; ++c) {
			if(isCreature(marioEgoRow, c)) {
				result = c;
				break;
			}
		}
		return result;
	}
	
	void goLeft() {
		action[Mario.KEY_LEFT] = true;
		action[Mario.KEY_RIGHT] = false;
	}
	void goRight() {
		action[Mario.KEY_RIGHT] = true;
		action[Mario.KEY_LEFT] = false;
	}
	void actionUpdate() {
		if(action[Mario.KEY_LEFT]) {
			leftCounter++;
		} else {
			leftCounter = 0;
		}
		if(action[Mario.KEY_RIGHT]) {
			rightCounter++;
		} else {
			rightCounter = 0;
		}
	}

	public boolean[] getAction()
	{
		final int NOW_R = marioEgoRow;
		final int NOW_C = marioEgoCol;
		
		goRight();
		action[Mario.KEY_SPEED] = true;
		
		boolean can_jump = isMarioAbleToJump || !isMarioOnGround;
		if(isMarioAbleToJump) {
			good_jump = false;
		}
		if(isObstacle(marioEgoRow, marioEgoCol + 1)) {
			if(canJump()) {
				action[Mario.KEY_JUMP] = can_jump;
				goRight();
			} else {
				goLeft();
			}
		}
		
		int frontEnemyCol = findFrontEnemy();
		if(frontEnemyCol != -1) {
			action[Mario.KEY_SPEED] = false;
			if(isMarioAbleToShoot) {
				action[Mario.KEY_SPEED] = true;
				goRight();
			} else if(canJump()) {
				action[Mario.KEY_JUMP] = true;
				goRight();
			} else {
				goLeft();
			}
		}
		if(isGaps(marioEgoCol + 1)) {
			action[Mario.KEY_JUMP] = can_jump;
			if(isMarioAbleToJump && isMarioOnGround && !isGaps(marioEgoCol)) {
				good_jump = true;
			}
			if(!good_jump) {
				goLeft();
			}
		}
		actionUpdate();
	    return action;
	}
}
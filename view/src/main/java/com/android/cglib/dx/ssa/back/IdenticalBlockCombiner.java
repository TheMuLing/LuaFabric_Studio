/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cglib.dx.ssa.back;

import com.android.cglib.dx.rop.code.BasicBlock;
import com.android.cglib.dx.rop.code.BasicBlockList;
import com.android.cglib.dx.rop.code.RegOps;
import com.android.cglib.dx.rop.code.RopMethod;
import com.android.cglib.dx.util.IntList;

import java.util.BitSet;

/**
 * Searches for basic blocks that all have the same successor and insns
 * but different predecessors. These blocks are then combined into a single
 * block and the now-unused blocks are deleted. These identical blocks
 * frequently are created when catch blocks are edge-split.
 */
public class IdenticalBlockCombiner {
    private final RopMethod ropMethod;
    private final BasicBlockList blocks;
    private final BasicBlockList newBlocks;

    /**
     * Constructs instance. Call {@code process()} to run.
     *
     * @param rm {@code non-null;} instance to process
     */
    public IdenticalBlockCombiner(RopMethod rm) {
        ropMethod = rm;
        blocks = ropMethod.getBlocks();
        newBlocks = blocks.getMutableCopy();
    }

    /**
     * Runs algorithm. TODO: This is n^2, and could be made linear-ish with
     * a hash. In particular, hash the contents of each block and only
     * compare blocks with the same hash.
     *
     * @return {@code non-null;} new method that has been processed
     */
    public RopMethod process() {
        int szBlocks = blocks.size();
        // indexed by label
        BitSet toDelete = new BitSet(blocks.getMaxLabel());

        // For each non-deleted block...
        for (int bindex = 0; bindex < szBlocks; bindex++) {
            BasicBlock b = blocks.get(bindex);

            if (toDelete.get(b.label())) {
                // doomed block
                continue;
            }

            IntList preds = ropMethod.labelToPredecessors(b.label());

            // ...look at all of it's predecessors that have only one succ...
            int szPreds = preds.size();
            for (int i = 0; i < szPreds; i++) {
                int iLabel = preds.get(i);

                BasicBlock iBlock = blocks.labelToBlock(iLabel);

                if (toDelete.get(iLabel)
                        || iBlock.successors().size() > 1
                        || iBlock.getFirstInsn().getOpcode().opcode() ==
                        RegOps.MOVE_RESULT) {
                    continue;
                }

                IntList toCombine = new IntList();

                // ...and see if they can be combined with any other preds...
                for (int j = i + 1; j < szPreds; j++) {
                    int jLabel = preds.get(j);
                    BasicBlock jBlock = blocks.labelToBlock(jLabel);

                    if (jBlock.successors().size() == 1
                            && compareInsns(iBlock, jBlock)) {

                        toCombine.add(jLabel);
                        toDelete.set(jLabel);
                    }
                }

                combineBlocks(iLabel, toCombine);
            }
        }

        for (int i = szBlocks - 1; i >= 0; i--) {
            if (toDelete.get(newBlocks.get(i).label())) {
                newBlocks.set(i, null);
            }
        }

        newBlocks.shrinkToFit();
        newBlocks.setImmutable();

        return new RopMethod(newBlocks, ropMethod.getFirstLabel());
    }

    /**
     * Helper method to compare the contents of two blocks.
     *
     * @param a {@code non-null;} a block to compare
     * @param b {@code non-null;} another block to compare
     * @return {@code true} iff the two blocks' instructions are the same
     */
    private static boolean compareInsns(BasicBlock a, BasicBlock b) {
        return a.insns().contentEquals(b.insns());
    }

    /**
     * Combines blocks proven identical into one alpha block, re-writing
     * all of the successor links that point to the beta blocks to point
     * to the alpha block instead.
     *
     * @param alphaLabel block that will replace all the beta block
     * @param betaLabels label list of blocks to combine
     */
    private void combineBlocks(int alphaLabel, IntList betaLabels) {
        int szBetas = betaLabels.size();

        for (int i = 0; i < szBetas; i++) {
            int betaLabel = betaLabels.get(i);
            BasicBlock bb = blocks.labelToBlock(betaLabel);
            IntList preds = ropMethod.labelToPredecessors(bb.label());
            int szPreds = preds.size();

            for (int j = 0; j < szPreds; j++) {
                BasicBlock predBlock = newBlocks.labelToBlock(preds.get(j));
                replaceSucc(predBlock, betaLabel, alphaLabel);
            }
        }
    }

    /**
     * Replaces one of a block's successors with a different label. Constructs
     * an updated BasicBlock instance and places it in {@code newBlocks}.
     *
     * @param block    block to replace
     * @param oldLabel label of successor to replace
     * @param newLabel label of new successor
     */
    private void replaceSucc(BasicBlock block, int oldLabel, int newLabel) {
        IntList newSuccessors = block.successors().mutableCopy();
        int newPrimarySuccessor;

        newSuccessors.set(newSuccessors.indexOf(oldLabel), newLabel);
        newPrimarySuccessor = block.primarySuccessor();

        if (newPrimarySuccessor == oldLabel) {
            newPrimarySuccessor = newLabel;
        }

        newSuccessors.setImmutable();

        BasicBlock newBB = new BasicBlock(block.label(),
                block.insns(), newSuccessors, newPrimarySuccessor);

        newBlocks.set(newBlocks.indexOfLabel(block.label()), newBB);
    }
}

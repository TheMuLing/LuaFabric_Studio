/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cglib.dx.io;

public record Code(int registersSize, int insSize, int outsSize, int debugInfoOffset,
                   short[] instructions, Try[] tries, CatchHandler[] catchHandlers) {

    public static class Try {
        final int startAddress;
        final int instructionCount;
        final int handlerOffset;

        Try(int startAddress, int instructionCount, int handlerOffset) {
            this.startAddress = startAddress;
            this.instructionCount = instructionCount;
            this.handlerOffset = handlerOffset;
        }

        public int getStartAddress() {
            return startAddress;
        }

        public int getInstructionCount() {
            return instructionCount;
        }

        public int getHandlerOffset() {
            return handlerOffset;
        }
    }

    public record CatchHandler(int[] typeIndexes, int[] addresses, int catchAllAddress) {
    }
}

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.dfs;

import org.apache.hadoop.io.*;

import java.io.*;

/****************************************************
 * A LocatedBlock is a pair of Block, DatanodeInfo[]
 * objects.  It tells where to find a Block.
 * 
 * @author Michael Cafarella
 ****************************************************/
class LocatedBlock implements Writable {

  static {                                      // register a ctor
    WritableFactories.setFactory
      (LocatedBlock.class,
       new WritableFactory() {
         public Writable newInstance() { return new LocatedBlock(); }
       });
  }

  private Block b;
  private long offset;  // offset of the first byte of the block in the file
  private DatanodeInfo[] locs;

  /**
   */
  public LocatedBlock() {
    this(new Block(), new DatanodeInfo[0], 0L);
  }

  /**
   */
  public LocatedBlock(Block b, DatanodeInfo[] locs) {
    this(b, locs, -1); // startOffset is unknown
  }

  /**
   */
  public LocatedBlock(Block b, DatanodeInfo[] locs, long startOffset) {
    this.b = b;
    this.offset = startOffset;
    if (locs==null) {
      this.locs = new DatanodeInfo[0];
    } else {
      this.locs = locs;
    }
  }

  /**
   */
  public Block getBlock() {
    return b;
  }

  /**
   */
  DatanodeInfo[] getLocations() {
    return locs;
  }
  
  long getStartOffset() {
    return offset;
  }
  
  long getBlockSize() {
    return b.getNumBytes();
  }

  void setStartOffset(long value) {
    this.offset = value;
  }

  ///////////////////////////////////////////
  // Writable
  ///////////////////////////////////////////
  public void write(DataOutput out) throws IOException {
    out.writeLong(offset);
    b.write(out);
    out.writeInt(locs.length);
    for (int i = 0; i < locs.length; i++) {
      locs[i].write(out);
    }
  }

  public void readFields(DataInput in) throws IOException {
    offset = in.readLong();
    this.b = new Block();
    b.readFields(in);
    int count = in.readInt();
    this.locs = new DatanodeInfo[count];
    for (int i = 0; i < locs.length; i++) {
      locs[i] = new DatanodeInfo();
      locs[i].readFields(in);
    }
  }
}

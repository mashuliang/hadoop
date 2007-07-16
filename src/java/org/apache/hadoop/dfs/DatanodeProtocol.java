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

import java.io.*;
import org.apache.hadoop.ipc.VersionedProtocol;

/**********************************************************************
 * Protocol that a DFS datanode uses to communicate with the NameNode.
 * It's used to upload current load information and block reports.
 *
 * The only way a NameNode can communicate with a DataNode is by
 * returning values from these functions.
 *
 **********************************************************************/
interface DatanodeProtocol extends VersionedProtocol {
  /*
   * 8: blockCrcUpgradeGetBlockLocations() added;
   */
  public static final long versionID = 8L;
  
  // error code
  final static int NOTIFY = 0;
  final static int DISK_ERROR = 1;
  final static int INVALID_BLOCK = 2;

  /**
   * Determines actions that data node should perform 
   * when receiving a datanode command. 
   */
  final static int DNA_UNKNOWN = 0;    // unknown action   
  final static int DNA_TRANSFER = 1;   // transfer blocks to another datanode
  final static int DNA_INVALIDATE = 2; // invalidate blocks
  final static int DNA_SHUTDOWN = 3;   // shutdown node
  final static int DNA_REGISTER = 4;   // re-register
  final static int DNA_FINALIZE = 5;   // finalize previous upgrade

  /** 
   * Register Datanode.
   *
   * @see org.apache.hadoop.dfs.DataNode#register()
   * @see org.apache.hadoop.dfs.FSNamesystem#registerDatanode(DatanodeRegistration, String)
   * 
   * @return updated {@link org.apache.hadoop.dfs.DatanodeRegistration}, which contains 
   * new storageID if the datanode did not have one and
   * registration ID for further communication.
   */
  public DatanodeRegistration register(DatanodeRegistration registration,
                                       String networkLocation
                                       ) throws IOException;
  /**
   * sendHeartbeat() tells the NameNode that the DataNode is still
   * alive and well.  Includes some status info, too. 
   * It also gives the NameNode a chance to return a "DatanodeCommand" object.
   * A DatanodeCommand tells the DataNode to invalidate local block(s), 
   * or to copy them to other DataNodes, etc.
   */
  public DatanodeCommand sendHeartbeat(DatanodeRegistration registration,
                                       long capacity, long remaining,
                                       int xmitsInProgress,
                                       int xceiverCount) throws IOException;

  /**
   * blockReport() tells the NameNode about all the locally-stored blocks.
   * The NameNode returns an array of Blocks that have become obsolete
   * and should be deleted.  This function is meant to upload *all*
   * the locally-stored blocks.  It's invoked upon startup and then
   * infrequently afterwards.
   */
  public DatanodeCommand blockReport(DatanodeRegistration registration,
                                     Block blocks[]) throws IOException;
    
  /**
   * blockReceived() allows the DataNode to tell the NameNode about
   * recently-received block data.  For example, whenever client code
   * writes a new Block here, or another DataNode copies a Block to
   * this DataNode, it will call blockReceived().
   */
  public void blockReceived(DatanodeRegistration registration,
                            Block blocks[]) throws IOException;

  /**
   * errorReport() tells the NameNode about something that has gone
   * awry.  Useful for debugging.
   */
  public void errorReport(DatanodeRegistration registration,
                          int errorCode, 
                          String msg) throws IOException;
    
  public NamespaceInfo versionRequest() throws IOException;

  /**
   * This is a very general way to send a command to the name-node during
   * distributed upgrade process.
   * 
   * The generosity is because the variety of upgrade commands is unpredictable.
   * The reply from the name-node is also received in the form of an upgrade 
   * command. 
   * 
   * @return a reply in the form of an upgrade command
   */
  UpgradeCommand processUpgradeCommand(UpgradeCommand comm) throws IOException;
  
  /**
   * Get locations for a given block. This is used only during 
   * Block Level CRC upgrades (HADOOP-1134), when a datanode node
   * misses the cluster wide distributed upgrade. It uses the same
   * BlockCrcInfo class, that is also used during distributed upgrade
   * 
   * @param block 
   * @return BlockCrcInfo that contains the block locations.
   * @throws IOException
   */
  public BlockCrcInfo blockCrcUpgradeGetBlockLocations(Block block)
                                                      throws IOException;  
}

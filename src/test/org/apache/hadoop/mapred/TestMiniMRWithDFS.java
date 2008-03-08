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

package org.apache.hadoop.mapred;

import java.io.*;
import java.util.*;
import junit.framework.TestCase;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.dfs.MiniDFSCluster;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.examples.WordCount;

/**
 * A JUnit test to test Mini Map-Reduce Cluster with Mini-DFS.
 */
public class TestMiniMRWithDFS extends TestCase {
  private static final Log LOG =
    LogFactory.getLog(TestMiniMRWithDFS.class.getName());
  
  static final int NUM_MAPS = 10;
  static final int NUM_SAMPLES = 100000;
  
  public static class TestResult {
    public String output;
    public RunningJob job;
    TestResult(RunningJob job, String output) {
      this.job = job;
      this.output = output;
    }
  }
  public static TestResult launchWordCount(JobConf conf,
                                           Path inDir,
                                           Path outDir,
                                           String input,
                                           int numMaps,
                                           int numReduces) throws IOException {
    FileSystem inFs = inDir.getFileSystem(conf);
    FileSystem outFs = outDir.getFileSystem(conf);
    outFs.delete(outDir);
    if (!inFs.mkdirs(inDir)) {
      throw new IOException("Mkdirs failed to create " + inDir.toString());
    }
    {
      DataOutputStream file = inFs.create(new Path(inDir, "part-0"));
      file.writeBytes(input);
      file.close();
    }
    conf.setJobName("wordcount");
    conf.setInputFormat(TextInputFormat.class);
    
    // the keys are words (strings)
    conf.setOutputKeyClass(Text.class);
    // the values are counts (ints)
    conf.setOutputValueClass(IntWritable.class);
    
    conf.setMapperClass(WordCount.MapClass.class);        
    conf.setCombinerClass(WordCount.Reduce.class);
    conf.setReducerClass(WordCount.Reduce.class);
    conf.setInputPath(inDir);
    conf.setOutputPath(outDir);
    conf.setNumMapTasks(numMaps);
    conf.setNumReduceTasks(numReduces);
    RunningJob job = JobClient.runJob(conf);
    return new TestResult(job, readOutput(outDir, conf));
  }

  public static String readOutput(Path outDir, 
                                  JobConf conf) throws IOException {
    FileSystem fs = outDir.getFileSystem(conf);
    StringBuffer result = new StringBuffer();
    {
      
      Path[] fileList = FileUtil.stat2Paths(fs.listStatus(outDir,
                                   new OutputLogFilter()));
      for(int i=0; i < fileList.length; ++i) {
        LOG.info("File list[" + i + "]" + ": "+ fileList[i]);
        BufferedReader file = 
          new BufferedReader(new InputStreamReader(fs.open(fileList[i])));
        String line = file.readLine();
        while (line != null) {
          result.append(line);
          result.append("\n");
          line = file.readLine();
        }
        file.close();
      }
    }
    return result.toString();
  }
  
  /**
   * Make sure that there are exactly the directories that we expect to find.
   * @param mr the map-reduce cluster
   * @param taskDirs the task ids that should be present
   */
  static void checkTaskDirectories(MiniMRCluster mr,
                                           String[] jobIds,
                                           String[] taskDirs) {
    mr.waitUntilIdle();
    int trackers = mr.getNumTaskTrackers();
    List<String> neededDirs = new ArrayList<String>(Arrays.asList(taskDirs));
    boolean[] found = new boolean[taskDirs.length];
    for(int i=0; i < trackers; ++i) {
      int numNotDel = 0;
      File localDir = new File(mr.getTaskTrackerLocalDir(i));
      LOG.debug("Tracker directory: " + localDir);
      File trackerDir = new File(localDir, "taskTracker");
      assertTrue("local dir " + localDir + " does not exist.", 
                 localDir.isDirectory());
      assertTrue("task tracker dir " + trackerDir + " does not exist.", 
                 trackerDir.isDirectory());
      String contents[] = localDir.list();
      String trackerContents[] = trackerDir.list();
      for(int j=0; j < contents.length; ++j) {
        System.out.println("Local " + localDir + ": " + contents[j]);
      }
      for(int j=0; j < trackerContents.length; ++j) {
        System.out.println("Local jobcache " + trackerDir + ": " + trackerContents[j]);
      }
      for(int fileIdx = 0; fileIdx < contents.length; ++fileIdx) {
        String name = contents[fileIdx];
        if (!("taskTracker".equals(contents[fileIdx]))) {
          LOG.debug("Looking at " + name);
          int idx = neededDirs.indexOf(name);
          assertTrue("Spurious directory " + name + " found in " +
                     localDir, idx != -1);
          assertTrue("Matching output directory not found " + name +
                     " in " + trackerDir, 
                     new File(new File(new File(trackerDir, "jobcache"), jobIds[idx]), name).isDirectory());
          found[idx] = true;
          numNotDel++;
        }  
      }
    }
    for(int i=0; i< found.length; i++) {
      assertTrue("Directory " + taskDirs[i] + " not found", found[i]);
    }
  }

  static void runPI(MiniMRCluster mr, JobConf jobconf) throws IOException {
    LOG.info("runPI");
    double estimate = PiEstimator.launch(NUM_MAPS, NUM_SAMPLES, jobconf);
    double error = Math.abs(Math.PI - estimate);
    assertTrue("Error in PI estimation "+error+" exceeds 0.01", (error < 0.01));
    checkTaskDirectories(mr, new String[]{}, new String[]{});
  }

  static void runWordCount(MiniMRCluster mr, JobConf jobConf) throws IOException {
    LOG.info("runWordCount");
    // Run a word count example
    // Keeping tasks that match this pattern
    jobConf.setKeepTaskFilesPattern("task_[^_]*_[0-9]*_m_000001_.*");
    TestResult result;
    final Path inDir = new Path("./wc/input");
    final Path outDir = new Path("./wc/output");
    result = launchWordCount(jobConf, inDir, outDir,
                             "The quick brown fox\nhas many silly\n" + 
                             "red fox sox\n",
                             3, 1);
    assertEquals("The\t1\nbrown\t1\nfox\t2\nhas\t1\nmany\t1\n" +
                 "quick\t1\nred\t1\nsilly\t1\nsox\t1\n", result.output);
    String jobid = result.job.getJobID();
    String taskid = "task_" + jobid.substring(4) + "_m_000001_0";
    checkTaskDirectories(mr, new String[]{jobid}, new String[]{taskid});
    // test with maps=0
    jobConf = mr.createJobConf();
    result = launchWordCount(jobConf, inDir, outDir, "owen is oom", 0, 1);
    assertEquals("is\t1\noom\t1\nowen\t1\n", result.output);
    // Run a job with input and output going to localfs even though the 
    // default fs is hdfs.
    {
      FileSystem localfs = FileSystem.getLocal(jobConf);
      String TEST_ROOT_DIR =
        new File(System.getProperty("test.build.data","/tmp"))
        .toString().replace(' ', '+');
      Path localIn = localfs.makeQualified
                        (new Path(TEST_ROOT_DIR + "/local/in"));
      Path localOut = localfs.makeQualified
                        (new Path(TEST_ROOT_DIR + "/local/out"));
      result = launchWordCount(jobConf, localIn, localOut,
                               "all your base belong to us", 1, 1);
      assertEquals("all\t1\nbase\t1\nbelong\t1\nto\t1\nus\t1\nyour\t1\n", 
                   result.output);
      assertTrue("outputs on localfs", localfs.exists(localOut));
    }
  }

  public void testWithDFS() throws IOException {
    MiniDFSCluster dfs = null;
    MiniMRCluster mr = null;
    FileSystem fileSys = null;
    try {
      final int taskTrackers = 4;

      Configuration conf = new Configuration();
      dfs = new MiniDFSCluster(conf, 4, true, null);
      fileSys = dfs.getFileSystem();
      mr = new MiniMRCluster(taskTrackers, fileSys.getUri().toString(), 1);

      runPI(mr, mr.createJobConf());
      runWordCount(mr, mr.createJobConf());
    } finally {
      if (dfs != null) { dfs.shutdown(); }
      if (mr != null) { mr.shutdown();
      }
    }
  }
  
}

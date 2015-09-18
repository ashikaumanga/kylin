/*
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
package org.apache.kylin.storage.hbase.steps;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.kylin.engine.mr.HadoopUtil;
import org.apache.kylin.engine.mr.JobBuilderSupport;
import org.apache.kylin.job.engine.JobEngineConfig;
import org.apache.kylin.job.exception.ExecuteException;
import org.apache.kylin.job.execution.AbstractExecutable;
import org.apache.kylin.job.execution.ExecutableContext;
import org.apache.kylin.job.execution.ExecuteResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by sunyerui on 15/9/17.
 */
public class HDFSPathGarbageCollectionStep extends AbstractExecutable {

    private StringBuffer output;
    private JobEngineConfig config;

    public HDFSPathGarbageCollectionStep() {
        super();
        output = new StringBuffer();
    }

    @Override
    protected ExecuteResult doWork(ExecutableContext context) throws ExecuteException {
        try {
            config = new JobEngineConfig(context.getConfig());
            dropHdfsPathOnCluster(getDeletePathsOnHadoopCluster(), FileSystem.get(HadoopUtil.getCurrentConfiguration()));
            dropHdfsPathOnCluster(getDeletePathsOnHBaseCluster(), FileSystem.get(HadoopUtil.getCurrentHBaseConfiguration()));
        } catch (IOException e) {
            logger.error("job:" + getId() + " execute finished with exception", e);
            output.append("\n").append(e.getLocalizedMessage());
            return new ExecuteResult(ExecuteResult.State.ERROR, output.toString());
        }

        return new ExecuteResult(ExecuteResult.State.SUCCEED, output.toString());
    }

    private void dropHdfsPathOnCluster(List<String> oldHdfsPaths, FileSystem fileSystem) throws IOException {
        if (oldHdfsPaths != null && oldHdfsPaths.size() > 0) {
            logger.debug("Drop HDFS path on FileSystem: " + fileSystem.getUri());
            output.append("Drop HDFS path on FileSystem: \"" + fileSystem.getUri() + "\" \n");
            for (String path : oldHdfsPaths) {
                if (path.endsWith("*"))
                    path = path.substring(0, path.length() - 1);

                Path oldPath = new Path(path);
                if (fileSystem.exists(oldPath)) {
                    fileSystem.delete(oldPath, true);
                    logger.debug("HDFS path " + path + " is dropped.");
                    output.append("HDFS path " + path + " is dropped.\n");
                } else {
                    logger.debug("HDFS path " + path + " not exists.");
                    output.append("HDFS path " + path + " not exists.\n");
                }
                // If hbase was deployed on another cluster, the job dir is empty and should be dropped,
                // because of rowkey_stats and hfile dirs are both dropped.
                if (fileSystem.listStatus(oldPath.getParent()).length == 0) {
                    Path emptyJobPath = new Path(JobBuilderSupport.getJobWorkingDir(config, getJobId()));
                    if (fileSystem.exists(emptyJobPath)) {
                        fileSystem.delete(emptyJobPath, true);
                        logger.debug("HDFS path " + emptyJobPath + " is empty and dropped.");
                        output.append("HDFS path " + emptyJobPath + " is empty and dropped.\n");
                    }
                }
            }
        }
    }

    public void setDeletePathsOnHadoopCluster(List<String> deletePaths) {
        setArrayParam("toDeletePathsOnHadoopCluster", deletePaths);
    }

    public void setDeletePathsOnHBaseCluster(List<String> deletePaths) {
        setArrayParam("toDeletePathsOnHBaseCluster", deletePaths);
    }

    public void setJobId(String jobId) {
        setParam("jobId", jobId);
    }

    public List<String> getDeletePathsOnHadoopCluster() {
        return getArrayParam("toDeletePathsOnHadoopCluster");
    }

    public List<String> getDeletePathsOnHBaseCluster() {
        return getArrayParam("toDeletePathsOnHBaseCluster");
    }

    public String getJobId() {
        return getParam("jobId");
    }

    private void setArrayParam(String paramKey, List<String> paramValues) {
        setParam(paramKey, StringUtils.join(paramValues, ","));
    }

    private List<String> getArrayParam(String paramKey) {
        final String ids = getParam(paramKey);
        if (ids != null) {
            final String[] splitted = StringUtils.split(ids, ",");
            ArrayList<String> result = Lists.newArrayListWithExpectedSize(splitted.length);
            for (String id : splitted) {
                result.add(id);
            }
            return result;
        } else {
            return Collections.emptyList();
        }
    }
}
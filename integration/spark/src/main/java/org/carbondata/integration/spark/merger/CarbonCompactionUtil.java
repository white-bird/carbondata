/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.carbondata.integration.spark.merger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.carbondata.core.carbon.CarbonTableIdentifier;
import org.carbondata.core.carbon.datastore.block.TableBlockInfo;
import org.carbondata.core.carbon.datastore.block.TaskBlockInfo;
import org.carbondata.core.carbon.datastore.exception.IndexBuilderException;
import org.carbondata.core.carbon.metadata.CarbonMetadata;
import org.carbondata.core.carbon.metadata.blocklet.DataFileFooter;
import org.carbondata.core.carbon.metadata.schema.table.CarbonTable;
import org.carbondata.core.carbon.path.CarbonStorePath;
import org.carbondata.core.carbon.path.CarbonTablePath;
import org.carbondata.core.constants.CarbonCommonConstants;
import org.carbondata.core.util.CarbonProperties;
import org.carbondata.core.util.CarbonUtil;
import org.carbondata.core.util.CarbonUtilException;

/**
 * Utility Class for the Compaction Flow.
 */
public class CarbonCompactionUtil {

  /**
   * To create a mapping of Segment Id and TableBlockInfo.
   *
   * @param tableBlockInfoList
   * @return
   */
  public static Map<String, TaskBlockInfo> createMappingForSegments(
      List<TableBlockInfo> tableBlockInfoList) {

    // stores taskBlockInfo of each segment
    Map<String, TaskBlockInfo> segmentBlockInfoMapping =
        new HashMap<>(CarbonCommonConstants.DEFAULT_COLLECTION_SIZE);


    for (TableBlockInfo info : tableBlockInfoList) {
      String segId = info.getSegmentId();
      // check if segId is already present in map
      TaskBlockInfo taskBlockInfoMapping = segmentBlockInfoMapping.get(segId);
      // extract task ID from file Path.
      String taskNo = CarbonTablePath.DataFileUtil.getTaskNo(info.getFilePath());
      // if taskBlockInfo is not there, then create and add
      if (null == taskBlockInfoMapping) {
        taskBlockInfoMapping = new TaskBlockInfo();
        groupCorrespodingInfoBasedOnTask(info, taskBlockInfoMapping, taskNo);
        // put the taskBlockInfo with respective segment id
        segmentBlockInfoMapping.put(segId, taskBlockInfoMapping);
      } else
      {
        groupCorrespodingInfoBasedOnTask(info, taskBlockInfoMapping, taskNo);
      }
    }
    return segmentBlockInfoMapping;

  }

  /**
   * Grouping the taskNumber and list of TableBlockInfo.
   * @param info
   * @param taskBlockMapping
   * @param taskNo
   */
  private static void groupCorrespodingInfoBasedOnTask(TableBlockInfo info,
      TaskBlockInfo taskBlockMapping, String taskNo) {
    // get the corresponding list from task mapping.
    List<TableBlockInfo> blockLists = taskBlockMapping.getTableBlockInfoList(taskNo);
    if (null != blockLists) {
      blockLists.add(info);
    } else {
      blockLists = new ArrayList<>(CarbonCommonConstants.DEFAULT_COLLECTION_SIZE);
      blockLists.add(info);
      taskBlockMapping.addTableBlockInfoList(taskNo, blockLists);
    }
  }

  /**
   * To create a mapping of Segment Id and DataFileFooter.
   *
   * @param tableBlockInfoList
   * @return
   */
  public static Map<String, List<DataFileFooter>> createDataFileFooterMappingForSegments(
      List<TableBlockInfo> tableBlockInfoList) throws IndexBuilderException {

    Map<String, List<DataFileFooter>> segmentBlockInfoMapping = new HashMap<>();
    for (TableBlockInfo blockInfo : tableBlockInfoList) {
      List<DataFileFooter> eachSegmentBlocks = new ArrayList<>();
      String segId = blockInfo.getSegmentId();

      DataFileFooter dataFileMatadata = null;
      // check if segId is already present in map
      List<DataFileFooter> metadataList = segmentBlockInfoMapping.get(segId);
      try {
        dataFileMatadata = CarbonUtil
            .readMetadatFile(blockInfo.getFilePath(), blockInfo.getBlockOffset(),
                blockInfo.getBlockLength());
      } catch (CarbonUtilException e) {
        throw new IndexBuilderException(e);
      }
      if (null == metadataList) {
        // if it is not present
        eachSegmentBlocks.add(dataFileMatadata);
        segmentBlockInfoMapping.put(segId, eachSegmentBlocks);
      } else {

        // if its already present then update the list.
        metadataList.add(dataFileMatadata);
      }
    }
    return segmentBlockInfoMapping;

  }

  /**
   * For forming the temp store location
   * @param schemaName
   * @param cubeName
   * @param partitionID
   * @param segmentId
   * @param taskNo
   * @return
   */
  public static String getTempLocation(String schemaName, String cubeName, String partitionID,
      String segmentId, String taskNo){
    String storeLocation;
    String tempLocationKey = schemaName + '_' + cubeName;
    String baseStorePath = CarbonProperties.getInstance()
        .getProperty(tempLocationKey, CarbonCommonConstants.STORE_LOCATION_DEFAULT_VAL);
    CarbonTable carbonTable = CarbonMetadata.getInstance().getCarbonTable(tempLocationKey);
    CarbonTableIdentifier carbonTableIdentifier = carbonTable.getCarbonTableIdentifier();
    CarbonTablePath carbonTablePath =
        CarbonStorePath.getCarbonTablePath(baseStorePath, carbonTableIdentifier);
    String partitionId = partitionID;
    String carbonDataDirectoryPath = carbonTablePath.getCarbonDataDirectoryPath(partitionId,
        segmentId);
    carbonDataDirectoryPath = carbonDataDirectoryPath + File.separator + taskNo;
    storeLocation = carbonDataDirectoryPath + CarbonCommonConstants.FILE_INPROGRESS_STATUS;
    return storeLocation;
  }

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.schema.stats;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.SortedMap;

import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableUtils;

import com.google.common.collect.ImmutableSortedMap;


/**
 * Interface for Phoenix table statistics. Statistics is collected on the server
 * side and can be used for various purpose like splitting region for scanning, etc.
 * 
 * The table is defined on the client side, but it is populated on the server side. The client
 * should not populate any data to the statistics object.
 */
public interface PTableStats extends Writable {

    public static final PTableStats EMPTY_STATS = new PTableStats() {
        @Override
        public SortedMap<byte[], GuidePostsInfo> getGuidePosts() {
            return ImmutableSortedMap.of();
        }

        @Override
        public void write(DataOutput output) throws IOException {
            WritableUtils.writeVInt(output, 0);
        }

        @Override
        public void readFields(DataInput arg0) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getEstimatedSize() {
            return 0;
        }
    };

    /**
     * TODO: Change from TreeMap to Map
     * Returns a tree map of the guide posts collected against a column family
     * @return
     */
    SortedMap<byte[], GuidePostsInfo> getGuidePosts();

    int getEstimatedSize();
}
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
package org.apache.phoenix.schema.json;

import java.io.IOException;
import java.sql.SQLException;

import org.apache.hadoop.hbase.util.Bytes;
import org.apache.phoenix.exception.SQLExceptionCode;
import org.apache.phoenix.exception.SQLExceptionInfo;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonParser.Feature;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ValueNode;

import com.google.common.base.Preconditions;

/**
 * The {@link PhoenixJson} wraps json and uses Jackson library to parse and traverse the json. It
 * should be used to represent the JSON data type and also should be used to parse Json data and
 * read the value from it. It always conside the last value if same key exist more than once.
 */
public class PhoenixJson implements Comparable<PhoenixJson> {
    private final JsonNode node;
    private String jsonAsString;

    /**
     * Static Factory method to get an {@link PhoenixJson} object. It also validates the json and
     * throws {@link SQLException} if it is invalid with line number and character.
     * @param data Buffer that contains data to parse
     * @param offset Offset of the first data byte within buffer
     * @param length Length of contents to parse within buffer
     * @return {@link PhoenixJson}.
     * @throws SQLException
     */
    public static PhoenixJson getPhoenixJson(byte[] jsonData, int offset, int length)
            throws SQLException {

        String jsonDataStr = Bytes.toString(jsonData, offset, length);
        return getPhoenixJson(jsonDataStr);

    }

    /**
     * Static Factory method to get an {@link PhoenixJson} object. It also validates the json and
     * throws {@link SQLException} if it is invalid with line number and character.
     * @param jsonData Json data as {@link String}.
     * @return {@link PhoenixJson}.
     * @throws SQLException
     */
    public static PhoenixJson getPhoenixJson(String jsonData) throws SQLException {
        try {
            JsonFactory jsonFactory = new JsonFactory();
            JsonParser jsonParser = jsonFactory.createJsonParser(jsonData);
            PhoenixJson phoenixJson = getPhoneixJson(jsonParser);
            /*
             * input data has been stored as it is, since some data is lost when json parser runs,
             * for example if a JSON object within the value contains the same key more than once
             * then only last one is stored rest all of them are ignored, which will defy the
             * contract of PJsonDataType of keeping user data as it is.
             */
            phoenixJson.setJsonAsString(jsonData);
            return phoenixJson;
        } catch (IOException x) {
            throw new SQLExceptionInfo.Builder(SQLExceptionCode.INVALID_JSON_DATA).setRootCause(x)
                    .setMessage(x.getMessage()).build().buildException();
        }

    }

    private static PhoenixJson getPhoneixJson(JsonParser jsonParser) throws IOException,
            JsonProcessingException {
        jsonParser.configure(Feature.ALLOW_COMMENTS, true);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode rootNode = objectMapper.readTree(jsonParser);
            PhoenixJson phoenixJson = new PhoenixJson(rootNode);
            return phoenixJson;
        } finally {
            jsonParser.close();
        }
    }

    /* Default for unit testing */PhoenixJson(final JsonNode node) {
        Preconditions.checkNotNull(node, "root node cannot be null for json");
        this.node = node;
    }

    /**
     * Get {@link PhoenixJson} for a given json paths. For example :
     * <p>
     * <code>
     * {"f2":{"f3":1},"f4":{"f5":99,"f6":{"f7":"2"}}}'
     * </code>
     * <p>
     * for this source json, if we want to know the json at path {'f4','f6'} it will return
     * {@link PhoenixJson} object for json {"f7":"2"}. It always returns the last key if same key
     * exist more than once.
     * <p>
     * If the given path is unreachable then it throws {@link PhoenixJsonException} with the message
     * having information about not found path. It is caller responsibility to wrap it in
     * {@link SQLException} or catch it and return null to client.
     * @param paths {@link String []} of path in the same order as they appear in json.
     * @return {@link PhoenixJson} for the json against @paths.
     * @throws PhoenixJsonException
     */
    public PhoenixJson getPhoenixJson(String[] paths) throws PhoenixJsonException {
        JsonNode node = this.node;
        for (String path : paths) {
            JsonNode nodeTemp = null;
            if (node.isArray()) {
                try {
                    int index = Integer.parseInt(path);
                    nodeTemp = node.path(index);
                } catch (NumberFormatException nfe) {
                    throw new PhoenixJsonException("path: " + path + " not found", nfe);
                }
            } else {
                nodeTemp = node.path(path);
            }
            if (nodeTemp == null || nodeTemp.isMissingNode()) {
                throw new PhoenixJsonException("path: " + path + " not found");
            }
            node = nodeTemp;
        }
        return new PhoenixJson(node);
    }

    /**
     * Get {@link PhoenixJson} for a given json paths. For example :
     * <p>
     * <code>
     * {"f2":{"f3":1},"f4":{"f5":99,"f6":{"f7":"2"}}}'
     * </code>
     * <p>
     * for this source json, if we want to know the json at path {'f4','f6'} it will return
     * {@link PhoenixJson} object for json {"f7":"2"}. It always returns the last key if same key
     * exist more than once.
     * <p>
     * If the given path is unreachable then it return null.
     * @param paths {@link String []} of path in the same order as they appear in json.
     * @return {@link PhoenixJson} for the json against @paths.
     */
    public PhoenixJson getNullablePhoenixJson(String[] paths) {
        JsonNode node = this.node;
        for (String path : paths) {
            JsonNode nodeTemp = null;
            if (node.isArray()) {
                try {
                    int index = Integer.parseInt(path);
                    nodeTemp = node.path(index);
                } catch (NumberFormatException nfe) {
                    return null;
                }
            } else {
                nodeTemp = node.path(path);
            }
            if (nodeTemp == null || nodeTemp.isMissingNode()) {
                return null;
            }
            node = nodeTemp;
        }
        return new PhoenixJson(node);
    }

    /**
     * Serialize the current {@link PhoenixJson} to String. Its required for
     * json_extract_path_text(). If we just return node.toString() it will wrap String value in
     * double quote which is not the expectation, hence avoiding calling toString() on
     * {@link JsonNode} until PhoenixJson represent a Json Array or container for Json object. If
     * PhoenixJson just represent a {@link ValueNode} then it should return value returned from
     * objects toString().
     */
    public String serializeToString() {
        if (this.node == null || this.node.isNull()) {
            return null;
        } else if (this.node.isValueNode()) {

            if (this.node.isNumber()) {
                return this.node.getNumberValue().toString();
            } else if (this.node.isBoolean()) {
                return String.valueOf(this.node.getBooleanValue());
            } else if (this.node.isTextual()) {
                return this.node.getTextValue();
            } else {
                return getJsonAsString();
            }
        } else if (this.node.isArray()) {
            return getJsonAsString();
        } else if (this.node.isContainerNode()) {
            return getJsonAsString();
        }

        return null;

    }

    @Override
    public String toString() {
        if (this.jsonAsString == null && this.node != null) {
            return getJsonAsString();
        }
        return this.jsonAsString;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.node == null) ? 0 : this.node.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PhoenixJson other = (PhoenixJson) obj;
        if ((this.jsonAsString != null) && (other.jsonAsString != null)) {
            if (this.jsonAsString.equals(other.jsonAsString)) return true;
        }
        if (this.node == null) {
            if (other.node != null) return false;
        } else if (!this.node.equals(other.node)) return false;
        return true;
    }

    /**
     * @return length of the string represented by the current {@link PhoenixJson}.
     */
    public int estimateByteSize() {
        if (this.jsonAsString != null) {
            return this.jsonAsString.length();
        }
        String jsonStr = getJsonAsString();
        return jsonStr == null ? 1 : jsonStr.length();
    }

    @Override
    public int compareTo(PhoenixJson o) {
        if (o == null) {
            return 1;
        } else if (this.equals(o)) {
            return 0;
        } else {
            return this.toString().compareTo(o.toString());
        }
    }

    private void setJsonAsString(String str) {
        this.jsonAsString = str;
    }

    private String getJsonAsString() {
        if (this.node != null) {
            return this.node.toString();
        }
        return null;
    }

}
